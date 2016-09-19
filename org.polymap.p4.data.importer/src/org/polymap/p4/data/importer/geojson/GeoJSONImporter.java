/*
 * polymap.org Copyright (C) 2015 individual contributors as indicated by the
 * 
 * @authors tag. All rights reserved.
 * 
 * This is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 */
package org.polymap.p4.data.importer.geojson;

import java.util.Collection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.util.ProgressListener;

import org.apache.commons.io.FilenameUtils;

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
import org.polymap.p4.data.importer.prompts.CharsetPrompt;
import org.polymap.p4.data.importer.prompts.CrsPrompt;
import org.polymap.p4.data.importer.prompts.SchemaNamePrompt;
import org.polymap.p4.data.importer.shapefile.ShpFeatureTableViewer;

/**
 * @author Joerg Reichert <joerg@mapzone.io>
 * @author Steffen Stundzig
 */
public class GeoJSONImporter
        implements Importer {

    private static final IMessages i18n = Messages.forPrefix( "ImporterGeoJSON" );

    private static final IMessages i18nPrompt = Messages.forPrefix( "ImporterPrompt" );

    protected ImporterSite         site;

    @ContextIn
    protected File                 geojsonFile;

    @ContextOut
    protected FeatureCollection    features;

    private Exception              exception;

    private CrsPrompt              crsPrompt;

    private CharsetPrompt          charsetPrompt;

    private SchemaNamePrompt       schemaNamePrompt;

    private GeoJSONFeatureIterator featureIterator;


    @Override
    public void init( ImporterSite newSite, IProgressMonitor monitor ) throws Exception {
        this.site = newSite;
        site.icon.set( ImporterPlugin.images().svgImage( "json.svg", SvgImageRegistryHelper.NORMAL24 ) );
        site.summary.set( i18n.get( "summary", geojsonFile.getName() ) );
        site.description.set( i18n.get( "description" ) );
        site.terminal.set( true );
    }


    @Override
    public ImporterSite site() {
        return site;
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        // charset prompt
        charsetPrompt = new CharsetPrompt( site, i18nPrompt.get("encodingSummary"), i18nPrompt.get( "encodingDescription" ), () -> {
            return Charset.forName( "UTF-8" );
        } );
        // http://geojson.org/geojson-spec.html#coordinate-reference-system-objects
        crsPrompt = new CrsPrompt( site, i18nPrompt.get("crsSummary"), i18nPrompt.get( "crsDescription" ), () -> {
                    return getPredefinedCRS();
                } );
        schemaNamePrompt = new SchemaNamePrompt( site, i18nPrompt.get("schemaSummary"), i18nPrompt.get( "schemaDescription" ), () -> {
            return FilenameUtils.getBaseName( geojsonFile.getName() );
        } );
    }


    @Override
    public void verify( IProgressMonitor monitor ) {
        if (featureIterator != null) {
            featureIterator.close();
        }
        featureIterator = new GeoJSONFeatureIterator( geojsonFile, charsetPrompt.selection(), schemaNamePrompt.selection(), crsPrompt.selection(), monitor );
        try {

            ListFeatureCollection featureList = new ListFeatureCollection( featureIterator.getFeatureType() );
            int i = 0;
            while (i < 100 && featureIterator.hasNext()) {
                SimpleFeature next = featureIterator.next();
                featureList.add( next );
                i++;
            }
            features = featureList;
            site.ok.set( true );
            exception = null;
        }
        catch (Exception e) {
            site.ok.set( false );
            exception = e;
            e.printStackTrace();
        }
        finally {
            if (featureIterator != null) {
                featureIterator.close();
            }
        }
    }


    CoordinateReferenceSystem getPredefinedCRS() {
        CoordinateReferenceSystem predefinedCRS = null;
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader( new FileInputStream( geojsonFile ), CharsetPrompt.DEFAULT );
            FeatureJSON featureJSON = new FeatureJSON();
            predefinedCRS = featureJSON.readCRS( isr );
        }
        catch (Exception ioe) {
            exception = ioe;
        }
        finally {
            if (isr != null) {
                try {
                    isr.close();
                }
                catch (IOException e) {
                    // do nothing
                }
            }
        }
        if (predefinedCRS == null) {
            try {
                predefinedCRS = CRS.decode( "EPSG:4326" );
            }
            catch (Exception e) {
                // do nothing
            }
        }
        return predefinedCRS;
    }


    @Override
    public void createResultViewer( Composite parent, IPanelToolkit toolkit ) {
        if (exception != null) {
            toolkit.createFlowText( parent, "\nUnable to read the data.\n\n" + "**Reason**: "
                    + exception.getMessage() );
        }
        else {
            SimpleFeatureType schema = (SimpleFeatureType)features.getSchema();
            ShpFeatureTableViewer table = new ShpFeatureTableViewer( parent, schema );
            table.setContentProvider( new FeatureCollectionContentProvider() );
            table.setInput( features );
        }
    }


    @Override
    public void execute( IProgressMonitor monitor ) throws Exception {
        // must be created in verify before
        featureIterator.reset();
        features = new FeatureCollection() {

            @Override
            public FeatureIterator features() {
                return featureIterator;
            }


            @Override
            public FeatureType getSchema() {
                return featureIterator.getFeatureType();
            }


            @Override
            public String getID() {
                return null;
            }


            @Override
            public void accepts( FeatureVisitor visitor, ProgressListener progress ) throws IOException {
                FeatureIterator iterator = features();
                while (iterator.hasNext()) {
                    visitor.visit( iterator.next() );
                }
            }


            @Override
            public FeatureCollection subCollection( Filter filter ) {
                // TODO Auto-generated method stub
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public FeatureCollection sort( SortBy order ) {
                // TODO Auto-generated method stub
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public ReferencedEnvelope getBounds() {
                // TODO Auto-generated method stub
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public boolean contains( Object o ) {
                // TODO Auto-generated method stub
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public boolean containsAll( Collection o ) {
                // TODO Auto-generated method stub
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public boolean isEmpty() {
                return false;
            }


            @Override
            public int size() {
                // TODO Auto-generated method stub
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public Object[] toArray() {
                // TODO Auto-generated method stub
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public Object[] toArray( Object[] a ) {
                // TODO Auto-generated method stub
                throw new RuntimeException( "not yet implemented." );
            }

        };
    }
}