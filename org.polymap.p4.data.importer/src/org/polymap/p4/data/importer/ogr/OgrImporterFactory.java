/*
 * Copyright (C) 2017 individual contributors as indicated by the 
 * @authors tag. All rights reserved.
 * 
 * This is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 */
package org.polymap.p4.data.importer.ogr;

import java.util.List;

import java.io.File;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.polymap.p4.data.importer.ContextIn;
import org.polymap.p4.data.importer.ImporterFactory;

/**
 * 
 * 
 * @author Falko Bräutigam
 */
public class OgrImporterFactory
        implements ImporterFactory {

    @ContextIn
    protected File       file;

    @ContextIn
    protected List<File> files;


    @Override
    public void createImporters( ImporterBuilder builder ) throws Exception {
        if (file != null && isSupported( file )) {
            builder.newImporter( new GeojsonOgrImporter(), file );
        }
        if (files != null) {
            for (File currentFile : files) {
                if (isSupported( currentFile )) {
                    builder.newImporter( new GeojsonOgrImporter(), currentFile );
                }
            }
        }
    }


    private boolean isSupported( File f ) {
        return GeojsonOgrTransformer.isSupported( f, new NullProgressMonitor() );
    }
    
}