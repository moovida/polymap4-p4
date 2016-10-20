/*
 * polymap.org Copyright (C) 2015, Falko Bräutigam. All rights reserved.
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

import java.util.Map.Entry;
import java.util.regex.Pattern;

import java.net.URL;

import org.geotools.data.ows.CRSEnvelope;
import org.geotools.data.ows.Layer;
import org.geotools.data.ows.Service;
import org.geotools.data.ows.WMSCapabilities;
import org.geotools.data.wms.WebMapServer;
import org.geotools.data.wms.request.GetMapRequest;
import org.geotools.data.wms.response.GetMapResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Sets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IProgressMonitor;
import org.polymap.core.catalog.IMetadata;
import org.polymap.core.catalog.IUpdateableMetadataCatalog.Updater;
import org.polymap.core.data.wms.catalog.WmsServiceResolver;
import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.ColumnDataFactory;
import org.polymap.core.ui.ColumnLayoutFactory;
import org.polymap.core.ui.FormLayoutFactory;

import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.toolkit.IPanelToolkit;
import org.polymap.rhei.batik.toolkit.SimpleDialog;

import org.polymap.p4.P4Plugin;
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
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class WmsImporter
        implements Importer {

    private static final Log       log = LogFactory.getLog( WmsImporter.class );

    private static final IMessages i18n = Messages.forPrefix( "WMS" );

    public static final Pattern    urlPattern = Pattern.compile( "((https?):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)" );

    private ImporterSite           site;

    private Exception              exception;

    private ImporterPrompt         urlPrompt;

    private String                 url;

    private WebMapServer           wms;

    private IPanelToolkit          toolkit;


    @Override
    public ImporterSite site() {
        return site;
    }


    @Override
    public void init( @SuppressWarnings( "hiding" ) ImporterSite site, IProgressMonitor monitor ) {
        this.site = site;
        site.icon.set( ImporterPlugin.images().svgImage( "earth.svg", SvgImageRegistryHelper.NORMAL24 ) );
        site.summary.set( i18n.get( "summary" ) );
        site.description.set( i18n.get( "description" ) );
        site.terminal.set( true );
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        // start without alert icon and un-expanded
        urlPrompt = site.newPrompt( "url" )
                .summary.put( "URL" )
                .value.put( "<Click to specify>" )
                .description.put( "The URL of the remote server" )
                .severity.put( Severity.INFO )
                .extendedUI.put( new PromptUIBuilder() {
                    @Override
                    public void createContents( ImporterPrompt prompt, Composite parent, IPanelToolkit tk ) {
                        parent.setLayout( FormLayoutFactory.defaults().spacing( 3 ).create() );
                        Text text = on( tk.createText( parent, 
                                url != null ? url : "", 
                                SWT.BORDER | SWT.WRAP | SWT.MULTI ) )
                                .top( 0 ).left( 0 ).right( 100 ).width( DEFAULT_WIDTH ).height( 40 ).control();
                        text.forceFocus();

                        final Label msg = on( tk.createLabel( parent, "" ) ).fill().top( text ).control();

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
                } );
    }


    @Override
    public void verify( IProgressMonitor monitor ) {
        try {
            if (url != null) {
                wms = new WebMapServer( new URL( url ), 10 );
                WMSCapabilities capabilities = wms.getCapabilities();
                String title = capabilities.getService().getTitle();
                log.info( "Service title: " + title );

                if (P4Plugin.localCatalog().query( "", monitor ).execute().stream().filter( metadata -> metadata.getTitle().equals( title ) ).findAny().isPresent()) {
                    throw new Exception( i18n.get( "entryExists", title ) );
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
        final ScrolledComposite scrolledComposite = new ScrolledComposite( parent, SWT.V_SCROLL | SWT.H_SCROLL );
        scrolledComposite.setExpandHorizontal( true );
        scrolledComposite.setExpandVertical( true );

        final Composite content = new Composite( scrolledComposite, SWT.NONE );
        scrolledComposite.setContent( content );
        content.setLayout( ColumnLayoutFactory.defaults().columns( 1, 1 ).margins( 0 ).create() );
        int preferredWidth = preferredWidth( parent );
        if (exception != null) {
            ColumnDataFactory.on( tk.createFlowText( content, i18n.get( "exception", exception.getMessage() ) ) ).widthHint( preferredWidth );
        }
        else if (wms == null) {
            ColumnDataFactory.on( tk.createFlowText( content, i18n.get( "noUrl" ) ) ).widthHint( preferredWidth );
        }
        else {

            WMSCapabilities capabilities = wms.getCapabilities();
            OwsMetadata md = new OwsMetadata().markdown( capabilities.getService() ).markdown( capabilities.getLayerList() );
            ColumnDataFactory.on( tk.createFlowText( content, md.toString() ) ).widthHint( preferredWidth );

            UIJob job = new UIJob( "Load image preview" ) {

                @Override
                protected void runWithException( IProgressMonitor monitor ) throws Exception {
                    try {
                        GetMapRequest getMapRequest = wms.createGetMapRequest();
                        boolean bboxSet = false;
                        for (Layer layer : capabilities.getLayerList()) {
                            if (!StringUtils.isBlank( layer.getName() )) {
                                getMapRequest.addLayer( layer );
                            }
                            // use the first found bbox as default
                            if (!bboxSet && !layer.getBoundingBoxes().isEmpty()) {
                                Entry<String,CRSEnvelope> bbox = layer.getBoundingBoxes().entrySet().iterator().next();
                                getMapRequest.setSRS( bbox.getKey() );
                                getMapRequest.setBBox( bbox.getValue() );
                                bboxSet = true;
                            }
                        }
                        // png is the default
                        getMapRequest.setFormat( "image/png" );
                        getMapRequest.setDimensions( preferredWidth, preferredWidth );
                        getMapRequest.setTransparent( true );

                        GetMapResponse getMapResponse = wms.issueRequest( getMapRequest );
                        ImageData id = new ImageData( getMapResponse.getInputStream() );
                        UIThreadExecutor.asyncFast( () -> {
                            if (!content.isDisposed()) {
                                Image image = new Image( null, id );
                                final Label imagePreview = tk.createLabel( content, "", SWT.NONE );
                                ColumnDataFactory.on( imagePreview ).widthHint( preferredWidth ).heightHint( preferredWidth );
                                imagePreview.setImage( image );
                                content.layout( true );
                                scrolledComposite.setMinSize( content.computeSize( preferredWidth, SWT.DEFAULT ) );
                            }
                        } );
                    }
                    catch (Exception e) {
                        // do nothing here, url or external server seems to be broken
                        log.error( e );
                    }
                }
            };
            job.schedule();
        }
        content.layout( true );
        scrolledComposite.setMinSize( content.computeSize( preferredWidth, SWT.DEFAULT ) );
    }


    @Override
    public void execute( IProgressMonitor monitor ) throws Exception {
        // create catalog entry
        try (Updater update = P4Plugin.localCatalog().prepareUpdate()) {
            Service service = wms.getCapabilities().getService();
            update.newEntry( metadata -> {
                metadata.setTitle( service.getTitle() );
                metadata.setDescription( normalize( service.get_abstract() ) );
                metadata.setType( "Service" );
                metadata.setFormats( Sets.newHashSet( "WMS" ) );

                if (service.getKeywordList() != null) {
                    metadata.setKeywords( Sets.newHashSet( service.getKeywordList() ) );
                }

                // WMS provides some more but GeoTools does not deliver more info
                metadata.setDescription( IMetadata.Field.Publisher, 
                        normalize( new OwsMetadata().markdown( service.getContactInformation() ).toString() ) );

                metadata.setConnectionParams( WmsServiceResolver.createParams( url ) );
            });
            update.commit();
        }

        //
        UIThreadExecutor.async( () -> {
            SimpleDialog dialog = new SimpleDialog();
            dialog.title.put( "Information" );
            dialog.setContents( parent -> {
                toolkit.createFlowText( parent, i18n.get( "infoAdded" ) );
            } );
            dialog.addOkAction( () -> {
                dialog.close();
                return true;
            } );
            dialog.open();
        } );
    }


    protected String normalize( String s ) {
        return StringUtils.defaultIfBlank( s, null );
    }

    private int preferredWidth( Composite parent ) {
        return Math.min( parent.getDisplay().getClientArea().width, 400 ) - 50;
    }
}
