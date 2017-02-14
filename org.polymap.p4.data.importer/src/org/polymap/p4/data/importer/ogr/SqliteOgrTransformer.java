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

import java.io.File;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.p4.data.importer.ImportTempDir;
import org.polymap.p4.data.importer.raster.ExternalProgram;

/**
 * 
 *
 * @author Falko Bräutigam
 */
class SqliteOgrTransformer
        extends ExternalProgram {

    private static final Log log = LogFactory.getLog( SqliteOgrTransformer.class );
    
    /**
     * 
     */
    public static boolean isSupported( File f, IProgressMonitor monitor ) {
        try {
            String[] command = {"ogrinfo", f.getAbsolutePath()};
            return execute( command, monitor, (exitCode, out, err) -> {
                return exitCode == 0 && !err.contains( "ERROR" );
            });
        }
        catch (IOException e) {
            log.warn( e.getLocalizedMessage() );
            return false;
        }
    }

    
    /**
     * 
     * @return A newly created GeoJSON file.
     * @throws IOException 
     */
    public static File translate( File f, IProgressMonitor monitor ) throws IOException {
        monitor.beginTask( "transforming format", IProgressMonitor.UNKNOWN );
        File tempDir = ImportTempDir.create();
        File temp = new File( tempDir, f.getName() + ".sqlite" );

        String[] command = {"ogr2ogr", "-dsco", "spatialite=yes", "-preserve_fid", "-f", "SQLite", 
                temp.getAbsolutePath(), f.getAbsolutePath()};
        
        return execute( command, monitor, (exitCode, out, err) -> {
            monitor.done();
            if (exitCode == 0 && !err.contains( "ERROR" )) {
                return temp;
            }
            else {
                throw new IOException( err );
            }
        });
    }
    
    
//    public static File warp( File f, String epsg, IProgressMonitor monitor ) throws IOException {
//        File tempDir = ImportTempDir.create();
//        File temp = new File( tempDir, getBaseName( f.getName() ) + "." + substringAfterLast( epsg, ":" ) + ".tif" );
//
//        String[] command = {"gdalwarp", "-t_srs", epsg, f.getAbsolutePath(), temp.getAbsolutePath()};
//        return execute( command, monitor, (exitCode, out, err) -> {
//            if (exitCode == 0 && !err.contains( "ERROR" )) {
//                return temp;
//            }
//            else {
//                throw new IOException( err );
//            }
//        });
//    }

}
