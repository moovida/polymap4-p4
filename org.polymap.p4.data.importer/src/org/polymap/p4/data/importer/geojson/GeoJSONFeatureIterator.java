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

import java.util.Iterator;
import java.util.NoSuchElementException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.geojson.GeoJSONUtil;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * @author Steffen Stundzig
 */
public class GeoJSONFeatureIterator
        implements FeatureIterator<SimpleFeature>, Iterator<SimpleFeature> {

    private FeatureIterator<SimpleFeature> underlying = null;

    private InputStreamReader              isr        = null;

    private final SimpleFeatureType        schema;

    private final FeatureJSON              featureJSON;

    private final File                     jsonFile;

    private final Charset                  encoding;


    public GeoJSONFeatureIterator( File file, Charset encoding, String schemaName, CoordinateReferenceSystem crs,
            IProgressMonitor monitor ) {
        this.jsonFile = file;
        this.encoding = encoding;
        try {
            featureJSON = new FeatureJSON();
            featureJSON.setEncodeFeatureCRS( false );
            featureJSON.setEncodeNullValues( true );
            isr = new InputStreamReader( new FileInputStream( file ), encoding );
            schema = GeoJSONUtil.parse( new LaxFeatureTypeHandler( schemaName, crs ), isr, false );
            isr.close();
            featureJSON.setFeatureType( schema );

            reset();
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
    }


    public void reset() throws IOException {
        if (isr != null) {
            isr.close();
        }
        isr = new InputStreamReader( new FileInputStream( jsonFile ), encoding );
        underlying = featureJSON.streamFeatureCollection( isr );
    }


    public SimpleFeatureType getFeatureType() {
        return schema;
    }


    @Override
    public void close() {
        if (isr != null) {
            try {
                isr.close();
            }
            catch (IOException e) {
                // do nothing
            }
        }
        if (underlying != null) {
            underlying.close();
        }
    }


    @Override
    public boolean hasNext() {
        return underlying.hasNext();
    }


    @Override
    public SimpleFeature next() throws NoSuchElementException {
        final SimpleFeature next = underlying.next();
        return new SimpleFeatureImpl( next.getAttributes(), schema, null );
    }
}