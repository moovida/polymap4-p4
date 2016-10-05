/* 
 * polymap.org
 * Copyright (C) 2015, Falko Bräutigam. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.core.CorePlugin;

/**
 * Provides temp dirs for uploaded files and temp content of {@link Importer}s.  
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class ImportTempDir {

    private static Log log = LogFactory.getLog( ImportTempDir.class );
    
    private static final File   baseTempDir = new File( CorePlugin.getDataLocation( ImporterPlugin.instance() ), "temp" );

    
    static {
        try {
            baseTempDir.mkdirs();
            FileUtils.cleanDirectory( baseTempDir );
            log.info( baseTempDir + " ...clean.");
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
    }

    
    public static File create() {
        try {
            return Files.createTempDirectory( baseTempDir.toPath(), null ).toFile();
        }
        catch (IOException e) {
            throw new RuntimeException( e );            
        }
    }
    
}
