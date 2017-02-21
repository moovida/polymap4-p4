/*
 * polymap.org Copyright (C) 2017, the @authors. All rights reserved.
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

import org.jgrasstools.gears.libs.monitor.IJGTProgressMonitor;

import org.eclipse.core.runtime.IProgressMonitor;

public class GPProgressMonitor
        implements IJGTProgressMonitor, IProgressMonitor {

    private IProgressMonitor pm;


    public GPProgressMonitor( IProgressMonitor pm ) {
        this.pm = pm;
    }


    @Override
    public void beginTask( String name, int totalWork ) {
        pm.beginTask( name, totalWork );
    }


    @Override
    public void done() {
        pm.done();
    }


    @Override
    public void internalWorked( double work ) {
        pm.internalWorked( work );
    }


    @Override
    public boolean isCanceled() {
        return pm.isCanceled();
    }


    @Override
    public void setCanceled( boolean value ) {
        pm.setCanceled( value );
    }


    @Override
    public void setTaskName( String name ) {
        pm.setTaskName( name );
    }


    @Override
    public void subTask( String name ) {
        pm.subTask( name );
    }


    @Override
    public void worked( int work ) {
        pm.worked( work );
    }


    @Override
    public <T> T adapt( Class<T> arg0 ) {
        throw new RuntimeException( "not yet implemented." );
    }


    @Override
    public void errorMessage( String arg0 ) {
    }


    @Override
    public void exceptionThrown( String arg0 ) {
    }


    @Override
    public void message( String arg0 ) {
    }


    @Override
    public void onModuleExit() {
    }

}
