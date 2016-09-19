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
package org.polymap.p4.data.importer.kml;

import java.util.Collection;

import java.io.File;
import java.io.IOException;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
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
import org.polymap.p4.data.importer.prompts.SchemaNamePrompt;
import org.polymap.p4.data.importer.shapefile.ShpFeatureTableViewer;

/**
 * @author Steffen Stundzig
 */
public class KMLImporter
        implements Importer {

    private static final IMessages i18n = Messages.forPrefix( "ImporterKML" );

    private static final IMessages i18nPrompt = Messages.forPrefix( "ImporterPrompt" );

    private ImporterSite           site;

    @ContextIn
    protected File                 kmlFile;

    @ContextOut
    private FeatureCollection      features;

    private Exception              exception;

    private SchemaNamePrompt       schemaNamePrompt;


    @Override
    public void init( ImporterSite importerSite, IProgressMonitor monitor ) throws Exception {
        this.site = importerSite;
        importerSite.icon.set( ImporterPlugin.images().svgImage( "file-xml.svg", SvgImageRegistryHelper.NORMAL24 ) );
        importerSite.summary.set( i18n.get( "summary", kmlFile.getName() ) );
        importerSite.description.set( i18n.get( "description" ) );
        importerSite.terminal.set( true );
    }


    @Override
    public ImporterSite site() {
        return site;
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        schemaNamePrompt = new SchemaNamePrompt( site, i18nPrompt.get("schemaSummary"), i18nPrompt.get( "schemaDescription" ), () -> {
            return FilenameUtils.getBaseName( kmlFile.getName() );
        } );
    }


    @Override
    public void verify( IProgressMonitor monitor ) {
        // System.err.println( "verify " + System.currentTimeMillis() );
        KMLFeatureIterator featureIterator = null;
        try {
            featureIterator = new KMLFeatureIterator( kmlFile, schemaNamePrompt.selection() );
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
        }
        finally {
            if (featureIterator != null) {
                featureIterator.close();
            }
        }
        // System.err.println( "verify done " + System.currentTimeMillis() );
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
        final KMLFeatureIterator featureIterator = new KMLFeatureIterator( kmlFile, schemaNamePrompt.selection() );
        final SimpleFeatureType schema = featureIterator.getFeatureType();
        features = new FeatureCollection<SimpleFeatureType,SimpleFeature>() {

            @Override
            public FeatureIterator<SimpleFeature> features() {
                try {
                    return featureIterator;
                }
                catch (Exception e) {
                    throw new RuntimeException( e );
                }
            }


            @Override
            public SimpleFeatureType getSchema() {
                return schema;
            }


            @Override
            public String getID() {
                return null;
            }


            @Override
            public void accepts( FeatureVisitor visitor, ProgressListener progress ) throws IOException {
                FeatureIterator<SimpleFeature> iterator = features();
                while (iterator.hasNext()) {
                    visitor.visit( iterator.next() );
                }
            }


            @Override
            public FeatureCollection<SimpleFeatureType,SimpleFeature> subCollection( Filter filter ) {
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public FeatureCollection<SimpleFeatureType,SimpleFeature> sort( SortBy order ) {
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public ReferencedEnvelope getBounds() {
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public boolean contains( Object o ) {
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public boolean containsAll( Collection o ) {
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public boolean isEmpty() {
                return false;
            }


            @Override
            public int size() {
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public Object[] toArray() {
                throw new RuntimeException( "not yet implemented." );
            }


            @Override
            public Object[] toArray( Object[] a ) {
                throw new RuntimeException( "not yet implemented." );
            }

        };
    }
}