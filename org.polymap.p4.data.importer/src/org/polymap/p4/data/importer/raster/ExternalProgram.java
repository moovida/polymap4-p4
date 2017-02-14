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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Throwables;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Executes an external program (gdal/ogr), catches output, checks error and
 * checks/feeds progress monitor.
 *
 * @author Falko Bräutigam
 */
public abstract class ExternalProgram {

    private static final Log log = LogFactory.getLog( ExternalProgram.class );

    @FunctionalInterface
    public interface ResultHandler<R, E extends Exception> {
        /**
         *
         * @param exitCode
         * @param out The standard output of the external program.
         * @param err The standard error of the external program.
         * @throws E
         */
        public R handle( int exitCode, String out, String err ) throws E;
    }
    
    /**
     * Executes the given command.
     * 
     * @param command Command to execute and arguments.
     * @throws IOException If the command could not be executed. The message of the
     *         exception contains the error output of the command.
     * @throws RuntimeException If canceled by monitor.
     */
    public static <R, E extends Exception> 
            R execute( String[] command, IProgressMonitor monitor, ResultHandler<R,E> handler ) 
            throws IOException {
        
        Process process = new ProcessBuilder( command ).start();

        InputStream in = new BufferedInputStream( process.getInputStream() );
        StringBuilder output = new StringBuilder( 4096 );
        InputStream err = new BufferedInputStream( process.getErrorStream() );
        StringBuilder error = new StringBuilder( 4096 );

        do {
            if (monitor.isCanceled()) {
                throw new RuntimeException( "Canceled" );                    
            }
            if (read( err, error ) > 0) {
                //checkError( error );
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
        
        try {
            return handler.handle( process.exitValue(), output.toString(), error.toString() );
        }
        catch (Exception e) {
            Throwables.propagateIfInstanceOf( e, IOException.class );
            throw Throwables.propagate( e );
        }
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
    
}
