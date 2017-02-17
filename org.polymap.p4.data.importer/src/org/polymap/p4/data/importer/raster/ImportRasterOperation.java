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

import java.io.File;

import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.data.ServiceInfo;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Sets;

import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.polymap.core.catalog.IUpdateableMetadataCatalog.Updater;
import org.polymap.core.catalog.resolve.IResolvableInfo;
import org.polymap.core.data.raster.catalog.GridServiceInfo;
import org.polymap.core.data.raster.catalog.GridServiceResolver;
import org.polymap.core.operation.DefaultOperation;
import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.ConfigurationFactory;
import org.polymap.core.runtime.config.Immutable;
import org.polymap.core.runtime.config.Mandatory;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.catalog.AllResolver;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class ImportRasterOperation
        extends DefaultOperation
        implements IUndoableOperation {

    private static final Log log = LogFactory.getLog( ImportRasterOperation.class );
    
    private File                        f;
    
    @Mandatory
    @Immutable
    public Config<GridCoverage2DReader> gridReader;
    
    /** The newly created service. */
    @Mandatory
    @Immutable
    public Config<GridServiceInfo>      gridService;     

    
    public ImportRasterOperation( File f, GridCoverage2DReader gridReader ) {
        super( "Import raster" );
        ConfigurationFactory.inject( this );
        this.f = f;
        this.gridReader.set( gridReader );
    }

    
    @Override
    protected IStatus doExecute( IProgressMonitor monitor, IAdaptable info ) throws Exception {
        // copy data files
        monitor.beginTask( "Copying files", IProgressMonitor.UNKNOWN );
        File targetDir = new File( P4Plugin.gridStoreDir(), FilenameUtils.getBaseName( f.getName() ) );
        FileUtils.copyDirectory( f.getParentFile(), targetDir );
        File target = new File( targetDir, f.getName() );
        
        // create catalog entry
        try (
            Updater update = P4Plugin.localCatalog().prepareUpdate()
        ){
            ServiceInfo service = gridReader.get().getInfo();
            update.newEntry( metadata -> {
                metadata.setTitle( StringUtils.isBlank( service.getTitle() )
                        ? FilenameUtils.getBaseName( service.getSource().toString() )
                        : service.getTitle() );
                
                metadata.setDescription( gridReader.get().getFormat().getName() );
                
                metadata.setType( "Grid" );
                metadata.setFormats( Sets.newHashSet( gridReader.get().getFormat().getName() ) );

                if (service.getKeywords() != null) {
                    metadata.setKeywords( service.getKeywords() );
                }

                String url = target.toURI().toString();
                metadata.setConnectionParams( GridServiceResolver.createParams( url ) );
                
                try {
                    IResolvableInfo resolvable = AllResolver.instance().resolve( metadata, monitor );
                    gridService.set( (GridServiceInfo)resolvable );
                }
                catch (Exception e) {
                    throw new RuntimeException( e );
                }
            });
            update.commit();
        }
        return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
    }

}
