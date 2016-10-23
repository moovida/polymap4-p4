/*
 * polymap.org 
 * Copyright (C) 2015, Falko Bräutigam. All rights reserved.
 * 
 * This is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 3.0 of the License, or (at your option) any later
 * version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 */
package org.polymap.p4.data.importer.shapefile;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.apache.commons.io.FilenameUtils.getExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;

import org.geotools.data.Query;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.ContentFeatureCollection;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CRSFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.runtime.Streams;
import org.polymap.core.runtime.Streams.ExceptionCollector;
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
import org.polymap.p4.data.importer.prompts.CharsetPrompt;
import org.polymap.p4.data.importer.prompts.CrsPrompt;
import org.polymap.p4.data.importer.prompts.SchemaNamePrompt;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class ShpImporter
        implements Importer {

    private static final Log log = LogFactory.getLog( ShpImporter.class );

    private static final IMessages i18nPrompt = Messages.forPrefix( "ImporterPrompt" );

    private static final IMessages i18n = Messages.forPrefix( "ImporterShp" );

    private static final ShapefileDataStoreFactory dsFactory  = new ShapefileDataStoreFactory();

    private ImporterSite                           site;

    @ContextIn
    protected List<File>                           files;

    @ContextIn
    protected File                                 shp;

    @ContextOut
    private FeatureCollection                      features;

    private Exception                              exception;

    private ShapefileDataStore                     ds;

    private ContentFeatureSource                   fs;

    private CharsetPrompt                          charsetPrompt;

    private CrsPrompt                              crsPrompt;

    private SchemaNamePrompt                       schemaNamePrompt;


    @Override
    public ImporterSite site() {
        return site;
    }


    @Override
    @SuppressWarnings( "hiding" )
    public void init( ImporterSite site, IProgressMonitor monitor ) {
        this.site = site;
        site.icon.set( ImporterPlugin.images().svgImage( "shp.svg", SvgImageRegistryHelper.NORMAL24 ) );
        site.summary.set( i18n.get( "summary", shp.getName() ) );
        site.description.set( i18n.get( "description") );
        site.terminal.set( true );
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        charsetPrompt = new CharsetPrompt( site, i18nPrompt.get("encodingSummary"), i18nPrompt.get( "encodingDescription" ), () -> {
            Charset crs = null;
            try (ExceptionCollector<RuntimeException> exc = Streams.exceptions()) {
                crs = Charset.forName( files.stream()
                        .filter( f -> "cpg".equalsIgnoreCase( getExtension( f.getName() ) ) ).findAny()
                        .map( f -> exc.check( () -> readFileToString( f ).trim() ) )
                        .orElse( CharsetPrompt.DEFAULT.name() ) );
            }
            return crs;
        });
        
        crsPrompt = new CrsPrompt( site, defaultCrs() );
        
        schemaNamePrompt = new SchemaNamePrompt( site, getBaseName( shp.getName() ) );
    }

    
    protected CoordinateReferenceSystem defaultCrs() {
        return files.stream()
                .filter( f -> "prj".equalsIgnoreCase( getExtension( f.getName() ) ) )
                .findAny()
                .map( prjFile -> {
                    try {
                        // encoding used in geotools' PrjFileReader
                        String wkt = FileUtils.readFileToString( prjFile, Charset.forName( "ISO-8859-1" ) );
                        CRSFactory factory = ReferencingFactoryFinder.getCRSFactory( null );
                        CoordinateReferenceSystem result = factory.createFromWKT( wkt );
                        return result;
                    }
                    catch (Exception e) {
                        log.warn( "", e );
                        return null;
                    }
                })
                .orElse( null );
    }

    
    @Override
    public void verify( IProgressMonitor monitor ) {
        try {
            if (ds != null) {
                ds.dispose();
            }
            Map<String,Serializable> params = new HashMap<String,Serializable>();
            params.put( ShapefileDataStoreFactory.URLP.key, shp.toURI().toURL() );
            params.put( ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.TRUE );

            ds = (ShapefileDataStore)dsFactory.createNewDataStore( params );
            ds.setCharset( charsetPrompt.selection() );
            ds.forceSchemaCRS( crsPrompt.selection() );
            
            fs = ds.getFeatureSource();
            
            // check all features
            try (
                SimpleFeatureIterator it = fs.getFeatures().features();
            ){
                SimpleFeatureType schema = fs.getSchema();
                while (it.hasNext()) {
                    SimpleFeature feature = it.next();
                    // geometry
                    if (schema.getGeometryDescriptor() != null
                            && feature.getDefaultGeometry() == null) {
                        throw new RuntimeException( "Feature has no geometry: " + feature.getIdentifier().getID() );
                    }
                    // other checks...?
                }
            }

            site.ok.set( true );
            exception = null;
        }
        catch (Exception e) {
            site.ok.set( false );
            exception = e;
        }
    }


    @Override
    public void createResultViewer( Composite parent, IPanelToolkit tk ) {
        if (exception != null) {
            tk.createFlowText( parent, "\nUnable to read the data.\n\n**Reason**: " + exception.getMessage() );
        }
        else {
            try {
                SimpleFeatureType schema = (SimpleFeatureType)fs.getSchema();
                //log.info( "Features: " + features.size() + " : " + schema.getTypeName() );
                // tk.createFlowText( parent, "Features: *" + features.size() + "*" );
                
                ShpFeatureTableViewer table = new ShpFeatureTableViewer( parent, schema );
                table.setContentProvider( new FeatureCollectionContentProvider() );
                
                // XXX GeoTools shapefile impl does not handle setFirstResult() well
                // so we can just display 100 features :(
                Query query = new Query();
                query.setMaxFeatures( 1000 );
                ContentFeatureCollection content = fs.getFeatures( query );
                table.setInput( content );
            }
            catch (IOException e) {
                tk.createFlowText( parent, "\nUnable to read the data.\n\n**Reason**: " + exception.getMessage() );
                site.ok.set( false );
                exception = e;
            }
        }
    }


    @Override
    public void execute( IProgressMonitor monitor ) throws Exception {
        // no maxResults restriction
        ContentFeatureCollection result = fs.getFeatures();
        
        features = schemaNamePrompt.retypeFeatures( result, shp.getName() );
    }

}
