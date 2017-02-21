/*
 * polymap.org Copyright (C) 2015, the @autors. All rights reserved.
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

import java.util.List;

import java.io.File;

import org.jgrasstools.dbs.spatialite.jgt.SqliteDb;
import org.jgrasstools.gears.io.geopaparazzi.OmsGeopaparazzi4Converter;

import org.polymap.p4.data.importer.ContextIn;
import org.polymap.p4.data.importer.ImporterFactory;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class GeopaparazziImporterFactory
        implements ImporterFactory {

    @ContextIn
    protected File geopapDatabaseFile;


    @Override
    public void createImporters( ImporterBuilder builder ) throws Exception {
        if (geopapDatabaseFile != null) {

            try (SqliteDb geopaparazziDb = new SqliteDb()) {
                geopaparazziDb.open( geopapDatabaseFile.getAbsolutePath() );

                List<String> layerNamesList = OmsGeopaparazzi4Converter.getLayerNamesList( geopaparazziDb.getConnection() );
                for (String layerName : layerNamesList) {
                    builder.newImporter( new GeopaparazziImporter(), layerName , geopapDatabaseFile);
                }

            }
        }
    }

}
