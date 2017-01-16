/* 
 * polymap.org
 * Copyright (C) 2016, the @authors. All rights reserved.
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
package org.polymap.p4.atlas.ui;

import java.util.Optional;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.json.JSONArray;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.vividsolutions.jts.geom.Envelope;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ViewerCell;

import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.SelectionAdapter;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.toolkit.ActionText;
import org.polymap.rhei.batik.toolkit.ClearTextAction;
import org.polymap.rhei.batik.toolkit.TextActionItem;
import org.polymap.rhei.batik.toolkit.TextActionItem.Type;
import org.polymap.rhei.batik.toolkit.md.MdListViewer;
import org.polymap.rhei.batik.toolkit.md.TreeExpandStateDecorator;

import org.polymap.p4.P4Panel;
import org.polymap.p4.atlas.AtlasFeatureLayer;
import org.polymap.p4.atlas.AtlasPlugin;
import org.polymap.p4.atlas.index.LayerQueryBuilder;
import org.polymap.rap.openlayers.base.OlEvent;
import org.polymap.rap.openlayers.base.OlEventListener;
import org.polymap.rap.openlayers.view.View;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class SearchPanel
        extends P4Panel
        implements OlEventListener {

    private static final Log log = LogFactory.getLog( SearchPanel.class );
    
    public static final PanelIdentifier ID = PanelIdentifier.parse( "atlas-search" );
    
    /** Inbound: */
    @Mandatory
    @Scope( AtlasPlugin.Scope )
    protected Context<IMap>         map;

    /** Inbound: */
    @Scope( AtlasPlugin.Scope )
    protected Context<MapViewer<ILayer>> mapExtent;
    
    private ReferencedEnvelope      currentExtent;

    private ActionText              searchText;

    private MdListViewer            viewer;
    
    private SearchContentProvider   contentProvider;

    
    @Override
    public boolean beforeInit() {
        if (parentPanel().isPresent() && parentPanel().get() instanceof AtlasMapPanel) {
            site().title.set( "" );
            site().tooltip.set( "Einträge suchen" );
            site().icon.set( AtlasPlugin.images().svgImage( "magnify.svg", AtlasPlugin.HEADER_ICON_CONFIG ) );
            return true;            
        }
        return false;
    }


    @Override
    public void dispose() {
        View view = mapExtent.get().getMap().view.get();
        view.removeEventListener( View.Event.resolution, this );
        view.removeEventListener( View.Event.rotation, this );
        view.removeEventListener( View.Event.center, this );

        super.dispose();
    }


    @Override
    public void createContents( Composite parent ) {
        site().title.set( "Suchen" );
        parent.setLayout( FormLayoutFactory.defaults().margins( 0, 8 ).spacing( 8 ).create() );
        
        // searchText
        searchText = tk().createActionText( parent, "" );
        new TextActionItem( searchText, Type.DEFAULT )
                .action.put( ev -> doSearch() )
                .text.put( "Suchen..." )
                .tooltip.put( "Fulltext search. Use * as wildcard.<br/>&lt;ENTER&gt; starts the search." )
                .icon.put( AtlasPlugin.images().svgImage( "magnify.svg", SvgImageRegistryHelper.DISABLED12 ) );
        new ClearTextAction( searchText );

        // viewer
        viewer = tk().createListViewer( parent, SWT.VIRTUAL, SWT.SINGLE, SWT.FULL_SELECTION );
        viewer.setContentProvider( 
                contentProvider = new SearchContentProvider() );
        viewer.firstLineLabelProvider.set( 
                new TreeExpandStateDecorator( viewer, 
                new SearchLabelProvider() ) );
        viewer.iconProvider.set( new LayersIconProvider() );
        viewer.addOpenListener( ev -> 
                SelectionAdapter.on( ev.getSelection() ).first( ILayer.class ).ifPresent( l -> doToggleLayer( l ) ) );
        viewer.setInput( map.get() );

        // XXX rough way to get mapExtent changes
        View view = mapExtent.get().getMap().view.get();
        view.addEventListener( View.Event.resolution, this );
        view.addEventListener( View.Event.rotation, this );
        view.addEventListener( View.Event.center, this );

        // layout
        FormDataFactory.on( searchText.getControl() ).fill().noBottom();
        FormDataFactory.on( viewer.getTree() ).fill().top( searchText.getControl() );
    }

    
    @Override
    public void handleEvent( OlEvent ev ) {
        JSONArray json = ev.properties().optJSONArray( "extent" );
        if (json != null) {
            Envelope extent = new Envelope( 
                    json.getDouble( 0 ), json.getDouble( 2 ), 
                    json.getDouble( 1 ), json.getDouble( 3 ) );

            CoordinateReferenceSystem crs = mapExtent.get().getMapCRS();
            ReferencedEnvelope newExtent = ReferencedEnvelope.create( extent, crs );
            if (!newExtent.equals( currentExtent )) {
                contentProvider.updateViewer( new LayerQueryBuilder()
                        .queryText.put( searchText.getTextText() )
                        .mapExtent.put( currentExtent = newExtent ) );
            }
        }
    }

    
    protected void doSearch() {
        Envelope extent = mapExtent.get().mapExtent.get();
        CoordinateReferenceSystem crs = mapExtent.get().getMapCRS();
        contentProvider.updateViewer( new LayerQueryBuilder()
                .queryText.put( searchText.getTextText() )
                .mapExtent.put( ReferencedEnvelope.create( extent, crs ) ) );
    }
    

    /**
     * 
     */
    protected void doToggleLayer( ILayer layer ) {
        viewer.toggleItemExpand( layer );
        
        AtlasFeatureLayer.of( layer ).thenAccept( o -> {
            boolean expanded = viewer.getExpandedState( layer );
            o.ifPresent( afl -> afl.visible.set( expanded ) );    
        });
    }
    
    
    /**
     * 
     */
    public class SearchLabelProvider
            extends CellLabelProvider {

        @Override
        public void update( ViewerCell cell ) {
            Object elm = cell.getElement();
            if (elm == SearchContentProvider.LOADING) {
                cell.setText( "Loading..." );
            }
            // IMap
            else if (elm instanceof IMap) {
                cell.setText( ((IMap)elm).label.get() );
            }
            // ILayer
            else if (elm instanceof ILayer) {
                ILayer layer = (ILayer)elm;
                String layerLabel = layer.label.get();
                
                Optional<Integer> childCount = contentProvider.cachedChildCount( layer );
                if (childCount.isPresent()) {
                    cell.setText( layerLabel + " (" + childCount.get() + ")" );
                }
                else {
                    cell.setText( layerLabel + " (..)" );
                    // poll contentProvider for child count
                    contentProvider.updateLayer( layer, -1 );
                    UIJob.schedule( "Layer child count: " + layer.label.get(), monitor -> {
                        for (int c=0; c<100; c++) {
                            Thread.sleep( 100 );
                            Optional<Integer> polled = contentProvider.cachedChildCount( layer );
                            if (polled.isPresent()) {
                                UIThreadExecutor.async( () -> { 
                                    cell.setText( layerLabel + " (" + polled.get() + ")" );
                                    if (AtlasFeatureLayer.of( layer ).get().get().visible.get()) {
                                        log.info( "expand: " + layer.label.get() );
                                        viewer.expandToLevel( layer, 1 );
                                    }
                                    return null;
                                });
                                break;
                            }
                        }
                    });
                }
            }
            // Feature
            else if (elm instanceof Feature) {
                Property prop = ((Feature)elm).getProperty( "Name" );
                if (prop != null) {
                    cell.setText( prop.getValue().toString() );
                    return;
                }
                prop = ((Feature)elm).getProperty( "NAME" );
                if (prop != null) {
                    cell.setText( prop.getValue().toString() );
                    return;
                }
                else {
                    cell.setText( ((Feature)elm).getIdentifier().getID() );
                }
            }
            else {
                throw new IllegalStateException( "Unknown element type: " + elm );
            }
        }
    }

    
    /**
     * 
     */
    protected final class LayersIconProvider
            extends CellLabelProvider {

        @Override
        public void update( ViewerCell cell ) {
            Object elm = cell.getElement();
            if (elm instanceof ILayer) {
                cell.setImage( AtlasPlugin.images().svgImage( "map-marker-multiple.svg", SvgImageRegistryHelper.NORMAL24 ) );
            }
        }
    }

}
