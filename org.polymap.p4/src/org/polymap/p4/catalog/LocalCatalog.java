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

import static org.polymap.core.CorePlugin.getDataLocation;

import java.io.File;

import org.geotools.data.DataAccess;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Sets;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.polymap.core.CorePlugin;
import org.polymap.core.catalog.IMetadata;
import org.polymap.core.catalog.local.LocalMetadataCatalog;
import org.polymap.core.catalog.resolve.IResolvableInfo;
import org.polymap.core.data.rs.catalog.RServiceResolver;
import org.polymap.core.data.wms.catalog.WmsServiceResolver;

import org.polymap.rhei.fulltext.store.lucene.LuceneFulltextIndex;

import org.polymap.model2.store.recordstore.RecordStoreAdapter;
import org.polymap.p4.P4Plugin;
import org.polymap.recordstore.lucene.LuceneRecordStore;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class LocalCatalog
        extends LocalMetadataCatalog {

    private static Log log = LogFactory.getLog( LocalCatalog.class );

    private static final File       LOCAL_FEATURES_STORE_DIR = new File( CorePlugin.getDataLocation( P4Plugin.instance() ), "features" );

    public static final String      LOCAL_FEATURES_STORE_ID = "_local_features_store_";

    public static final String      WORLD_BACKGROUND_ID = "_world_background_";

    private LuceneRecordStore       store;

    private LuceneFulltextIndex     index;
    
    
    public LocalCatalog() throws Exception {
        File dataDir = getDataLocation( P4Plugin.instance() );
        store = new LuceneRecordStore( new File( dataDir, "localCatalog" ), false );
        index = new LuceneFulltextIndex( new File( dataDir, "localCatalogIndex" ) );
        
        init( new RecordStoreAdapter( store ), index );
        
        createEntries();
    }

    
    @Override
    public String getTitle() {
        return "Project's data sources";
    }


    @Override
    public String getDescription() {
        return "Data sources of this local project";
    }


    /**
     * Returns the one and only 'local' store for features in this P4 instance.
     */
    public IMetadata localFeaturesStoreEntry() {
        try {
            return entry( LOCAL_FEATURES_STORE_ID, new NullProgressMonitor() ).get();
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
    }
    
    
    /**
     * Returns the one and only 'local' store for features in this P4 instance.
     */
    public IResolvableInfo localFeaturesStoreInfo() {
        try {
            IMetadata metadata = localFeaturesStoreEntry();
            return P4Plugin.allResolver().resolve( metadata, new NullProgressMonitor() );
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
    }
    
    
    /**
     * Returns the one and only 'local' store for features in this P4 instance.
     */
    public DataAccess localFeaturesStore() {
        try {
            IResolvableInfo info = localFeaturesStoreInfo();
            return info.getServiceInfo().createService( new NullProgressMonitor() );
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
    }
    
    
    protected void createEntries() throws Exception {
        // check empty
        if (query( ALL_QUERY, new NullProgressMonitor() ).execute().size() == 0) {
            // create standard entries
            try (Updater update = prepareUpdate()) {
                LOCAL_FEATURES_STORE_DIR.mkdirs();
                update.newEntry( metadata -> {
                    metadata.setIdentifier( LOCAL_FEATURES_STORE_ID );
                    metadata.setTitle( "Datastore" );
                    metadata.setDescription( "The data store of this project" );
                    metadata.setType( "Database" );
                    metadata.setConnectionParams( RServiceResolver.createParams( LOCAL_FEATURES_STORE_DIR ) );
                });
                update.newEntry( metadata -> {
                    metadata.setIdentifier( WORLD_BACKGROUND_ID );
                    metadata.setTitle( "World" );
                    metadata.setDescription( "Default background and demo layers from mapzone.io." );
                    metadata.setType( "Service" );
                    metadata.setFormats( Sets.newHashSet( "WMS", "WFS" ) );
                    metadata.setConnectionParams( WmsServiceResolver.createParams( "http://mapzone.io/projects/falko/World/ows/n92ln3he1b536m3ir8rds98ohn/ows?SERVICE=WMS" ) );
                });
//                update.newEntry( metadata -> {
//                    metadata.setTitle( "Schutzgebiete Mittelsachsen" );
//                    metadata.setDescription( "-test entry-" );
//                    metadata.setConnectionParams( WmsServiceResolver.createParams( "http://www.mittelsachsen-atlas.de/polymap-atlas/services/INSPIRE/Schutzgebiete" ) );
//                });
                update.commit();
            }
        }
    }


    /**
     * 
     */
    public void deleteEntry( String identifier ) throws Exception {
        try (Updater update = prepareUpdate()) {
            update.removeEntry( identifier );
            update.commit();
        }
    }
    
}
