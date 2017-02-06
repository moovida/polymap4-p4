/*
 * polymap.org Copyright (C) 2016, the @authors. All rights reserved.
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
package org.polymap.p4.data.importer.geojson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;

import org.geotools.referencing.CRS;
import org.junit.Test;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;

import org.eclipse.core.runtime.NullProgressMonitor;

public class GeoJSONFeatureIteratorTest {

    @Test
    @SuppressWarnings( "resource" )
    public void wochenmaerkte() throws Exception {
        File file = new File( this.getClass().getResource( "playgrounds_kidsle_kb2.geojson" ).getFile() );
        GeoJSONFeatureIterator iterator = new GeoJSONFeatureIterator( file, Charset.forName( "utf-8" ), "test", CRS.decode( "EPSG:4326" ), new NullProgressMonitor() );
        SimpleFeatureType featureType = iterator.getFeatureType();
        assertEquals( CRS.decode( "EPSG:4326"), featureType.getCoordinateReferenceSystem());
        assertTrue( iterator.hasNext() );
        Feature f1 = iterator.next();
        assertEquals("Leipzig", f1.getProperty( "town").getValue());
    }
}
