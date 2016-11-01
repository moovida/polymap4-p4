/* 
 * polymap.org
 * Copyright (C) 2016, the @authors. All rights reserved.
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
package org.polymap.p4.data.importer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Joiner;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.polymap.core.runtime.Timer;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;

import org.polymap.rhei.batik.BatikPlugin;
import org.polymap.rhei.batik.toolkit.IPanelToolkit;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class ImporterMonitor
        extends NullProgressMonitor {

    private static final Log log = LogFactory.getLog( ImporterMonitor.class );

    private Label           msg;

    private String          taskName;

    private String          subTaskName;

    private int             total = UNKNOWN;

    private int             worked;
    
    private Timer           updated = new Timer();
    
    private boolean         canceled;
    
    
    public void createContents( Composite parent, IPanelToolkit tk ) {
        log.info( "parent: " + parent );
        
        parent.setLayout( FormLayoutFactory.defaults().create() );
        Label wheel = new Label( parent, SWT.CENTER );
        wheel.setLayoutData( FormDataFactory.filled().top( 0, 50 ).noBottom().create() );
        wheel.setText( "Crunching data..." );
        wheel.setImage( BatikPlugin.images().image( "resources/icons/loading24.gif" ) );

        msg = new Label( parent, SWT.CENTER );
        msg.setLayoutData( FormDataFactory.filled().top( wheel, 10 ).create() );
        update( false );
    }

    protected void update( boolean throttle ) {
        if (throttle && updated.elapsedTime() < 2000) {
            return;
        }
        updated.start();
        UIThreadExecutor.async( () -> {
            if (msg != null && !msg.isDisposed()) {
                String s = Joiner.on( " " ).skipNulls().join( taskName, " ...", subTaskName );
                if (total != UNKNOWN) {
                    double percent = 100d / total * worked;
                    s += " (" + (int)percent + "%)";
                }
                msg.setText( s );
            }
        });
    }
    
    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void beginTask( String name, int totalWork ) {
        this.taskName = name;
        this.total = totalWork;
        update( false );
    }

    @Override
    public void setTaskName( String name ) {
        this.taskName = name;
        update( false );
    }

    @Override
    public void subTask( String name ) {
        this.subTaskName = name;
        update( true );
    }

    @Override
    public void worked( int work ) {
        worked += work;
        update( true );
    }
    
}
