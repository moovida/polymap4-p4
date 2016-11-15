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
package org.polymap.p4.project;

import java.io.File;
import java.io.IOException;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.polymap.core.CorePlugin;
import org.polymap.core.catalog.IMetadata;
import org.polymap.core.catalog.resolve.IResourceInfo;
import org.polymap.core.catalog.resolve.IServiceInfo;
import org.polymap.core.data.util.Geometries;
import org.polymap.core.project.EnvelopeComposite;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.runtime.session.SessionContext;
import org.polymap.core.runtime.session.SessionSingleton;

import org.polymap.model2.runtime.EntityRepository;
import org.polymap.model2.runtime.UnitOfWork;
import org.polymap.model2.runtime.locking.OptimisticLocking;
import org.polymap.model2.store.recordstore.RecordStoreAdapter;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.catalog.AllResolver;
import org.polymap.p4.catalog.LocalCatalog;
import org.polymap.recordstore.lucene.LuceneRecordStore;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class ProjectRepository {

    private static final Log log = LogFactory.getLog( ProjectRepository.class );
    
    public static final String          ROOT_MAP_ID = "root";

    private static EntityRepository     repo;

    static {
        try {
            File dir = new File( CorePlugin.getDataLocation( P4Plugin.instance() ), "project" );
            dir.mkdirs();
            LuceneRecordStore store = LuceneRecordStore.newConfiguration()
                    .indexDir.put( dir )
                    .clean.put( false )
                    .executor.put( null )
                    .create();
            
            repo = EntityRepository.newConfiguration()
                    .entities.set( new Class[] {
                            IMap.class, 
                            ILayer.class, 
                            ILayer.LayerUserSettings.class, 
                            MetadataReference.class } )
                    .store.set( 
                            new OptimisticLocking( 
                            new RecordStoreAdapter( store ) ) )
                    .create();
            
            checkInitRepo();
        }
        catch (IOException e) {
            throw new RuntimeException( e );
        }
    }
    
    protected static void checkInitRepo() {
        try (
            UnitOfWork uow = repo.newUnitOfWork()
        ){
            String srs = "EPSG:3857";
            CoordinateReferenceSystem epsg3857 = Geometries.crs( srs );
            ReferencedEnvelope maxExtent = new ReferencedEnvelope( 
                    -20026376.39, 20026376.39, -20048966.10, 20048966.10, epsg3857 );

            IMap map = uow.entity( IMap.class, "root" );
            if (map == null) {
                // The one and only project of a P4 instance
                map = uow.createEntity( IMap.class, ROOT_MAP_ID, (IMap proto) -> {
                    proto.label.set( "Project" );
                    proto.srsCode.set( srs );
                    proto.maxExtent.createValue( EnvelopeComposite.defaults( maxExtent ) );
                    return proto;
                });
                
                // default background layer from mapzone.io
                try {
                    NullProgressMonitor monitor = new NullProgressMonitor();
                    IMetadata md = P4Plugin.localCatalog().entry( LocalCatalog.WORLD_BACKGROUND_ID, monitor ).get();
                    IServiceInfo service = (IServiceInfo)AllResolver.instance().resolve( md ).get();
                    for (IResourceInfo res : service.getResources( monitor )) {
                        if ("Simple".equalsIgnoreCase( res.getName() ) ) {
                            ILayer layer = uow.createEntity( ILayer.class, null, (ILayer proto) -> {
                                proto.label.set( "World" );
                                proto.description.set( res.getDescription().orElse( null ) );
                                proto.resourceIdentifier.set( AllResolver.resourceIdentifier( res ) );
                                proto.orderKey.set( 1 );
                                return proto;
                            });
                            layer.parentMap.set( map );
                        }
                    }
                }
                catch (Exception e) {
                    log.warn( "Error while creating default background layer.", e );
                }
            }
            else {
                // convert legacy setting
                if (map.maxExtent().getMinX() == ReferencedEnvelope.EVERYTHING.getMinX() ) {
                    log.info( "Converting old EVERYTHING envelope..." );
                    map.srsCode.set( srs );
                    map.maxExtent.createValue( EnvelopeComposite.defaults( maxExtent ) );
                }
            }
            uow.commit();
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
    }

    
    /**
     * The instance of the current {@link SessionContext}.
     */
    public static UnitOfWork unitOfWork() {
        return SessionHolder.instance( SessionHolder.class ).uow;
    }

    
    static class SessionHolder
            extends SessionSingleton {
        UnitOfWork uow = repo.newUnitOfWork();
    }
    
    
    public static UnitOfWork newUnitOfWork() {
        return repo.newUnitOfWork();
    }
    
}
