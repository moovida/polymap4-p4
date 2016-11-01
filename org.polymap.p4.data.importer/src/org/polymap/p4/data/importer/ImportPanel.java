/* 
 * Copyright (C) 2015, the @authors. All rights reserved.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3.0 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package org.polymap.p4.data.importer;

import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.polymap.core.runtime.UIThreadExecutor.async;
import static org.polymap.core.runtime.event.TypeEventFilter.ifType;
import static org.polymap.core.ui.FormDataFactory.on;
import static org.polymap.rhei.batik.app.SvgImageRegistryHelper.WHITE24;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.geotools.feature.FeatureCollection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.rap.rwt.client.ClientFile;
import org.eclipse.rap.rwt.client.service.ClientFileUploader;
import org.eclipse.rap.rwt.dnd.ClientFileTransfer;

import org.polymap.core.operation.DefaultOperation;
import org.polymap.core.operation.OperationSupport;
import org.polymap.core.runtime.Timer;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.SelectionAdapter;
import org.polymap.core.ui.StatusDispatcher;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.toolkit.IPanelSection;
import org.polymap.rhei.batik.toolkit.SimpleDialog;
import org.polymap.rhei.batik.toolkit.Snackbar.Appearance;
import org.polymap.rhei.batik.toolkit.md.MdListViewer;
import org.polymap.rhei.batik.toolkit.md.MdToolkit;

import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.catalog.CatalogPanel;
import org.polymap.p4.data.importer.ImportsLabelProvider.Type;
import org.polymap.p4.data.importer.features.ImportFeaturesOperation;
import org.polymap.p4.layer.LayersPanel;
import org.polymap.p4.map.ProjectMapPanel;
import org.polymap.rap.updownload.upload.IUploadHandler;
import org.polymap.rap.updownload.upload.Upload;
import org.polymap.rap.updownload.upload.UploadService;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class ImportPanel
        extends P4Panel
        implements IUploadHandler {

    private static Log log = LogFactory.getLog( ImportPanel.class );

    public static final PanelIdentifier ID   = PanelIdentifier.parse( "dataimport" );

    private static final IMessages      i18n = Messages.forPrefix( "ImportPanel" );
    
    
    // instance *******************************************
    
    private Context<ImporterContext>    nextContext;
    
    private ImporterContext             context;
    
    private boolean                     rootImportPanel;
    
    private MdListViewer                importsList;

    private IPanelSection               resultSection;

    private File                        tempDir = ImportTempDir.create();

    private Button                      fab;

    
    @Override
    public boolean wantsToBeShown() {
        return parentPanel()
                .filter( parent -> parent instanceof CatalogPanel 
                        || parent instanceof LayersPanel
                        || parent instanceof ProjectMapPanel )
                .map( parent -> {
                    site().title.set( "" );
                    site().tooltip.set( "Import new data into this project" );
                    site().icon.set( ImporterPlugin.images().svgImage( "database-plus.svg", P4Plugin.HEADER_ICON_CONFIG ) );
                    return true;
                } )
                .orElse( false );
    }
    
    
    @Override
    public void init() {
        super.init();
        site().setSize( SIDE_PANEL_WIDTH, SIDE_PANEL_WIDTH2, Integer.MAX_VALUE );
        site().title.set( "Import" );
        context = nextContext.isPresent() ? nextContext.get() : new ImporterContext(); 

        // listen to ok (to execute) event from ImporterSite
        EventManager.instance().subscribe( this, ifType( ConfigChangeEvent.class, cce -> 
                cce.propName.equals( "ok" ) &&
                cce.getSource() instanceof ImporterSite ) );
    }


    @Override
    public void dispose() {
        nextContext.set( context.importer() != null ? context : null );
        EventManager.instance().unsubscribe( this );
    }


    /**
     * After a prompt change has triggered verification in ImporterContext, the ok state
     * of the importer has been set -> select this importer in the list. This triggers
     * update of the FAB too. 
     */
    @EventHandler( display=true )
    protected void importerOkChanged( ConfigChangeEvent ev ) {
        importsList.setSelection( new StructuredSelection( ((ImporterSite)ev.getSource()).context() ), true );
    }
    
    
    @Override
    public void createContents( Composite parent ) {
        // margin left/right: shadow
        parent.setLayout( FormLayoutFactory.defaults().spacing( 5 ).margins( 3, 8 ).create() );
        MdToolkit tk = (MdToolkit)site().toolkit();

        // upload button
        Upload upload = null;
        if (!nextContext.isPresent()) {
            upload = tk.adapt( new Upload( parent, SWT.FLAT ) ); //, Upload.SHOW_PROGRESS ) );
            upload.setBackground( parent.getBackground() );
            upload.setImage( ImporterPlugin.images().svgImage( "upload.svg", WHITE24 ) );
            upload.setText( "Upload file..." );
            upload.setToolTipText( "<b>Drop</b> files here<br/>or <b>click</b> to open file dialog" );
            upload.setHandler( this );
            upload.moveAbove( null );

            DropTarget labelDropTarget = new DropTarget( upload, DND.DROP_MOVE );
            labelDropTarget.setTransfer( new Transfer[] { ClientFileTransfer.getInstance() } );
            labelDropTarget.addDropListener( createDropTargetAdapter() );
        }

        // imports and prompts
        importsList = tk.createListViewer( parent, SWT.VIRTUAL, SWT.SINGLE | SWT.FULL_SELECTION );
        importsList.setContentProvider( new ImportsContentProvider() );
        importsList.firstLineLabelProvider.set( new ImportsLabelProvider( Type.Summary ) );
        importsList.secondLineLabelProvider.set( new ImportsLabelProvider( Type.Description ) );
        importsList.iconProvider.set( new ImportsLabelProvider( Type.Icon ) );
        importsList.firstSecondaryActionProvider.set( new ImportsLabelProvider( Type.StatusIcon ));

        // selection listener recognizes events from UI *and* from ImportsContentProvider
        importsList.addSelectionChangedListener( ev -> {
            SelectionAdapter.on( ev.getSelection() ).forEach( elm -> {
                //
                if (elm instanceof ImporterContext) {
                    createResultViewer( (ImporterContext)elm );
                    updateFab( (ImporterContext)elm );

                    importsList.expandToLevel( elm, 1 );
                }
                //
                else if (elm instanceof ImporterPrompt) {
                    createPromptViewer( (ImporterPrompt)elm );
                }
            });
        });
        
        // ImportsContentProvider selects every ImportContext when it is loaded;
        // this triggers expansion and resultViewer (see above)
        importsList.setInput( context );
        
        // result viewer
        resultSection = tk.createPanelSection( parent, "Data preview", SWT.BORDER );
        tk.createFlowText( resultSection.getBody(), "No data to preview yet.\n\n" +
                "Please drop a **data** file (CSV, Excel, Shapefile, KML/KMZ, GeoJSON) or an **archive** (ZIP, TAR, TGZ) archive above. " +
                "Or choose one of the **service** importers.");
        
        // layout
        if (upload != null) {
            on( upload ).fill().bottom( 0, 40 );
        }
        // width/height specifies that we want scrollbars in the contets
        on( importsList.getControl() ).fill().top( 0, 45 ).bottom( 50, -10 ).width( 100 ).height( 100 );
        on( resultSection.getControl() ).fill().top( 50, 5 ).height( 100 ).width( 100 );
    }


    protected void createResultViewer( @SuppressWarnings("hiding") ImporterContext context ) {
        resultSection.setTitle( "Data preview" );
        context.updateResultViewer( resultSection.getBody(), site().toolkit() );
    }
    
    
    protected void updateFab( @SuppressWarnings("hiding") ImporterContext context ) {
        if (fab != null) {
            fab.dispose();
        }
        
        if (context.site().ok.get()) {
            MdToolkit tk = (MdToolkit)site().toolkit();
            if (context.site().terminal.get()) {
                fab = tk.createFab( ImporterPlugin.images().svgImage( "check.svg", WHITE24 ), SWT.RIGHT );
                fab.setToolTipText( "Import data" );
            }
            else {
                fab = tk.createFab( ImporterPlugin.images().svgImage( "arrow-right.svg", WHITE24 ), SWT.RIGHT );
                fab.setToolTipText( "Send these data to the next stage" );
            }
            fab.getParent().layout();
            
            fab.addSelectionListener( new org.eclipse.swt.events.SelectionAdapter() {
                @Override
                public void widgetSelected( SelectionEvent ev ) {
                    try {
                        if (context.site().terminal.get()) {
                            executeTerminalContext( context );
                        }
                        else {
                            executeNonTerminalContext( context );
                        }
                    }
                    catch (Exception e) {
                        StatusDispatcher.handleError( "Importer did not execute successfully.", e );
                    }
                }
            });
        }
    }

    
    /**
     * Executes the given context and open next panel.
     */
    protected void executeNonTerminalContext( @SuppressWarnings("hiding") ImporterContext context ) throws Exception {
        context.execute( new NullProgressMonitor() );
        
        nextContext.set( context );
        getContext().openPanel( site().path(), ImportPanel.ID );
    }
    
    
    /**
     * 
     */
    protected void executeTerminalContext( @SuppressWarnings("hiding") ImporterContext context ) throws Exception {
        // execute
        Map<Class,Object> contextOut = new HashMap();
        DefaultOperation executeOp = new DefaultOperation( "" ) {
            @Override
            protected IStatus doExecute( IProgressMonitor monitor, IAdaptable info ) throws Exception {
                contextOut.putAll( context.execute( monitor ) );
                return Status.OK_STATUS;
            }
        };
        OperationSupport.instance().execute( executeOp, false, false );
        
        // copy features
        FeatureCollection features = (FeatureCollection)contextOut.get( FeatureCollection.class );
        if (features != null) {
            ImportFeaturesOperation op = new ImportFeaturesOperation( context, features );
            getContext().propagate( op );
            OperationSupport.instance().execute( op, false, false );
        }
        
        // close panel
        getContext().closePanel( site().path() );
    }
    
    
    
    
    protected void createPromptViewer( ImporterPrompt prompt ) {
        SimpleDialog dialog = site().toolkit().createSimpleDialog( prompt.summary.get() );
        
        dialog.setContents( parent -> prompt.context().createPromptViewer( parent, prompt, site().toolkit() ) );
        
        dialog.addAction( new Action( "OK" ) {
            public void run() {
                prompt.extendedUI.get().submit( prompt );
                dialog.close();
            }
        });
        dialog.addAction( new Action( "CANCEL" ) {
            public void run() {
                dialog.close( );
            }
        });
        dialog.open();
    }
    
    
    @Override
    public void uploadStarted( ClientFile clientFile, InputStream in ) throws Exception {
        log.info( clientFile.getName() + " - " + clientFile.getType() + " - " + clientFile.getSize() );

        uploadProgress( resultSection.getBody(), -1 );
        
        // upload file
        assert clientFile.getName() != null : "Null client file name is not supported yet.";
        File f = new File( tempDir, clientFile.getName() );
        try (
            OutputStream out = new FileOutputStream( f )
        ){
            Timer timer = new Timer();
            byte[] buf = new byte[4096];
            AtomicLong count = new AtomicLong();
            for (int c=in.read( buf ); c>-1; c=in.read( buf )) {
                out.write( buf, 0, c );
                count.addAndGet( c );
                
                if (timer.elapsedTime() > 2000) {
                    Composite parent = resultSection.getBody();
                    if (parent.isDisposed()) {
                        break;  // stop uploading
                    }
                    else {
                        uploadProgress( resultSection.getBody(), count.get() );
                        timer.start();
                    }
                }
            }
        }
        catch (Exception e) {
            async( () -> site().toolkit().createSnackbar( Appearance.FadeIn, "Unable to upload file." ) );
            return;
        }

        async( () -> {
            // fires event which triggers UI update in ImportsContentProvider
            context.addContextOut( f );
        });
    }

    
    private void uploadProgress( Composite parent, long bytes ) {
        parent.getDisplay().asyncExec( () -> {
            parent.setLayout( new FillLayout( SWT.VERTICAL ) );
            UIUtils.disposeChildren( parent );
            tk().createFlowText( parent, "Uploading... " + (bytes >=0 ? byteCountToDisplaySize( bytes ) : "") );
            parent.layout();
        });        
    }
    
    
    protected DropTargetAdapter createDropTargetAdapter() {
        return new DropTargetAdapter() {
            @Override
            public void drop( DropTargetEvent ev ) {
                ClientFile[] clientFiles = (ClientFile[])ev.data;
                Arrays.stream( clientFiles ).forEach( clientFile -> {
                    log.info( clientFile.getName() + " - " + clientFile.getType() + " - " + clientFile.getSize() );

                    String uploadUrl = UploadService.registerHandler( ImportPanel.this );

                    ClientFileUploader uploader = RWT.getClient().getService( ClientFileUploader.class );
                    uploader.submit( uploadUrl, new ClientFile[] { clientFile } );
                } );
            }
        };
    }

}
