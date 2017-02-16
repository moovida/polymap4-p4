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
package org.polymap.p4.data.importer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

/**
 * Describes an extension of extension point
 * {@link ImporterPlugin#ID}.{@value #POINT_ID}.
 *
 * @author Falko Bräutigam
 */
public class ImporterExtension {

    public static final String POINT_ID = "importers";
    
    public static List<ImporterExtension> all() {
        IConfigurationElement[] elms = Platform.getExtensionRegistry()
                .getConfigurationElementsFor( ImporterPlugin.ID, POINT_ID );
        return Arrays.stream( elms )
                .map( elm -> new ImporterExtension( elm ) )
                .collect( Collectors.toList() );
    }

    
    // instance *******************************************
    
    private IConfigurationElement elm;

    public ImporterExtension( IConfigurationElement elm ) {
        this.elm = elm;
    }

    public ImporterFactory createFactory() throws CoreException {
        return (ImporterFactory)elm.createExecutableExtension( "class" );
    }
    
}
