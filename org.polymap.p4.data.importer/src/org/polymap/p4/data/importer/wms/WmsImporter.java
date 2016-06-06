/*
 * polymap.org 
 * Copyright (C) 2015, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.data.importer.wms;

import static org.polymap.core.ui.FormDataFactory.on;

import java.util.regex.Pattern;
import java.net.URL;

import org.geotools.data.ows.Layer;
import org.geotools.data.ows.Service;
import org.geotools.data.ows.WMSCapabilities;
import org.geotools.data.wms.WebMapServer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Sets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.catalog.IUpdateableMetadataCatalog.Updater;
import org.polymap.core.data.wms.catalog.WmsServiceResolver;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.ui.FormLayoutFactory;

import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.toolkit.IPanelToolkit;
import org.polymap.rhei.batik.toolkit.SimpleDialog;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.data.importer.Importer;
import org.polymap.p4.data.importer.ImporterPlugin;
import org.polymap.p4.data.importer.ImporterPrompt;
import org.polymap.p4.data.importer.ImporterSite;
import org.polymap.p4.data.importer.ImporterPrompt.PromptUIBuilder;
import org.polymap.p4.data.importer.ImporterPrompt.Severity;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class WmsImporter
        implements Importer {

    private static Log log = LogFactory.getLog( WmsImporter.class );
    
    public static final Pattern     urlPattern = Pattern.compile( "((https?):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)" );
    
    private ImporterSite            site;

    private Exception               exception;

    private ImporterPrompt          urlPrompt;
    
    private String                  url;

    private WebMapServer            wms;

    private IPanelToolkit           toolkit;
    

    @Override
    public ImporterSite site() {
        return site;
    }


    @Override
    public void init( @SuppressWarnings("hiding") ImporterSite site, IProgressMonitor monitor ) {
        this.site = site;
        site.icon.set( ImporterPlugin.images().svgImage( "earth.svg", SvgImageRegistryHelper.NORMAL24 ) );
        site.summary.set( "WMS - Web Map Service" );
        site.description.set( "Simple HTTP interface for requesting map images from remote sites" );
        site.terminal.set( true );
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        urlPrompt = site.newPrompt( "url" )
                .summary.put( "URL" )
                .value.put( "<Click to specify>" )
                .description.put( "The URL of the remote server" )
                .severity.put( Severity.INFO )  // start without alert icon and un-expanded
                .extendedUI.put( new PromptUIBuilder() {
                    
                    @Override
                    public void createContents( ImporterPrompt prompt, Composite parent, IPanelToolkit tk ) {
                        parent.setLayout( FormLayoutFactory.defaults().spacing( 3 ).create() );
                        //Label l = on( tk.createLabel( parent, "URL" ) ).fill().noBottom().control();
                        Text text = on( tk.createText( parent, url != null ? url : "", SWT.BORDER ) )
                                .top( 0 ).left( 0 ).right( 100 ).width( 350 ).control();
                        text.forceFocus();
                        
                        Label msg = on( tk.createLabel( parent, "" ) ).fill().top( text ).control();                        
                        
                        text.addModifyListener( new ModifyListener() {
                            @Override
                            public void modifyText( ModifyEvent ev ) {
                                url = text.getText();
                                msg.setText( urlPattern.matcher( url ).matches() ? "Ok" : "Not a valid URL" );
                            }
                        } );
                    }

                    @Override
                    public void submit( ImporterPrompt prompt ) {
                        urlPrompt.severity.set( Severity.REQUIRED );
                        urlPrompt.ok.set( urlPattern.matcher( url ).matches() );
                        urlPrompt.value.set( url );
                    }
                });
    }


    @Override
    public void verify( IProgressMonitor monitor ) {
        try {
            if (url != null) {
                wms = new WebMapServer( new URL( url ), 5 );
                WMSCapabilities capabilities = wms.getCapabilities();
                String title = capabilities.getService().getTitle();
                log.info( "Service title: " + title );
                
                if (P4Plugin.localCatalog().query( "" ).execute().stream()
                        .filter( metadata -> metadata.getTitle().equals( title ) )
                        .findAny()
                        .isPresent()) {
                    throw new Exception( "Entry already exists in catalog: " + title );
                }
                site.ok.set( true );
                exception = null;
            }
        }
        catch (Exception e) {
            site.ok.set( false );
            exception = e;
        }
    }


    @Override
    public void createResultViewer( Composite parent, IPanelToolkit tk ) {
        this.toolkit = tk;
        if (exception != null) {
            tk.createFlowText( parent, "Unable to initialize WMS.\n\n**Reason:** " + exception.getMessage() );
        }
        else if (wms == null) {
            tk.createFlowText( parent, "Please specify the **URL** of the WMS above." );
        }
        else {
            StringBuilder msg = new StringBuilder();
            WMSCapabilities capabilities = wms.getCapabilities();
            Service serviceInfo = capabilities.getService();
            msg.append( "#### " + serviceInfo.getTitle() + " (" + serviceInfo.getName() + ")\n\n" );
            msg.append( "**Contact info:** " + serviceInfo.getContactInformation() + "\n\n");
            msg.append( "#### Layers:\n\n" );
            for (Layer layer : capabilities.getLayerList()) {
                msg.append( "  * " + layer.getTitle() + "\n" );
            }
            tk.createFlowText( parent, msg.toString() );
        }
    }


    @Override
    public void execute( IProgressMonitor monitor ) throws Exception {
        // create catalog entry
        try (Updater update = P4Plugin.localCatalog().prepareUpdate()) {
            Service info = wms.getCapabilities().getService();
            update.newEntry( metadata -> {
                metadata.setTitle( info.getTitle() );
                //metadata.setDescription( info. );
                if (info.getKeywordList() != null) {
                    metadata.setKeywords( Sets.newHashSet( info.getKeywordList() ) );
                }
                metadata.setConnectionParams( WmsServiceResolver.createParams( url ) );
            });
            update.commit();
        }
        
        //
        UIThreadExecutor.async( () -> {
            SimpleDialog dialog = new SimpleDialog();
            dialog.title.put( "Information" );
            dialog.setContents( parent -> {
                toolkit.createFlowText( parent, "Entry has been created in the data catalog.\n\nOpen a resource from the catalog in order to create a new layer." );
            });
            dialog.addOkAction( () -> dialog.close() );
            dialog.open();
        });
    }

}
