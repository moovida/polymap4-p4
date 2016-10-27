/*
 * polymap.org 
 * Copyright (C) 2016, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.data.importer.download;

import static org.polymap.core.ui.FormDataFactory.on;

import java.util.regex.Pattern;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.runtime.Timer;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.toolkit.IPanelToolkit;

import org.polymap.p4.data.importer.ContextOut;
import org.polymap.p4.data.importer.ImportTempDir;
import org.polymap.p4.data.importer.Importer;
import org.polymap.p4.data.importer.ImporterPlugin;
import org.polymap.p4.data.importer.ImporterPrompt;
import org.polymap.p4.data.importer.ImporterPrompt.PromptUIBuilder;
import org.polymap.p4.data.importer.ImporterPrompt.Severity;
import org.polymap.p4.data.importer.ImporterSite;
import org.polymap.p4.data.importer.Messages;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class DownloadImporter
        implements Importer {

    private static final Log log = LogFactory.getLog( DownloadImporter.class );

    private static final IMessages i18n = Messages.forPrefix( "Download" );

    public static final Pattern    URL_PATTERN = Pattern.compile( "(((ftp)|(https?)):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)" );

    @ContextOut
    protected File                 downloaded;
    
    private ImporterSite           site;

    private Exception              exception;

    private ImporterPrompt         urlPrompt;

    private URL                    url;

    private UIJob                  downloader;


    @Override
    public ImporterSite site() {
        return site;
    }


    @Override
    public void init( @SuppressWarnings( "hiding" ) ImporterSite site, IProgressMonitor monitor ) {
        this.site = site;
        site.icon.set( ImporterPlugin.images().svgImage( "file-import.svg", SvgImageRegistryHelper.NORMAL24 ) );
        site.summary.set( i18n.get( "summary" ) );
        site.description.set( i18n.get( "description" ) );
        site.terminal.set( false );
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        // start without alert icon and un-expanded
        urlPrompt = site.newPrompt( "url" )
                .summary.put( "URL" )
                .value.put( "<Click to specify>" )
                .description.put( "The URL to download file from" )
                .severity.put( Severity.INFO )
                .extendedUI.put( new PromptUIBuilder() {
                    @Override
                    public void createContents( ImporterPrompt prompt, Composite parent, IPanelToolkit tk ) {
                        parent.setLayout( FormLayoutFactory.defaults().spacing( 3 ).create() );
                        Text text = on( tk.createText( parent, 
                                url != null ? url.toString() : "", 
                                SWT.BORDER | SWT.WRAP | SWT.MULTI ) )
                                .top( 0 ).left( 0 ).right( 100 ).width( DEFAULT_WIDTH ).height( 40 ).control();
                        text.forceFocus();

                        final Label msg = on( tk.createLabel( parent, "" ) ).fill().top( text ).control();

                        text.addModifyListener( new ModifyListener() {
                            @Override
                            public void modifyText( ModifyEvent ev ) {
                                String value = text.getText();
                                url = null;
                                if (URL_PATTERN.matcher( value ).matches()) {
                                    try {
                                        url = new URL( value );
                                        msg.setText( "Ok" );
                                        return;
                                    }
                                    catch (MalformedURLException e) {
                                    }
                                }
                                msg.setText( "Not a valid URL" );
                            }
                        });
                    }

                    @Override
                    public void submit( ImporterPrompt prompt ) {
                        urlPrompt.severity.set( Severity.REQUIRED );
                        urlPrompt.ok.set( url != null );
                        urlPrompt.value.set( url != null ? url.toString() : "" );
                    }
                });
    }


    @Override
    public void verify( IProgressMonitor monitor ) {
        exception = null;
        
        if (url != null) {
            monitor.beginTask( "Downloading", IProgressMonitor.UNKNOWN );
            File tempDir = ImportTempDir.create();
            downloaded = new File( tempDir, FilenameUtils.getName( url.getPath() ) );
            try (
                OutputStream out = new FileOutputStream( downloaded );
                InputStream in = url.openStream();
            ){
                Timer timer = new Timer();
                byte[] buf = new byte[4096];
                long count = 0;
                for (int c=in.read( buf ); c>-1; c=in.read( buf )) {
                    out.write( buf, 0, c );
                    count += c;

                    if (monitor.isCanceled()) {
                        break;
                    }
                    else {
                        monitor.subTask( FileUtils.byteCountToDisplaySize( count ) );
                        timer.start();
                    }
                }
                site.ok.set( true );
            }
            catch (Exception e) {
                site.ok.set( false );
                exception = e;
                return;
            }
        }
        else {
            site.ok.set( false );
        }
    }


    @Override
    public void createResultViewer( Composite parent, IPanelToolkit tk ) {
        if (downloaded != null) {
            tk.createFlowText( parent, "Download complete.\n\n" 
                    + "> " + downloaded.getName() + "\n\n"
                    + "> " + FileUtils.byteCountToDisplaySize( downloaded.length() ) );            
        }
        else {
            tk.createFlowText( parent, "Unable to download file." );
            if (exception != null) {
                tk.createFlowText( parent, "Reason: " + exception.getMessage() );                
            }
        }
    }


    @Override
    public void execute( IProgressMonitor monitor ) throws Exception {
    }

}
