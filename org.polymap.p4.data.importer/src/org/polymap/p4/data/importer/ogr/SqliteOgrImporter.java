/* 
 * polymap.org
 * Copyright (C) 2017, the @authors. All rights reserved.
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
package org.polymap.p4.data.importer.ogr;

import java.util.HashMap;
import java.util.Map;

import java.io.File;
import java.io.IOException;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.simple.SimpleFeatureType;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.runtime.SubMonitor;
import org.polymap.core.runtime.i18n.IMessages;

import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.toolkit.IPanelToolkit;
import org.polymap.rhei.table.FeatureCollectionContentProvider;

import org.polymap.p4.data.importer.ContextIn;
import org.polymap.p4.data.importer.ContextOut;
import org.polymap.p4.data.importer.Importer;
import org.polymap.p4.data.importer.ImporterPlugin;
import org.polymap.p4.data.importer.ImporterSite;
import org.polymap.p4.data.importer.Messages;
import org.polymap.p4.data.importer.prompts.SchemaNamePrompt;
import org.polymap.p4.data.importer.shapefile.ShpFeatureTableViewer;

/**
 * Uses SQLite/Spatialite as intermediate format.
 * <p/>
 * TODO<ul>
 * <li>download/load proper libgeos/libproj libreries</li>
 * <li>database version: table 'type' is missing</li>
 * </ul> 
 *
 * @author Falko Bräutigam
 */
public class SqliteOgrImporter
        implements Importer {

    private static final Log log = LogFactory.getLog( SqliteOgrImporter.class );
    
    private static final IMessages i18n = Messages.forPrefix( "ImporterOgr" );

    private ImporterSite        site;

    @ContextIn
    protected File              f;

    @ContextOut
    private FeatureCollection   features;
    
    /** The DataStore of {@link #fs}. */
    private DataStore           ds;
    
    /** The FeatureSource of the {@link #features}. */
    private FeatureSource       fs;

    private Exception           exc;

    private SchemaNamePrompt    schemaNamePrompt;

    
    @Override
    public void init( ImporterSite newSite, IProgressMonitor monitor ) throws Exception {
        this.site = newSite;
        site.icon.set( ImporterPlugin.images().svgImage( "file.svg", SvgImageRegistryHelper.NORMAL24 ) );
        site.summary.set( i18n.get( "summary", f.getName() ) );
        site.description.set( i18n.get( "description") );
        site.terminal.set( true );
    }

    @Override
    public ImporterSite site() {
        return site;
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        schemaNamePrompt = new SchemaNamePrompt( site, FilenameUtils.getBaseName( f.getName() ) );
    }


    @Override
    public void verify( IProgressMonitor monitor ) {
        try {
            // translate to json
            monitor.beginTask( "Verify", 3 );
            File temp = SqliteOgrTransformer.translate( f, new SubMonitor( monitor, 1 ) );
            
            monitor.subTask( "opening temp spatialite database" );            
            System.load( "/home/falko/servers/spatialite-libs/libgeos-3.1.1.so" );
            System.load( "/home/falko/servers/spatialite-libs/libgeos_c.so.1.6.0" );
            System.load( "/home/falko/servers/spatialite-libs/libproj.so.0.5.5" );
            
            Map<String,Object> params = new HashMap();
            params.put( "dbtype" /*SpatiaLiteDataStoreFactory.DBTYPE.key*/, "spatiali te" /*SpatiaLiteDataStoreFactory.DBTYPE.sample*/ );
            params.put( "database" /*SpatiaLiteDataStoreFactory.DATABASE.key*/, temp.getAbsolutePath() );
            ds = DataStoreFinder.getDataStore(params);  //dsf.createDataStore( params );
            log.info( "columns: " + ds.getNames() );
            String name = FilenameUtils.getBaseName( f.getName() );
            fs = ds.getFeatureSource( name );
            monitor.worked( 1 );
            
            // checking geometries
            SubMonitor submon = new SubMonitor( monitor, 1 );
            submon.beginTask( "checking all features", IProgressMonitor.UNKNOWN );
            FeatureCollection results = fs.getFeatures();
            try (
                FeatureIterator it = results.features();
            ){
                while (it.hasNext()) {
                    Feature feature = it.next();
                    // geometry
                    GeometryAttribute geom = feature.getDefaultGeometryProperty();
                    if (geom == null || geom.getValue() == null) {
                        throw new RuntimeException( "Feature has no geometry: " + feature.getIdentifier().getID() );
                    }
                    // other checks...?
                    monitor.worked( 1 );
                }
            }

            site.ok.set( true );
            exc = null;
        }
        catch (IOException e) {
            site.ok.set( false );
            exc = e;
        }
    }


    @Override
    public void createResultViewer( Composite parent, IPanelToolkit tk ) {
        if (exc != null) {
            tk.createFlowText( parent, "\nUnable to read the data.\n\n**Reason**: " + exc.getMessage() );
        }
        else {
            try {
                SimpleFeatureType schema = (SimpleFeatureType)fs.getSchema();
                ShpFeatureTableViewer table = new ShpFeatureTableViewer( parent, schema );
                table.setContentProvider( new FeatureCollectionContentProvider() );
                
//                // XXX GeoTools shapefile impl does not handle setFirstResult() well
//                // so we can just display 100 features :(
//                Query query = new Query();
//                query.setMaxFeatures( 1000 );
                FeatureCollection content = fs.getFeatures();
                table.setInput( content );
            }
            catch (Exception e) {
                log.info( "", e );
                exc = e;
                site.ok.set( false );
                tk.createFlowText( parent, "\nUnable to read the data.\n\n**Reason**: " + exc.getMessage() );
            }
        }
    }


    @Override
    public void execute( IProgressMonitor monitor ) throws Exception {
        FeatureCollection result = fs.getFeatures();
        features = schemaNamePrompt.retypeFeatures( (SimpleFeatureCollection)result, f.getName() );
    }
    
}
