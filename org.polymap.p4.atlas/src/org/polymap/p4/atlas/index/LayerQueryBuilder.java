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
package org.polymap.p4.atlas.index;

import static org.polymap.core.data.DataPlugin.ff;

import org.geotools.data.Query;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.config.Config2;
import org.polymap.core.runtime.config.ConfigurationFactory;
import org.polymap.core.runtime.config.Immutable;

import org.polymap.p4.layer.FeatureLayer;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class LayerQueryBuilder {

    @Immutable
    public Config2<LayerQueryBuilder,String>               queryText;

    @Immutable
    public Config2<LayerQueryBuilder,ReferencedEnvelope>   mapExtent;


    /** Constructs a new instance with no restrictions. */
    public LayerQueryBuilder() {
        ConfigurationFactory.inject( this );
    }

    public Query build( ILayer layer ) throws Exception {
        Filter filter = Filter.INCLUDE;
        //
        if (queryText.isPresent() && mapExtent.isPresent()) {
            AtlasIndex index = AtlasIndex.instance();
            CoordinateReferenceSystem layerCrs = FeatureLayer.of( layer ).get().get().featureSource().getSchema().getCoordinateReferenceSystem();
            ReferencedEnvelope transformedExtent = mapExtent.get().transform( layerCrs, true );
            filter = ff.and(
                    ff.bbox( ff.property( "" ), transformedExtent ),
                    index.query( queryText.get(), layer ) );
        }
        //
        else if (queryText.isPresent() || mapExtent.isPresent()) {
            throw new UnsupportedOperationException( "Both, queryText and mapExtent have to be set." );
        }
        return new Query( "", filter );
    }
    
}
