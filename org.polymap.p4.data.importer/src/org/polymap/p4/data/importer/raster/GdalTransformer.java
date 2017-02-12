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
package org.polymap.p4.data.importer.raster;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;

import java.util.Arrays;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.polymap.p4.data.importer.ImportTempDir;

/**
 * 
 *
 * @author Falko Bräutigam
 */
class GdalTransformer {

    private static final Log log = LogFactory.getLog( GdalTransformer.class );
    
    /**
     * 
     */
    public static boolean isSupported( File f, IProgressMonitor monitor ) {
        try {
            Process process = new ProcessBuilder( Arrays.asList( "gdalinfo", f.getAbsolutePath() ) ).start();
            process.waitFor();  // XXX job in order to support cancel?
            log.debug( "exit value: " + process.exitValue() );
            String error = IOUtils.toString( new BufferedInputStream( process.getErrorStream() ) );
            log.debug( error );
            String output = IOUtils.toString( new BufferedInputStream( process.getInputStream() ) );
            log.debug( output );
            return process.exitValue() == 0 && !error.contains( "ERROR" );
        }
        catch (IOException | InterruptedException e) {
            log.warn( e.getLocalizedMessage() );
            return false;
        }
    }

    
    /**
     * 
     * @return A newly created File.
     * @throws IOException 
     */
    public static File translate( File f, IProgressMonitor monitor ) throws IOException {
        File tempDir = ImportTempDir.create();
        File temp = new File( tempDir, f.getName() + ".tif" );

        Process process = new ProcessBuilder( Arrays.asList( "gdal_translate", 
                f.getAbsolutePath(), temp.getAbsolutePath() ) )
                .start();

        InputStream in = new BufferedInputStream( process.getInputStream() );
        StringBuilder output = new StringBuilder( 4096 );
        InputStream err = new BufferedInputStream( process.getErrorStream() );
        StringBuilder error = new StringBuilder( 4096 );

        do {
            if (monitor.isCanceled()) {
                throw new RuntimeException( "Canceled" );                    
            }
            if (read( err, error ) > 0) {
                if (error.toString().contains( "ERROR" )) {
                    throw new RuntimeException( error.toString() );
                }
            }
            if (read( in, output ) > 0) {
                monitor.worked( 1 );
            }
            try {
                Thread.sleep( 100 );
            }
            catch (InterruptedException e) {
                // continue
            }
        } while (isRunning( process ));
        return temp;
    }
    
    
    public static File warp( File f, String epsg, IProgressMonitor monitor ) throws IOException {
        File tempDir = ImportTempDir.create();
        File temp = new File( tempDir, getBaseName( f.getName() ) + "." + substringAfterLast( epsg, ":" ) + ".tif" );

        Process process = new ProcessBuilder( Arrays.asList( "gdalwarp",
                "-t_srs", epsg,
                f.getAbsolutePath(), temp.getAbsolutePath() ) )
                .start();

        InputStream in = new BufferedInputStream( process.getInputStream() );
        StringBuilder output = new StringBuilder( 4096 );
        InputStream err = new BufferedInputStream( process.getErrorStream() );
        StringBuilder error = new StringBuilder( 4096 );

        do {
            if (monitor.isCanceled()) {
                throw new RuntimeException( "Canceled" );                    
            }
            if (read( err, error ) > 0) {
                if (error.toString().contains( "ERROR" )) {
                    throw new RuntimeException( error.toString() );
                }
            }
            if (read( in, output ) > 0) {
                monitor.worked( 1 );
            }
            try {
                Thread.sleep( 100 );
            }
            catch (InterruptedException e) {
                // continue
            }
        } while (isRunning( process ));
        return temp;
    }


    protected static int read( InputStream in, StringBuilder out ) throws IOException {
        byte[] bytes = new byte[ in.available() ];
        int result = in.read( bytes );
        out.append( new String( bytes ) );
        return result;
    }
    
    
    protected static boolean isRunning( Process process ) {
        try {
            process.exitValue();
            return false;
        }
        catch (IllegalThreadStateException e) {
            return true;
        }
    }
    
    // Test ***********************************************
    
    public static final void main( String[] args ) {
        File f = new File( "/home/falko/Data/ncrast/elevation.grd" );
        log.info( "isSupported(): " + isSupported( f, new NullProgressMonitor() ) );
    }
    
}
