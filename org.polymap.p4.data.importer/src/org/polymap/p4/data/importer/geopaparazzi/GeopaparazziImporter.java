/*
 * polymap.org Copyright (C) 2015, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.data.importer.geopaparazzi;

import java.util.HashMap;
import java.util.List;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.jgrasstools.dbs.compat.IJGTConnection;
import org.jgrasstools.dbs.spatialite.jgt.SqliteDb;
import org.jgrasstools.gears.io.geopaparazzi.OmsGeopaparazzi4Converter;
import org.jgrasstools.gears.io.geopaparazzi.geopap4.DaoGpsLog.GpsLog;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IProgressMonitor;

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
import org.polymap.p4.data.importer.prompts.CrsPrompt;
import org.polymap.p4.data.importer.prompts.SchemaNamePrompt;

/**
 * 
 *
 * @author hydrologis
 */
public class GeopaparazziImporter
        implements Importer {

    private static final Log                       log        = LogFactory.getLog( GeopaparazziImporter.class );

    private static final IMessages                 i18nPrompt = Messages.forPrefix( "ImporterPrompt" );

    private static final IMessages                 i18n       = Messages.forPrefix( "ImporterGpap" );

    private static final ShapefileDataStoreFactory dsFactory  = new ShapefileDataStoreFactory();

    private ImporterSite                           site;

    @ContextIn
    protected File                                 geopapDatabaseFile;

    @ContextIn
    protected String                               layerName;

    @ContextOut
    private FeatureCollection                      features;

    private Exception                              exception;

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
        site.icon.set( ImporterPlugin.images().svgImage( "gpap.svg", SvgImageRegistryHelper.NORMAL24 ) );
        site.summary.set( i18n.get( "summary", layerName ) );
        site.description.set( i18n.get( "description" ) );
        site.terminal.set( true );
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        // charsetPrompt = new CharsetPrompt( site, i18nPrompt.get( "encodingSummary"
        // ), i18nPrompt.get( "encodingDescription" ), () -> {
        // Charset crs = null;
        // try (ExceptionCollector<RuntimeException> exc = Streams.exceptions()) {
        // crs = Charset.forName( files.stream().filter( f -> "cpg".equalsIgnoreCase(
        // getExtension( f.getName() ) ) ).findAny().map( f -> exc.check( () ->
        // readFileToString( f ).trim() ) ).orElse( CharsetPrompt.DEFAULT.name() ) );
        // }
        // return crs;
        // } );

        crsPrompt = new CrsPrompt( site, defaultCrs() );

        schemaNamePrompt = new SchemaNamePrompt( site, layerName );
    }


    protected CoordinateReferenceSystem defaultCrs() {
        return DefaultGeographicCRS.WGS84;
    }


    @Override
    public void verify( IProgressMonitor monitor ) {
        try {
            try (SqliteDb db = new SqliteDb()) {
                db.open( geopapDatabaseFile.getAbsolutePath() );
                IJGTConnection connection = db.getConnection();

                GPProgressMonitor pm = new GPProgressMonitor( monitor );
                switch (layerName) {
                    case OmsGeopaparazzi4Converter.SIMPLE_NOTES:
                        features = OmsGeopaparazzi4Converter.simpleNotes2featurecollection( connection, pm );
                        break;
                    case OmsGeopaparazzi4Converter.GPS_LOGS:
                        List<GpsLog> gpsLogsList = OmsGeopaparazzi4Converter.getGpsLogsList( connection );
                        features = OmsGeopaparazzi4Converter.getLogLinesFeatureCollection( pm, gpsLogsList );
                        break;
                    case OmsGeopaparazzi4Converter.MEDIA_NOTES:
                        features = OmsGeopaparazzi4Converter.media2IdBasedFeatureCollection( connection, pm );
                        break;
                    default:
                        HashMap<String,SimpleFeatureCollection> complexNotesMap = OmsGeopaparazzi4Converter.complexNotes2featurecollections( connection, pm );
                        features = complexNotesMap.get( layerName );
                        break;
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
                SimpleFeatureType schema = (SimpleFeatureType)features.getSchema();
                // log.info( "Features: " + features.size() + " : " +
                // schema.getTypeName() );
                // tk.createFlowText( parent, "Features: *" + features.size() + "*"
                // );

                GPFeatureTableViewer table = new GPFeatureTableViewer( parent, schema );
                table.setContentProvider( new FeatureCollectionContentProvider() );

                // XXX GeoTools shapefile impl does not handle setFirstResult() well
                // so we can just display 100 features :(
                // Query query = new Query();
                // query.setMaxFeatures( 1000 );
                // ContentFeatureCollection content = features.subCollection( query
                // );

                DefaultFeatureCollection fc = new DefaultFeatureCollection();
                int count = 0;
                FeatureIterator featureIterator = features.features();
                while (featureIterator.hasNext()) {
                    SimpleFeature feature = (SimpleFeature)featureIterator.next();
                    fc.add( feature );
                    if (count > 1000)
                        break;
                }

                table.setInput( fc );
            }
            catch (Exception e) {
                tk.createFlowText( parent, "\nUnable to read the data.\n\n**Reason**: " + exception.getMessage() );
                site.ok.set( false );
                exception = e;
            }
        }
    }


    @Override
    public void execute( IProgressMonitor monitor ) throws Exception {
        // no maxResults restriction

        features = schemaNamePrompt.retypeFeatures( (SimpleFeatureCollection)features, layerName );
    }

}
