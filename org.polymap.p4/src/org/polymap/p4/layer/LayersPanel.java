/* 
 * polymap.org
 * Copyright (C) 2015, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.layer;

import static org.polymap.core.project.ui.ProjectNodeLabelProvider.PropType.Description;
import static org.polymap.core.project.ui.ProjectNodeLabelProvider.PropType.Label;
import static org.polymap.core.runtime.event.TypeEventFilter.ifType;
import static org.polymap.rhei.batik.app.SvgImageRegistryHelper.NORMAL24;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.beans.PropertyChangeEvent;

import org.opengis.feature.simple.SimpleFeatureType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.ViewerCell;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.operation.DefaultOperation;
import org.polymap.core.operation.OperationSupport;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ops.TwoPhaseCommitOperation;
import org.polymap.core.project.ui.ProjectNodeContentProvider;
import org.polymap.core.project.ui.ProjectNodeLabelProvider;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.SelectionAdapter;
import org.polymap.core.ui.StatusDispatcher;
import org.polymap.core.ui.StatusDispatcher.Style;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.toolkit.md.ActionProvider;
import org.polymap.rhei.batik.toolkit.md.CheckboxActionProvider;
import org.polymap.rhei.batik.toolkit.md.MdListViewer;
import org.polymap.rhei.batik.toolkit.md.MdToolkit;

import org.polymap.p4.Messages;
import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.map.ProjectMapPanel;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class LayersPanel
        extends P4Panel {

    private static Log log = LogFactory.getLog( LayersPanel.class );

    protected static final IMessages    i18n = Messages.forPrefix( "LayersPanel" );

    public static final PanelIdentifier ID = PanelIdentifier.parse( "layers" );

    @Mandatory
    @Scope(P4Plugin.Scope)
    protected Context<IMap>             map;
    
    /** Set before opening {@link LayerInfoPanel}. */
    @Scope(P4Plugin.Scope)
    protected Context<ILayer>           selected;
    
    private MdListViewer                viewer;
    
    /** The {@link MapViewer} of the parent panel ({@link ProjectMapPanel}). */
    private MapViewer<ILayer>           mapViewer;

    
    @Override
    public boolean beforeInit() {
        if (parentPanel().orElse( null ) instanceof ProjectMapPanel) {
            site().icon.set( P4Plugin.images().svgImage( "layers.svg", P4Plugin.HEADER_ICON_CONFIG ) );
            site().title.set( "" );
            return true;
        }
        return false;
    }


    @Override
    public void init() {
        super.init();
        mapViewer = ((ProjectMapPanel)parentPanel().get()).mapViewer;
    }

    @Override
    public void dispose() {
        EventManager.instance().unsubscribe( this );
        super.dispose();
    }


    @Override
    public void createContents( Composite parent ) {
        site().title.set( i18n.get( "title" ) );
        parent.setLayout( FormLayoutFactory.defaults().create() );
        
        viewer = ((MdToolkit)getSite().toolkit()).createListViewer( parent, SWT.SINGLE, SWT.FULL_SELECTION );
        viewer.setContentProvider( new ProjectNodeContentProvider() );

        viewer.firstLineLabelProvider.set( new ProjectNodeLabelProvider( Label ) );
        viewer.secondLineLabelProvider.set( new ProjectNodeLabelProvider( Description ) );
        viewer.iconProvider.set( new LayerIconProvider() );
        
        viewer.firstSecondaryActionProvider.set( new LayerVisibleAction());
        viewer.secondSecondaryActionProvider.set( new LayerDownAction() );
        viewer.thirdSecondaryActionProvider.set( new LayerUpAction() );
        
        viewer.addOpenListener( new IOpenListener() {
            @Override
            public void open( OpenEvent ev ) {
                SelectionAdapter.on( ev.getSelection() ).forEach( elm -> {
                    selected.set( (ILayer)elm );
                    getContext().openPanel( getSite().getPath(), LayerInfoPanel.ID );                        
                });
            }
        } );
        
//        int operations = DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_DEFAULT | DND.DROP_LINK;
//        DropTarget target = new DropTarget( viewer.getControl(), operations );
//        target.setTransfer( new Transfer[] {LocalSelectionTransfer.getTransfer()} );
//        
//        DragSource source = new DragSource( viewer.getControl(), operations );
//        source.setTransfer( new Transfer[] {LocalSelectionTransfer.getTransfer()} );
        
        viewer.setInput( map.get() );

        // avoid empty rows and lines
        viewer.getTree().setLayoutData( FormDataFactory.filled().noBottom().create() );
        
        // listen to ILayer changes
        EventManager.instance().subscribe( this, ifType( PropertyChangeEvent.class, 
                ev -> ev.getSource() instanceof ILayer && map.get().containsLayer( (ILayer)ev.getSource() ) ) );
    }

    
    @EventHandler( display=true, delay=10 )
    protected void layerChanged( List<PropertyChangeEvent> evs ) {
        if (viewer == null || viewer.getControl().isDisposed()) {
            EventManager.instance().unsubscribe( LayersPanel.this );            
        }
        else {
            viewer.refresh();
        }
    }
    

    /**
     * 
     */
    protected final class LayerIconProvider
            extends CellLabelProvider {

        private Map<Object,Image> legendGraphics = new HashMap();

        @Override
        public void update( ViewerCell cell ) {
            ILayer layer = (ILayer)cell.getElement();
            cell.setImage( legendGraphics.containsKey( layer.id() )
                    ? legendGraphics.get( layer.id() )
                    : P4Plugin.images().svgImage( "layers.svg", NORMAL24 ) );
            
//                new UIJob( "Legend graphic" ) {
//                    @Override
//                    protected void runWithException( IProgressMonitor monitor ) throws Exception {
//                        Thread.sleep( 3000 );
//                        UIThreadExecutor.async( () -> {
//                            legendGraphics.put( layer.id(), P4Plugin.instance().imageForName( "resources/icons/map.png" ) );
//                            viewer.update( layer, null );
//                        }, UIThreadExecutor.runtimeException() );
//                    }
//                }.scheduleWithUIUpdate();
        }
    }


    /**
     * 
     */
    protected final class LayerVisibleAction
            extends CheckboxActionProvider {
    
        @Override
        protected boolean initSelection( MdListViewer _viewer, Object elm ) {
            try {
                // check: feature layer without geom
                Optional<FeatureLayer> fl = FeatureLayer.of( (ILayer)elm ).get();
                if (fl.isPresent()) {
                    SimpleFeatureType schema = fl.get().featureSource().getSchema();
                    if (schema.getGeometryDescriptor() == null) {
                        return false;
                    }
                }
            }
            catch (Exception e) {
                log.warn( "", e );
            }
            
            return ((ILayer)elm).userSettings.get().visible.get();
        }

        @Override
        public void perform( MdListViewer _viewer, Object elm ) {
            FeatureLayer.of( (ILayer)elm ).thenAccept( fl -> {
                if (fl.isPresent()) {
                    if (fl.get().featureSource().getSchema().getGeometryDescriptor() != null) {
                        UIThreadExecutor.async( () -> {
                            super.perform( _viewer, elm );
                        });
                        return;
                    }
                    StatusDispatcher.handle( new Status( IStatus.INFO, P4Plugin.ID, i18n.get( "invisible" ) ), Style.SHOW, Style.LOG );
                }
                else {
                    super.perform( _viewer, elm );
                }
            })
            .exceptionally( e -> {
                StatusDispatcher.handleError( "Unable to set visibility of layer.", e );
                return null;
            });
        }
        
        @Override
        protected void onSelectionChange( MdListViewer _viewer, Object elm ) {
            ((ILayer)elm).userSettings.get().visible.set( isSelected( elm ) );
        }
    }


    /**
     * 
     */
    protected static class LayerUpAction
            extends ActionProvider {

        @Override
        public void update( ViewerCell cell ) {
            ILayer layer = (ILayer)cell.getElement();
            if (!layer.orderKey.get().equals( layer.maxOrderKey() )) {
                cell.setImage( P4Plugin.images().svgImage( "arrow-up.svg", P4Plugin.TOOLBAR_ICON_CONFIG ) );
            }
        }

        @Override
        public void perform( MdListViewer viewer, Object elm ) {
            DefaultOperation op = new TwoPhaseCommitOperation( "Layer up" ) {
                @Override
                protected IStatus doWithCommit( IProgressMonitor monitor, IAdaptable info ) throws Exception {
                    ILayer layer = (ILayer)elm;
                    register( layer.belongsTo() );
                    layer.orderUp( monitor );
                    return Status.OK_STATUS;
                }
            };
            OperationSupport.instance().execute( op, false, false );
        }    
    }

    
    /**
     * 
     */
    protected static class LayerDownAction
            extends ActionProvider {

        @Override
        public void update( ViewerCell cell ) {
            ILayer layer = (ILayer)cell.getElement();
            if (!layer.orderKey.get().equals( layer.minOrderKey() )) {
                cell.setImage( P4Plugin.images().svgImage( "arrow-down.svg", P4Plugin.TOOLBAR_ICON_CONFIG ) );
            }
        }

        @Override
        public void perform( MdListViewer viewer, Object elm ) {
            DefaultOperation op = new TwoPhaseCommitOperation( "Layer down" ) {
                @Override
                protected IStatus doWithCommit( IProgressMonitor monitor, IAdaptable info ) throws Exception {
                    ILayer layer = (ILayer)elm;
                    register( layer.belongsTo() );
                    layer.orderDown( monitor );
                    return Status.OK_STATUS;
                }
            };
            OperationSupport.instance().execute( op, false, false );
        }    
    }

}
