/* 
 * polymap.org
 * Copyright (C) 2017, Falko Bräutigam. All rights reserved.
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.opengis.coverage.grid.GridCoverage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.core.runtime.IProgressMonitor;
import org.polymap.core.data.pipeline.DataSourceDescription;
import org.polymap.core.data.pipeline.PipelineIncubationException;
import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.JobExecutor;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.cache.Cache;
import org.polymap.core.runtime.session.SessionSingleton;

import org.polymap.p4.catalog.AllResolver;

/**
 * Connection to the data source of layers connected to {@link GridCoverage} (raster data).
 * There is one instance per layer per session, retrieved via {@link #of(ILayer)}.
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class RasterLayer {
    
    private static final Log log = LogFactory.getLog( RasterLayer.class );

    /**
     * Waits for the {@link RasterLayer} of the given {@link ILayer}.
     * <p/>
     * Avoid calling just {@link CompletableFuture#get()} as this may block the
     * calling (UI) thread. Instead register callbacks that handle the result
     * asynchronously.
     * <p/>
     * The callbacks are called from within an {@link UIJob}. Use
     * {@link UIThreadExecutor} to do somethinf in the display thread.
     * <p/>
     * <b>Example usage:</b>
     * <pre>
     *      RasterLayer.of( layer ).thenAccept( rl -> {
     *          if (rl.isPresent()) {
     *              ...
     *          }
     *          else {
     *              ...
     *          }
     *      })
     *      .exceptionally( e -> {
     *          StatusDispatcher.handleError( "", e );
     *          return null;
     *      });
     * </pre>
     * 
     * @param layer
     */
    public static CompletableFuture<Optional<RasterLayer>> of( ILayer layer ) {
        return CompletableFuture.supplyAsync( () -> {
            SessionHolder session = SessionHolder.instance( SessionHolder.class );
            RasterLayer result = session.instances.computeIfAbsent( (String)layer.id(), key -> { 
                try {
                    IProgressMonitor monitor = UIJob.monitorOfThread();
                    return new RasterLayer( layer ).doConnectLayer( monitor );
                }
                catch (Exception e) {
                    throw new CompletionException( e );
                }
            });
            return result.isValid() ? Optional.of( result ) : Optional.empty();
        }, JobExecutor.instance() );
    }
    

    private static class SessionHolder
            extends SessionSingleton {
    
        /**
         * Using {@link ConcurrentHashMap} instead of {@link Cache} ensures that
         * mapping function is executed at most once per key and just one
         * {@link RasterLayer} instance is constructed per {@link ILayer}.
         */
        ConcurrentMap<String,RasterLayer>    instances = new ConcurrentHashMap( 32 );
    }
    
    
    // instance *******************************************
    
    private ILayer                      layer;
    
    private GridCoverage2DReader        gridCoverageReader;

    private GridCoverage2D              gridCoverage;
    
    
    protected RasterLayer( ILayer layer ) {
        this.layer = layer;
    }

    
    protected RasterLayer doConnectLayer( IProgressMonitor monitor ) throws PipelineIncubationException, Exception {
        log.info( "doConnectLayer(): " + layer.label.get() );
        assert gridCoverageReader == null;

        // resolve service
        DataSourceDescription dsd = AllResolver.instance().connectLayer( layer, monitor )
                .orElseThrow( () -> new RuntimeException( "No data source for layer: " + layer ) );

        if (dsd.service.get() instanceof GridCoverage2DReader) { 
            gridCoverageReader = (GridCoverage2DReader)dsd.service.get();
            gridCoverage = gridCoverageReader.read( dsd.resourceName.get(), null );
        
//            // XXX create pipeline for it
//            Pipeline pipeline = P4PipelineIncubator.forLayer( layer )
//                    .newPipeline( FeaturesProducer.class, dsd, null );
//            if (pipeline != null && pipeline.length() > 0) {
//                fs = new PipelineFeatureSource( pipeline );
//            }
        }
        return this;
    }

    
    public GridCoverage2DReader gridCoverageReader() {
        return gridCoverageReader;
    }


    public GridCoverage2D gridCoverage() {
        return gridCoverage;
    }


    @Override
    public boolean equals( Object obj ) {
        if (this == obj) {
            return true;
        }
        else if (obj instanceof RasterLayer) {
            RasterLayer rhs = (RasterLayer)obj;
            return layer.id().equals( rhs.layer.id() );
        }
        return false;
    }


    public boolean isValid() {
        return gridCoverageReader != null;
    }


    public ILayer layer() {
        assert isValid();
        return layer;
    }

}
