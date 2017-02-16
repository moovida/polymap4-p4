/*
 * polymap.org Copyright (C) 2016, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.data.importer.raster;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Arrays;
import java.util.List;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.data.ServiceInfo;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.referencing.CRS;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Sets;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.rap.rwt.RWT;

import org.polymap.core.catalog.IUpdateableMetadataCatalog.Updater;
import org.polymap.core.data.raster.GridCoverageReaderFactory;
import org.polymap.core.data.raster.catalog.GridServiceResolver;
import org.polymap.core.runtime.SubMonitor;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.runtime.text.MarkdownBuilder;

import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.toolkit.IPanelToolkit;
import org.polymap.rhei.batik.toolkit.SimpleDialog;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.data.importer.ContextIn;
import org.polymap.p4.data.importer.Importer;
import org.polymap.p4.data.importer.ImporterPlugin;
import org.polymap.p4.data.importer.ImporterSite;
import org.polymap.p4.data.importer.Messages;
import org.polymap.p4.data.importer.prompts.SchemaNamePrompt;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class RasterImporter
        implements Importer {

    private static final Log log  = LogFactory.getLog( RasterImporter.class );

    private static final IMessages i18n = Messages.forPrefix( "ImporterRaster" );

    private ImporterSite            site;

    @ContextIn
    protected List<File>            files;
    
    protected File                  main;

    private Exception               exception;

    private GridCoverage2DReader    grid;

    private SchemaNamePrompt        schemaNamePrompt;

    private IPanelToolkit           toolkit;


    @Override
    public ImporterSite site() {
        return site;
    }


    @Override
    @SuppressWarnings( "hiding" )
    public void init( ImporterSite site, IProgressMonitor monitor ) {
        this.site = site;
        
        // find main file
        for (File f : files) {
            if (RasterImporterFactory.isSupported( f )) {
                main = f;
                break;
            }
        }
        
        site.icon.set( ImporterPlugin.images().svgImage( "unknown_file.svg", SvgImageRegistryHelper.NORMAL24 ) );
        site.summary.set( i18n.get( "summary", main.getName() ) );
        site.description.set( i18n.get( "description" ) );
        site.terminal.set( true );
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        // schemaNamePrompt = new SchemaNamePrompt( site, getBaseName( shp.getName()
        // ) );
    }


    @Override
    public void verify( IProgressMonitor monitor ) {
        try {
            log.info( "File size: " + FileUtils.sizeOf( main ) );

            try {
                monitor.subTask( "read data" );
                grid = GridCoverageReaderFactory.openGeoTiff( main );
            }
            catch (Exception e) {
                try {
                    // translate to GeoTiff
                    main = GdalTransformer.translate( main, new SubMonitor( monitor, 1 ) );
                    grid = GridCoverageReaderFactory.openGeoTiff( main );
                }
                catch (Exception e1) {
                    // last resort: warp to EPSG:3857
                    main = GdalTransformer.warp( main, "EPSG:3857", new SubMonitor( monitor, 1 ) );
                    grid = GridCoverageReaderFactory.openGeoTiff( main );
                }
            }
            log.info( "reader: " + grid );
            log.info( "reader: " + Arrays.asList( grid.getGridCoverageNames() ) );
            log.info( "reader: " + grid.getFormat().getName() );
            log.info( "reader: " + grid.getCoordinateReferenceSystem() );

            site.ok.set( true );
            exception = null;
        }
        catch (Exception e) {
            log.warn( "", e );
            site.ok.set( false );
            exception = e;
        }
    }


    @Override
    public void createResultViewer( Composite parent, IPanelToolkit tk ) {
        this.toolkit = tk;
        
        if (exception != null) {
            tk.createFlowText( parent, "\nUnable to read the data.\n\n**Reason**: " + exception.getMessage() );
        }
        else {
            try {
                StringWriter out = new StringWriter( 1024 );
                new MarkdownBuilder( out ).locale.put( RWT.getLocale() )
                        .paragraph( "## Format: {0}", grid.getFormat().getName() )
                        .paragraph( p -> {
                            p.add( "  * Size: {0}", FileUtils.byteCountToDisplaySize( main.length() ) ).newline( 1 );
                            p.add( "  * CRS: {0}", CRS.toSRS( grid.getCoordinateReferenceSystem() ) ).newline( 1 );
                            GeneralEnvelope env = grid.getOriginalEnvelope();
                            p.add( "  * Envelope: {0}...{1} / {2}...{3}", 
                                    env.getMinimum( 0 ), env.getMaximum( 0 ),
                                    env.getMinimum( 1 ), env.getMaximum( 1 ) ).newline( 2 );
                        })
                        .paragraph( p -> {
                            p.add( "## Coverages" ).newline( 2 );
                            try {
                                for (String name : grid.getGridCoverageNames()) {
                                    p.add( "  * {0}", name ).newline( 1 );
                                }
                            }
                            catch (Exception e) {
                                log.warn( "", e );
                                p.add( "Error: " + e.getLocalizedMessage() ).newline( 1 );
                            }
                        })
                        .paragraph( p -> {
                            try {
                                if (grid.getMetadataNames() != null) {
                                    p.add( "## Metadata" ).newline( 2 );
                                    for (String name : grid.getMetadataNames()) {
                                        p.add( "  * {0}", name ).newline( 1 );
                                    }
                                }
                            }
                            catch (IOException e) {
                                log.warn( "", e );
                            }
                        });
                        //.paragraph( "*Grid support is in **BETA** stage. The data is imported and accessed as is. No error checking, validation or optimization is done.*" );
                tk.createFlowText( parent, out.toString() );
            }
            catch (Exception e) {
                log.warn( "", e );
                tk.createFlowText( parent, "\nUnable to read the data.\n\n**Reason**: " + e.getMessage() );
                site.ok.set( false );
                exception = e;
            }
        }
    }


    @Override
    public void execute( IProgressMonitor monitor ) throws Exception {
        // copy data files
        monitor.beginTask( "Copying files", IProgressMonitor.UNKNOWN );
        File targetDir = new File( P4Plugin.gridStoreDir(), getBaseName( main.getName() ) );
        FileUtils.copyDirectory( main.getParentFile(), targetDir );
        File targetMain = new File( targetDir, main.getName() );
        
        // create catalog entry
        try (Updater update = P4Plugin.localCatalog().prepareUpdate()) {
            ServiceInfo service = grid.getInfo();
            update.newEntry( metadata -> {
                metadata.setTitle( isBlank( service.getTitle() )
                        ? getBaseName( service.getSource().toString() )
                        : service.getTitle() );
                
                metadata.setDescription( grid.getFormat().getName() );
                
                metadata.setType( "Grid" );
                metadata.setFormats( Sets.newHashSet( grid.getFormat().getName() ) );

                if (service.getKeywords() != null) {
                    metadata.setKeywords( service.getKeywords() );
                }

                String url = targetMain.toURI().toString();
                metadata.setConnectionParams( GridServiceResolver.createParams( url ) );
            });
            update.commit();
        }

        //
        UIThreadExecutor.async( () -> {
            SimpleDialog dialog = new SimpleDialog();
            dialog.title.put( "Information" );
            dialog.setContents( parent -> {
                toolkit.createFlowText( parent, i18n.get( "infoAdded" ) );
            });
            dialog.addOkAction( () -> {
                dialog.close();
                return true;
            } );
            dialog.open();
        });
    }

}
