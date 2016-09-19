/* 
 * polymap.org
 * Copyright (C) 2016, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.layer;

import java.util.EventObject;

import org.opengis.filter.Filter;

import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.ConfigurationFactory;
import org.polymap.core.runtime.config.Immutable;
import org.polymap.core.runtime.config.Mandatory;

/**
 * Fired when {@link FeatureLayer} selection has changed. 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class FeatureSelectionEvent
        extends EventObject {

    @Mandatory
    @Immutable
    public Config<Filter>       newSelection;
    
    @Mandatory
    @Immutable
    public Config<Filter>       oldSelection;
    
    
    public FeatureSelectionEvent( FeatureLayer source, Filter newSelection, Filter oldSelection ) {
        super( source );
        ConfigurationFactory.inject( this );
        this.newSelection.set( newSelection );
        this.oldSelection.set( oldSelection );
    }

    @Override
    public FeatureLayer getSource() {
        return (FeatureLayer)super.getSource();
    }

}
