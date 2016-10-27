/* 
 * polymap.org
 * Copyright (C) 2016, the @autors. All rights reserved.
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

import static java.util.Collections.singletonList;

import java.util.List;
import java.io.File;

import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.Lists;

import org.polymap.p4.data.importer.ContextIn;
import org.polymap.p4.data.importer.ImporterFactory;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class RasterImporterFactory
        implements ImporterFactory {

    public static final List<String> SUPPORTED_EXTS = Lists.newArrayList( "geotiff", "tiff", "tif", /*"tfw", "wld",*/ "asc", "adf" );
    
    @ContextIn
    protected File                  file;
    
    @ContextIn
    protected List<File>            files;
    

    @Override
    public void createImporters( ImporterBuilder builder ) throws Exception {
        if (file != null && isSupported( file )) {
            builder.newImporter( new RasterImporter(), singletonList( file ) );            
        }
        else if (files != null) {
            for (File f : files) {
                if (isSupported( f )) {
                    builder.newImporter( new RasterImporter(), files );
                }
            }
        }
    }
    
    
    protected static boolean isSupported( File f ) {
        String ext = FilenameUtils.getExtension( f.getName() );
        return SUPPORTED_EXTS.contains( ext.toLowerCase() );
    }
    
}
