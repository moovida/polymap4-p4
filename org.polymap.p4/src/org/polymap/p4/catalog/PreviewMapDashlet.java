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
package org.polymap.p4.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.data.DataAccess;
import org.geotools.data.FeatureSource;
import org.geotools.data.wms.WebMapServer;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.catalog.resolve.IResolvableInfo;
import org.polymap.core.catalog.resolve.IResourceInfo;
import org.polymap.core.catalog.resolve.IServiceInfo;
import org.polymap.core.data.image.EncodedImageProducer;
import org.polymap.core.data.pipeline.DataSourceDescription;
import org.polymap.core.data.pipeline.Pipeline;
import org.polymap.core.data.util.Geometries;
import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.mapeditor.OlContentProvider;
import org.polymap.core.mapeditor.OlLayerProvider;
import org.polymap.core.mapeditor.services.SimpleWmsServer;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.config.Config2;
import org.polymap.core.runtime.config.ConfigurationFactory;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.dashboard.DashletSite;
import org.polymap.rhei.batik.dashboard.DefaultDashlet;
import org.polymap.rhei.batik.toolkit.MinHeightConstraint;
import org.polymap.rhei.batik.toolkit.MinWidthConstraint;

import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.data.P4PipelineIncubator;
import org.polymap.rap.openlayers.base.OlFeature;
import org.polymap.rap.openlayers.control.MousePositionControl;
import org.polymap.rap.openlayers.control.ScaleLineControl;
import org.polymap.rap.openlayers.format.GeoJSONFormat;
import org.polymap.rap.openlayers.geom.PolygonGeometry;
import org.polymap.rap.openlayers.layer.ImageLayer;
import org.polymap.rap.openlayers.layer.Layer;
import org.polymap.rap.openlayers.layer.TileLayer;
import org.polymap.rap.openlayers.layer.VectorLayer;
import org.polymap.rap.openlayers.source.ImageSource;
import org.polymap.rap.openlayers.source.ImageWMSSource;
import org.polymap.rap.openlayers.source.TileWMSSource;
import org.polymap.rap.openlayers.source.VectorSource;
import org.polymap.rap.openlayers.source.WMSRequestParams;
import org.polymap.rap.openlayers.style.StrokeStyle;
import org.polymap.rap.openlayers.style.Style;
import org.polymap.rap.openlayers.types.Color;
import org.polymap.rap.openlayers.types.Coordinate;

/**
 * Preview {@link MapViewer} of an {@link IServiceInfo} or {@link IResourceInfo}.
 *
 * @author Falko Bräutigam
 */
public class PreviewMapDashlet
        extends DefaultDashlet {

    private static final Log log = LogFactory.getLog( PreviewMapDashlet.class );
    
    /** Defaults to EPSG:3857 */
    public Config2<PreviewMapDashlet,CoordinateReferenceSystem> mapCrs;
    
    private IServiceInfo        serviceInfo;
    
    private IResourceInfo       resInfo;
    
    private MapViewer           mapViewer;
    
    private List<Layer>         layers = new ArrayList();

    private String              featureWmsAlias;

    private Layer<ImageSource>  bgLayer;

    
    public PreviewMapDashlet( IResolvableInfo info ) {
        ConfigurationFactory.inject( this );
        try {
            mapCrs.set( Geometries.crs( "EPSG:3857" ) );
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
        
        if (info instanceof IResourceInfo) {
            resInfo = (IResourceInfo)info;
            serviceInfo = resInfo.getServiceInfo(); 
        }
        else {
            throw new IllegalArgumentException( "Unhandled: " + info );
        }
    }
    
    
    @Override
    public void init( DashletSite site ) {
        super.init( site );
        site.title.set( "Preview" );
        //site.constraints.get().add( new PriorityConstraint( 100 ) );
        site.constraints.get().add( new MinWidthConstraint( P4Panel.SIDE_PANEL_WIDTH, 1 ) );
        site.constraints.get().add( new MinHeightConstraint( P4Panel.SIDE_PANEL_WIDTH-50, 1 ) );
        site.border.set( false );
    }

    
    @Override
    public void dispose() {
        if (featureWmsAlias != null) {
            P4Plugin.instance().httpService().unregister( featureWmsAlias );
            featureWmsAlias = null;
        }
    }


    @Override
    protected void finalize() throws Throwable {
        dispose();
    }


    @Override
    public void createContents( Composite parent ) {
        new UIJob( "Create map") {
            @Override
            protected void runWithException( IProgressMonitor monitor ) throws Exception {
                try {
                    Object service = serviceInfo.createService( monitor );
                    UIThreadExecutor.async( () -> { createMap( parent, service ); return null; } );
                }
                catch (Exception e) {
                    log.warn( "", e );
                    getSite().toolkit().createLabel( parent, "Unable to created preview.", SWT.WRAP );
                } 
            }
        }.scheduleWithUIUpdate();
    }
    
    
    protected void createMap( Composite parent, Object service ) throws Exception {
        parent.setLayout( new FillLayout() );
        parent.setBackground( UIUtils.getColor( 0xff, 0xff, 0xff ) );

        mapViewer = new MapViewer( parent );
        mapViewer.contentProvider.set( new OlContentProvider() );
        mapViewer.layerProvider.set( new OlLayerProvider() {
            @Override
            public int getPriority( Layer elm ) {
                if (elm instanceof VectorLayer) {
                    return 10;
                }
                if (elm == bgLayer) {
                    return 0;
                }
                return 5;
            }
        });
        mapViewer.addMapControl( new MousePositionControl() );
        mapViewer.addMapControl( new ScaleLineControl() );
        
        createBackgroundLayer();
        
        // WMS
        ReferencedEnvelope bounds = null;
        if (service instanceof WebMapServer) {
            bounds = createWmsLayer( (WebMapServer)service );
        }
        // Features
        else if (service instanceof DataAccess) {
            bounds = createFeatureLayer( (DataAccess)service );
        }
        //
        else {
            throw new Exception( "Preview of raster data is not yet supported." );
        }

        if (bounds != null && !bounds.isNull()) {
            try {
                bounds = bounds.transform( mapCrs.get(), false );
            }
            catch (Exception e) {
                log.warn( "", e );
            }
            createFenceLayer( bounds );
            mapViewer.maxExtent.set( bounds );
        }
        mapViewer.setInput( layers.toArray() );
        //mapViewer.mapExtent.set( bounds );

        parent.layout();
    }

    
    protected void createFenceLayer( ReferencedEnvelope bounds ) {
        VectorSource vectorSource = new VectorSource()
                .format.put( new GeoJSONFormat() );
               // .attributions.put( Arrays.asList( new Attribution( "Data extent" ) ) );

        VectorLayer vectorLayer = new VectorLayer()
                .style.put( new Style()
//                        .fill.put( new FillStyle()
//                                .color.put( new Color( 0, 0, 255, 0.1f ) ) )
                        .stroke.put( new StrokeStyle()
                                .color.put( new Color( "red" ) )
                                .width.put( 2.5f ) ) )
                .source.put( vectorSource );

        List<Coordinate> coords = Arrays.stream( JTS.toGeometry( bounds ).getCoordinates() )
                .map( c -> new Coordinate( c.x, c.y ) )
                .collect( Collectors.toList() );

        OlFeature feature = new OlFeature();
        feature.name.set( "Fence" );
        feature.geometry.set( new PolygonGeometry( coords ) );
        vectorSource.addFeature( feature );
        
        layers.add( vectorLayer );
    }
    
    
    protected ReferencedEnvelope createWmsLayer( WebMapServer wms ) {
        String url = wms.getInfo().getSource().toString();
        String layerName = resInfo.getName();
        layers.add( new ImageLayer()
                .source.put( new ImageWMSSource()
                        .url.put( url )
                        .params.put( new WMSRequestParams().layers.put( layerName ) ) ) );
        
        org.geotools.data.ows.Layer layer = wms.getCapabilities().getLayerList().stream()
                .filter( l -> layerName.equals( l.getName() ) )
                .findFirst().get();
        return wms.getInfo( layer ).getBounds();
    }
    
    
    protected void createBackgroundLayer() {
        layers.add( bgLayer = new ImageLayer()
                .source.put( new ImageWMSSource()
                        .url.put( "http://ows.terrestris.de/osm/service/" )
                        .params.put( new WMSRequestParams().layers.put( "OSM-WMS" ) ) ) );
    }

    
    protected ReferencedEnvelope createFeatureLayer( DataAccess ds ) throws Exception {
        FeatureSource fs = ds.getFeatureSource( new NameImpl( resInfo.getName() ) );
        
        // WMS server
        SimpleWmsServer wms = new SimpleWmsServer() {
            @Override
            protected String[] layerNames() {
                return new String[] {resInfo.getName()};
            }
            @Override
            protected Pipeline createPipeline( String layerName ) {
                try {
                    DataSourceDescription dsd = new DataSourceDescription().service.put( ds ).resourceName.put( layerName );
                    Pipeline pipeline = new P4PipelineIncubator().newPipeline( EncodedImageProducer.class, dsd, null );
                    assert pipeline != null && pipeline.length() > 0 : "Unable to build pipeline for: " + dsd;
                    return pipeline;
                }
                catch (Exception e) {
                    log.warn( "", e );
                    return null;
                }
            }
        };
        featureWmsAlias = "/preview" + hashCode();
        P4Plugin.instance().httpService().registerServlet( featureWmsAlias, wms, null, null );
        
        // layer
        String layerName = resInfo.getName();
        layers.add( new TileLayer()
                .source.put( new TileWMSSource()
                        .url.put( "." + featureWmsAlias )
                        .params.put( new WMSRequestParams()
                                .version.put( "1.1.1" )  // send "SRS" param
                                .layers.put( layerName )
                                .format.put( "image/png" ) ) ) );
        
        return fs.getBounds();
    }    

}
