/* 
 * polymap.org
 * Copyright (C) 2015-2016, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.map;

import static org.polymap.core.runtime.event.TypeEventFilter.isType;
import static org.polymap.core.ui.FormDataFactory.on;
import static org.polymap.p4.layer.FeatureLayer.ff;

import java.util.function.Consumer;

import org.geotools.data.FeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.json.JSONArray;
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.polymap.core.data.Features;
import org.polymap.core.data.util.Geometries;
import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ProjectNode.ProjectNodeCommittedEvent;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.security.SecurityContext;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.StatusDispatcher;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.PropertyAccessEvent;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.contribution.ContributionManager;
import org.polymap.rhei.batik.toolkit.Snackbar.Appearance;
import org.polymap.p4.Messages;
import org.polymap.p4.P4AppDesign;
import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.layer.FeatureSelectionTable;
import org.polymap.p4.project.ProjectRepository;
import org.polymap.rap.openlayers.base.OlEvent;
import org.polymap.rap.openlayers.base.OlEventListener;
import org.polymap.rap.openlayers.base.OlMap;
import org.polymap.rap.openlayers.base.OlMap.Event;
import org.polymap.rap.openlayers.control.MousePositionControl;
import org.polymap.rap.openlayers.control.ScaleLineControl;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class ProjectMapPanel
        extends P4Panel
        implements OlEventListener {

    private static Log log = LogFactory.getLog( ProjectMapPanel.class );

    public static final PanelIdentifier ID = PanelIdentifier.parse( "start" );
    
    public static final String          BOTTOM_TOOLBAR_TAG = "ProjectMapPanel.Bottom";

    private static final IMessages      i18n = Messages.forPrefix( "ProjectPanel" );

    /**
     * The map of this P4 instance. This instance belongs to
     * {@link ProjectRepository#unitOfWork()}.
     */
    @Scope( P4Plugin.Scope )
    protected Context<IMap>             map;

    public MapViewer<ILayer>            mapViewer;

    private Composite                   tableParent;
    
    
    @Override
    public void init() {
        super.init();
        
        // the 'start' panel initializes context
        map.compareAndSet( null, ProjectRepository.unitOfWork().entity( IMap.class, "root" ) );
        
        // XXX fake user login; used by ProjectNodeUser for example
        SecurityContext sc = SecurityContext.instance();
        if (!sc.isLoggedIn()) {
            if (!sc.login( "admin", "admin" )) {
                throw new RuntimeException( "Default/fake login did not succeed." );
            }
        }
        
        //
        featureLayer.addListener( this, ev -> ev.getType() == PropertyAccessEvent.TYPE.SET );
        
        // listen to maxExtent changes
        EventManager.instance().subscribe( this, isType( ProjectNodeCommittedEvent.class, ev -> 
                ev.getEntityId().equals( map.get().id() ) ) );
    }

    
    @Override
    public void dispose() {
        EventManager.instance().unsubscribe( this );
    }

    
    @EventHandler( display=true )
    protected void onFeatureLayerSet( PropertyAccessEvent ev ) {
        if (featureLayer.isPresent()) {
            createTableView();
        }
        else {
            closeButtomView();
        }
    }

    
    @EventHandler( display=true )
    protected void onMapChange( ProjectNodeCommittedEvent ev ) {
        ReferencedEnvelope mapMaxExtent = map.get().maxExtent();
        ReferencedEnvelope viewerMaxExtent = mapViewer.maxExtent.get();
        if (!mapMaxExtent.equals( viewerMaxExtent )) {
            mapViewer.maxExtent.set( mapMaxExtent );
            mapViewer.mapExtent.set( mapMaxExtent );
        }
    }

    
    @Override
    public void createContents( Composite parent ) {
        // title and layout
        site().title.set( P4AppDesign.appTitle );
        site().setSize( SIDE_PANEL_WIDTH/2, Integer.MAX_VALUE, Integer.MAX_VALUE );
        
        //parent.setBackground( UIUtils.getColor( 0xff, 0xff, 0xff ) );
        parent.setLayout( FormLayoutFactory.defaults().margins( 0, 0, 5, 0 ).spacing( 0 ).create() );
        
//        // buttom toolbar
//        MdToolbar2 tb = ((MdToolkit)site().toolkit()).createToolbar( parent, SWT.FLAT );
//        on( tb.getControl() ).fill().noTop();
//        tb.getControl().moveAbove( null );
//        ContributionManager.instance().contributeTo( tb, this, BOTTOM_TOOLBAR_TAG );
        
        // table area
        tableParent = on( site().toolkit().createComposite( parent, SWT.NONE ) )
                .fill().noTop().height( 0 ).control();
        
        // mapViewer
        try {
            mapViewer = new MapViewer( parent );
            // triggers {@link MapViewer#refresh()} on {@link ProjectNodeCommittedEvent} 
            mapViewer.contentProvider.set( new ProjectContentProvider() );
            mapViewer.layerProvider.set( new ProjectLayerProvider() );
            
            ReferencedEnvelope maxExtent = map.get().maxExtent();
            log.info( "maxExtent: " + maxExtent );
            mapViewer.maxExtent.set( maxExtent );
            
            mapViewer.addMapControl( new MousePositionControl() );
            mapViewer.addMapControl( new ScaleLineControl() );
            
            mapViewer.setInput( map.get() );
            on( mapViewer.getControl() ).fill().bottom( tableParent );
            mapViewer.getControl().moveBelow( null );
            mapViewer.getControl().setBackground( UIUtils.getColor( 0xff, 0xff, 0xff ) );
            
            mapViewer.mapExtent.set( maxExtent );

            //
            OlMap olmap = mapViewer.getMap();
            olmap.addEventListener( Event.click, this );
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }

        ContributionManager.instance().contributeTo( this, this );
    }

    
    @Override
    public void handleEvent( OlEvent ev ) {
        log.info( "event: " + ev.properties() );
        JSONArray coord = ev.properties().getJSONObject( "feature" ).getJSONArray( "coordinate" );
        double x = coord.getDouble( 0 );
        double y = coord.getDouble( 1 );
        
        if (featureLayer.isPresent()) {
            try {
                clickFeature( featureLayer.get().featureSource(), new Coordinate( x, y ) );
            }
            catch (Exception e) {
                StatusDispatcher.handleError( "Unable to select feature.", e );
            }
        }
        else {
            tk().createSnackbar( Appearance.FadeIn, "No layer activated" );
        }
    }

    
    protected void clickFeature( FeatureStore fs, Coordinate clicked ) throws Exception {
        CoordinateReferenceSystem mapCrs = Geometries.crs( map.get().srsCode.get() );
        GeometryFactory gf = new GeometryFactory();

        Point point = gf.createPoint( clicked );

        // buffer: 50m
        double buffer = 50;
        Point norm = Geometries.transform( point, mapCrs, Geometries.crs( "EPSG:3857" ) );
        ReferencedEnvelope buffered = new ReferencedEnvelope(
                norm.getX()-buffer, norm.getX()+buffer, norm.getY()-buffer, norm.getY()+buffer,
                Geometries.crs( "EPSG:3857" ) );
        
        // transform -> dataCrs
        CoordinateReferenceSystem dataCrs = fs.getSchema().getCoordinateReferenceSystem();
        buffered = buffered.transform( dataCrs, true );

        // get feature
        Filter filter = ff.intersects( ff.property( "" ), ff.literal( JTS.toGeometry( (Envelope)buffered ) ) );
        FeatureCollection selected = fs.getFeatures( filter );
        if (selected.isEmpty()) {
            return; // nothing found
        }
        if (selected.size() > 1) {
            log.info( "Multiple features found: " + selected.size() );
        }
        Feature any = (Feature)Features.stream( selected ).findAny().get();
        featureLayer.get().setClicked( any );
        log.info( "clicked: " + any );
    }

    
    /**
     * Simple/experimental way to add bottom view to this panel.
     *
     * @param creator
     */
    protected void updateButtomView( Consumer<Composite> creator ) {
        on( tableParent ).height( 250 );
        
        UIUtils.disposeChildren( tableParent );
        creator.accept( tableParent );
        tableParent.getParent().layout( true );
    }


    protected void closeButtomView() {
        on( tableParent ).height( 0 );
        UIUtils.disposeChildren( tableParent );
        tableParent.getParent().layout( true );
    }
    
    
    /**
     * Creates the table for the current {@link #featureLayer}.
     */
    protected void createTableView() {
        updateButtomView( parent -> {            
            tk().createFlowText( parent, " Loading " + featureLayer.get().layer().label.get() + "..." );
            parent.layout();

            UIThreadExecutor.async( () -> {
                UIUtils.disposeChildren( parent );
                new FeatureSelectionTable( parent, featureLayer.get(), ProjectMapPanel.this ) {
                    @Override
                    protected void close() {
                        featureLayer.set( null );
                    }
                };
                parent.layout();
            });
        });
    }
    
}
