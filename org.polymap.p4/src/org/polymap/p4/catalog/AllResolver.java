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
package org.polymap.p4.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;

import org.geotools.data.FeatureSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.catalog.IMetadata;
import org.polymap.core.catalog.IMetadataCatalog;
import org.polymap.core.catalog.resolve.IMetadataResourceResolver;
import org.polymap.core.catalog.resolve.IResolvableInfo;
import org.polymap.core.catalog.resolve.IResourceInfo;
import org.polymap.core.catalog.resolve.IServiceInfo;
import org.polymap.core.catalog.resolve.ResourceResolverExtension;
import org.polymap.core.data.pipeline.DataSourceDescription;
import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.cache.Cache;
import org.polymap.core.runtime.cache.CacheConfig;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.layer.FeatureLayer;

/**
 * Provides the connection between an {@link ILayer} -> {@link IMetadata} ->
 * {@link IServiceInfo} and back.
 * <p/>
 * Holds a list of delegate {@link #resolvers} which are responsible of actually
 * creating a service/resource out of a metadata entry. A {@link ILayer} is connected
 * to a metadata entry via the {@link #resourceIdentifier(IResourceInfo)} which
 * consists of the metadata identifier and the resource name.
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class AllResolver
        implements IMetadataResourceResolver {

    private static Log log = LogFactory.getLog( AllResolver.class );

    public static final String                      ID_DELIMITER = "|";
    
    /**
     * Returns {@link P4Plugin#allResolver()}.
     */
    public static AllResolver instance() {
        return P4Plugin.allResolver();
    }
    
    
    // instance *******************************************
    
    private List<IMetadataCatalog>          catalogs;

    /** The delegates. */
    private List<IMetadataResourceResolver> resolvers; 

    /**
     * Caches {@link IResolvableInfo} instances in order to have just one underlying
     * service instances (WMS, Shape, RDataStore, etc.) per JVM.
     */
    private Cache<IMetadata,IResolvableInfo> resolved = CacheConfig.defaults().initSize( 128 ).createCache();
    
    /**
     * Caches {@link IMetadata} 
     */
    private Cache<String,IMetadata>         metadataCache = CacheConfig.defaults().initSize( 128 ).createCache();
    
    
    public AllResolver( List<IMetadataCatalog> catalogs ) {
        assert !catalogs.isEmpty();
        this.catalogs = catalogs;
        this.resolvers = ResourceResolverExtension.createAllResolvers(); 
    }


    public String resourceIdentifier( IResourceInfo res ) {
        IServiceInfo serviceInfo = res.getServiceInfo();
        IMetadata metadata = serviceInfo.getMetadata();
        return metadata.getIdentifier() + ID_DELIMITER + res.getName();
    }
    
    
    /**
     * Connects the given layer with its backend service.
     * <p/>
     * Consider {@link FeatureLayer} if interested in the {@link FeatureSource}
     * of a layer.
     *
     * @see FeatureLayer
     * @param layer
     * @param monitor
     * @return Newly created {@link DataSourceDescription} with (cached) service
     *         instance.
     * @throws Exception
     */
    public Optional<DataSourceDescription> connectLayer( ILayer layer, IProgressMonitor monitor ) throws Exception {
        IServiceInfo serviceInfo = serviceInfo( layer, monitor ).orElse( null );
        
        if (serviceInfo != null) {
            StringTokenizer tokens = new StringTokenizer( layer.resourceIdentifier.get(), ID_DELIMITER );
            @SuppressWarnings( "unused" )
            String metadataId = tokens.nextToken();
            String resName = tokens.nextToken();

            Object service = serviceInfo.createService( monitor );
            
            return Optional.of( new DataSourceDescription()
                    .service.put( service )
                    .resourceName.put( resName ) );
        }
        return Optional.empty();
    }
    

    // provided by FeatureSelection
//    /**
//     * Connects the given layer with its backend service.
//     *
//     * @param layer
//     * @param usecase
//     * @param monitor
//     * @return An optional {@link FeatureSource}. This is empty if no service was
//     *         found for the given layer or if the resource of the layer does not
//     *         support the given usecase.
//     * @throws Exception
//     */
//    public Optional<PipelineFeatureSource> connectLayer( ILayer layer, Class<? extends PipelineProcessor> usecase, 
//            IProgressMonitor monitor ) throws Exception {
//        // resolve service
//        Optional<DataSourceDescription> dsd = AllResolver.instance().connectLayer( layer, monitor );
//        
//        if (dsd.isPresent()) {
//            // create pipeline for it
//            Pipeline pipeline = P4PipelineIncubator.forLayer( layer ).newPipeline( usecase, dsd.get(), null );
//            if (pipeline == null || pipeline.length() == 0) {
//                return Optional.empty(); //throw new PipelineIncubationException( "Unable to build pipeline for: " + dsd );
//            }
//            return Optional.of( new PipelineFeatureSource( pipeline ) );
//        }
//        else {
//            return Optional.empty();
//        }
//    }

    
    public Optional<IMetadata> metadata( ILayer layer, IProgressMonitor monitor ) throws Exception {
        StringTokenizer tokens = new StringTokenizer( layer.resourceIdentifier.get(), ID_DELIMITER );
        String metadataId = tokens.nextToken();
        
        return Optional.ofNullable( metadataCache.get( metadataId, key -> {
            for (IMetadataCatalog catalog : catalogs) {
                Optional<? extends IMetadata> result = catalog.entry( metadataId, monitor );
                if (result.isPresent()) {
                    return result.get();
                }
            }
            return null;
        }));
    }
    
    
    
    public Optional<IServiceInfo> serviceInfo( ILayer layer, IProgressMonitor monitor ) throws Exception {
        IMetadata metadata = metadata( layer, monitor ).orElse( null );

        return Optional.ofNullable( metadata != null
                ? (IServiceInfo)resolve( metadata, monitor ) : null ); 
    }
    
    
    public Optional<IResourceInfo> resInfo( ILayer layer, IProgressMonitor monitor ) throws Exception {
        IServiceInfo serviceInfo = serviceInfo( layer, monitor ).orElse( null );
        
        if (serviceInfo != null) {
            StringTokenizer tokens = new StringTokenizer( layer.resourceIdentifier.get(), ID_DELIMITER );
            @SuppressWarnings( "unused" )
            String metadataId = tokens.nextToken();
            String resName = tokens.nextToken();

            for (IResourceInfo info : serviceInfo.getResources( monitor )) {
                if (info.getName().equals( resName )) {
                    return Optional.of( info );
                }
            }
        }
        return Optional.empty();        
    }
    
    
    // IMetadataResourceResolver **************************
    
    @Override
    public boolean canResolve( IMetadata metadata ) {
        return resolved.get( metadata ) == null
                ? resolvers.stream().filter( r -> r.canResolve( metadata ) ).findFirst().isPresent()
                : true;
    }

    
    /**
     * Resolves info for the given metadata and caches the result.
     * <p>
     * This usually <b>blocks</b> execution until backend service is available and/or connected.
     * 
     * @param metadata
     * @param monitor
     * @return Created or cached info instance.
     */
    @Override
    public IResolvableInfo resolve( IMetadata metadata, IProgressMonitor monitor ) throws Exception {
        return resolved.get( metadata, key -> {
            for (IMetadataResourceResolver resolver : resolvers) {
                if (resolver.canResolve( metadata ) ) {
                    return resolver.resolve( metadata, monitor );
                }            
            }
            return (IResolvableInfo)null;
        });
    }

    
    @Override
    public Map<String,String> createParams( Object service ) {
        // XXX Auto-generated method stub
        throw new RuntimeException( "not yet implemented." );
    }
    
}
