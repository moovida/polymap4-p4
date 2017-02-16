/* 
 * Copyright (C) 2015-2016, the @autors. All rights reserved.
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

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;
import static org.polymap.core.runtime.UIThreadExecutor.asyncFast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EventObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import java.lang.reflect.Field;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.ImmutableList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;

import org.polymap.core.runtime.SubMonitor;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.config.Configurable;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.toolkit.IPanelToolkit;

import org.polymap.p4.data.importer.ImporterFactory.ImporterBuilder;
import org.polymap.p4.data.importer.ImporterPrompt.Severity;
import org.polymap.p4.data.importer.archive.ArchiveFileImporterFactory;
import org.polymap.p4.data.importer.download.DownloadImporterFactory;
import org.polymap.p4.data.importer.geojson.GeoJSONImporterFactory;
import org.polymap.p4.data.importer.kml.KMLImporterFactory;
import org.polymap.p4.data.importer.raster.RasterImporterFactory;
import org.polymap.p4.data.importer.refine.csv.CSVFileImporterFactory;
import org.polymap.p4.data.importer.refine.excel.ExcelFileImporterFactory;
import org.polymap.p4.data.importer.shapefile.ShpImporterFactory;
import org.polymap.p4.data.importer.wfs.WfsImporterFactory;
import org.polymap.p4.data.importer.wms.WmsImporterFactory;

/**
 * Provides the execution context of an {@link Importer}. It handles inbound context
 * variables, allows to find importers that are able to execute within this context,
 * executes one of them and provides outbound variables for the next level driver.
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class ImporterContext
        extends Configurable {

    private static final Log log = LogFactory.getLog( ImporterContext.class );
    
    // XXX make this an extension point
    private static final Class[]            FACTORIES = { 
            ArchiveFileImporterFactory.class, 
            CSVFileImporterFactory.class, 
            ExcelFileImporterFactory.class, 
            KMLImporterFactory.class,
            GeoJSONImporterFactory.class,
            ShpImporterFactory.class,
            DownloadImporterFactory.class,
            WmsImporterFactory.class, 
            WfsImporterFactory.class,
            RasterImporterFactory.class,
            //OgrImporterFactory.class 
            };
    
    private Importer                        importer;
    
    private ImporterSite                    site;
    
    private Map<Class,Object>               contextIn = new HashMap();
    
    private Map<Class,Object>               contextOut = new HashMap();
    
    /** Ordered prompts of the importer. Keep insert order. */
    private Map<String,ImporterPrompt>      prompts = new LinkedHashMap();
    
    private UIJob                           verifier;

    private ImporterMonitor                 verifierMonitor;

    private Composite                       resultViewerParent;

    private IPanelToolkit                   resultViewerTk;


    /**
     * Creates a starting context without importer and no context set.
     */
    public ImporterContext() {
    }

    
    /**
     * Creates a context for the given {@link Importer}. The importer gets initialized by
     * this ctor.
     * 
     * @throws Exception If {@link Importer#init(ImporterSite, IProgressMonitor)}
     *         throws Exception.
     */
    public ImporterContext( Importer importer, Object[] contextIn, IProgressMonitor monitor )
            throws Exception {
        assert importer != null && contextIn != null;
        this.importer = importer;
        this.contextIn = stream( contextIn ).collect( toMap( o -> o.getClass(), o -> o ) );

        injectContextIn( importer, this.contextIn );
        
        site = new ImporterSite() {
            @Override
            public ImporterContext context() {
                return ImporterContext.this;
            }
            @Override
            public ImporterPrompt newPrompt( String id ) {
                assert prompts != null : "newPrompt() called before createPrompts()!";
                assert !prompts.containsKey( id );
                ImporterPrompt result = new ImporterPrompt() {
                    @Override
                    public ImporterContext context() {
                        return ImporterContext.this;
                    }                    
                };
                prompts.put( id, result );
                EventManager.instance().publish( new ContextChangeEvent( ImporterContext.this ) );
                return result;
            }
            @Override
            public Optional<ImporterPrompt> prompt( String id ) {
                return Optional.ofNullable( prompts.get( id ) );
            }
        };
        monitor.beginTask( "Initialize importer", 10 );
        // init
        SubMonitor.on( monitor, 1 ).complete( submon -> importer.init( site, submon ) );
        assert importer.site() == site;
        // create prompts
        SubMonitor.on( monitor, 1 ).complete( submon -> importer.createPrompts( submon ) );
        // verify
        promptChanged( null );
        //SubMonitor.on( monitor, 8 ).complete( submon -> importer.verify( submon ) );
        
        // listen to prompt changes
        EventManager.instance().subscribe( this, ev -> 
                ev instanceof ConfigChangeEvent && prompts.containsValue( ev.getSource() ) );
    }
    
    
    /**
     * One of my prompts has been changed.
     */
    @EventHandler( delay=100 )
    protected void promptChanged( List<ConfigChangeEvent> evs ) {
        // cancel current verifier Job
        if (verifier != null) {
            log.info( "Cancel VERIFIER!" );
            verifier.cancel();
            verifier.getThread().interrupt();
            verifier = null;
        }
        // new Job
        verifierMonitor = new ImporterMonitor();
        verifier = new UIJob( "Progress import" ) {
            @Override
            protected void runWithException( IProgressMonitor monitor ) throws Exception {
                verifierMonitor.beginTask( "Verifying data", IProgressMonitor.UNKNOWN );
                importer.verify( verifierMonitor );
                verifierMonitor.done();
                
                if (site().ok.get()) {
                    // check if all prompts also ok
                    for (ImporterPrompt prompt : prompts.values()) {
                        if (prompt.severity.isPresent() && prompt.severity.get().equals( Severity.REQUIRED ) && !prompt.ok.get()) {
                            // on first false, all are false
                            site().ok.set( false );
                            break;
                        }
                    }
                }
            }
        };
        verifier.addJobChangeListener( new JobChangeAdapter() {
            private UIJob myVerifier = verifier;
            @Override
            public void done( IJobChangeEvent ev ) {
                if (verifier == myVerifier) {
                    verifier = null;
                }
            }
        });
        if (resultViewerParent != null) {
            asyncFast( () -> updateResultViewer( resultViewerParent, resultViewerTk ) );
        }
        verifier.scheduleWithUIUpdate();
        // FIXME totally weird hack: the above scheduleWithUIUpdate() did a
        // syncFast() in previous version (which was wrong anyway); this is now gone
        // but this code here seems to need it to display prompts properly
        UIThreadExecutor.syncFast( () -> log.info( "SYNC" ) );
    }
    
    
    public Importer importer() {
        return importer;
    }


    public ImporterSite site() {
        return site;
    }


    /**
     * For the root ImportContext without an importer this add the given value to
     * {@link #contextOut} in order to have in handed to factories in
     * {@link #findNext(IProgressMonitor)}
     */
    public void addContextOut( Object o ) {
        contextOut.put( o.getClass(), o );
        EventManager.instance().syncPublish( new ContextChangeEvent( this ) );
    }

    
    /**
     * List of {@link Importer}s that are able to handle the current context.
     * 
     * @param monitor 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     */
    public List<ImporterContext> findNext( IProgressMonitor monitor ) throws Exception {
        monitor.beginTask( "Check importers", FACTORIES.length*10 );
        
        List<ImporterContext> result = new ArrayList();
        
        for (Class<ImporterFactory> cl : FACTORIES) {
            ImporterFactory factory = cl.newInstance();
            injectContextIn( factory, contextOut );
            SubProgressMonitor submon = new SubProgressMonitor( monitor, 10 );

            factory.createImporters( new ImporterBuilder() {
                @Override
                public void newImporter( Importer newImporter, Object... newContextIn ) throws Exception {
                    result.add( new ImporterContext( newImporter, newContextIn, submon ) );
                }
            });
            submon.done();
        }
        return result;
    }


    /**
     * Get or initially create prompts.
     *
     * @param monitor
     * @throws Exception
     */
    public List<ImporterPrompt> prompts( IProgressMonitor monitor ) throws Exception {
//        if (prompts == null) {
//            prompts = new HashMap();
//            importer.createPrompts( monitor );
//        }
        return ImmutableList.copyOf( prompts.values() );
    }

    
    protected Optional<Severity> maxNotOkPromptSeverity() {
        return prompts == null 
                ? Optional.empty()
                : prompts.values().stream()
                        .filter( prompt -> !prompt.ok.get() )
                        .map( prompt -> prompt.severity.get() )
                        .max( Comparator.comparing( s -> s.ordinal() ) );
    }
    

    /**
     * User has clicked this context in the list and wants to see the contents.
     */
    public void updateResultViewer( Composite parent, IPanelToolkit tk ) {
        resultViewerParent = parent;
        resultViewerTk = tk;
        JobChangeAdapter updateUI = new JobChangeAdapter() {
            @Override
            public void done( IJobChangeEvent ev ) {
                if (ev == null || ev.getResult().isOK()) {
                    asyncFast( () -> {
                        if (!parent.isDisposed()) {
                            UIUtils.disposeChildren( parent );
                            parent.setLayout( new FillLayout( SWT.VERTICAL ) );
                            importer.createResultViewer( parent, tk );
                            parent.layout( true );
                        }
                    });
                }
            }
        };
        if (verifier != null) {
            UIUtils.disposeChildren( parent );
            verifierMonitor.createContents( parent, tk );
            parent.layout( true );
            
            verifier.addJobChangeListenerWithContext( updateUI );
        }
        else {
            updateUI.done( null );
        }
    }


    public void createPromptViewer( Composite parent, ImporterPrompt prompt, IPanelToolkit tk  ) {
        assert parent.getLayout() instanceof FillLayout;
        prompt.extendedUI.ifPresent( uibuilder -> uibuilder.createContents( prompt, parent, tk ) );
    }


    public Map<Class,Object> execute( IProgressMonitor monitor ) throws Exception {
        importer.execute( monitor );

        // collect contextOut
        contextOut.clear();
        Class cl = importer.getClass();
        while (cl != null) {
            for (Field f : cl.getDeclaredFields()) {
                ContextOut a = f.getAnnotation( ContextOut.class );
                if (a != null) {
                    try {
                        f.setAccessible( true );
                        Object value = f.get( importer );
                        Object previous = contextOut.put( f.getType(), value );
                        if (previous != null) {
                            throw new IllegalStateException( "ContextOut already contains a value for the given type: " + value + " -- " + previous );
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException( e );
                    }
                }
            }
            cl = cl.getSuperclass();
        }
        return contextOut;
    } 

    
    protected void injectContextIn( Object obj, Map<Class,Object> values ) {
        Class cl = obj.getClass();
        while (cl != null) {
            for (Field f : cl.getDeclaredFields()) {
                ContextIn a = f.getAnnotation( ContextIn.class );
                if (a != null) {
                    try {
                        f.setAccessible( true );
                        Object value = values.get( f.getType() );
                        
                        // check direct match first
                        if (value != null) {
                            f.set( obj, value );                            
                        }
                        // check for assignable types
                        else {
                            List<Object> assignable = values.values().stream()
                                    .filter( v -> v != null && f.getType().isAssignableFrom( v.getClass() ) )
                                    .collect( Collectors.toList() );
                            assert assignable.size() <= 1 : "more than 1 value could be assigned to field " + f.getName() + " in " + cl.getName();
                            if (assignable.size() > 0) {
                                f.set( obj, assignable.get( 0 ) );
                            }
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException( e );
                    }
                }
            }
            cl = cl.getSuperclass();
        }
    }
    
    
    /**
     * Fired when {@link ImporterContext#addContextIn(Object)} is called after a file
     * was uploaded or anything else has been added to the context. Also fired when a
     * prompt has been added by calling {@link ImporterSite#newPrompt(String)} .
     */
    class ContextChangeEvent
            extends EventObject {
        
        protected ContextChangeEvent( ImporterContext source ) {
            super( source );
        }

        @Override
        public ImporterContext getSource() {
            return (ImporterContext)super.getSource();
        }
        
    }
    
}
