/* 
 * polymap.org
 * Copyright (C) 2015-2016, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.data;

import java.util.HashMap;
import java.util.Map;

import org.polymap.core.data.feature.DataSourceProcessor;
import org.polymap.core.data.feature.FeatureRenderProcessor2;
import org.polymap.core.data.image.ImageDecodeProcessor;
import org.polymap.core.data.image.ImageEncodeProcessor;
import org.polymap.core.data.pipeline.DefaultPipelineIncubator;
import org.polymap.core.data.pipeline.PipelineIncubator;
import org.polymap.core.data.pipeline.PipelineProcessor;
import org.polymap.core.data.pipeline.PipelineProcessorSite;
import org.polymap.core.data.pipeline.ProcessorDescription;
import org.polymap.core.data.raster.RasterRenderProcessor;
import org.polymap.core.data.wms.WmsRenderProcessor;
import org.polymap.core.project.ILayer;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class P4PipelineIncubator
        extends DefaultPipelineIncubator
        implements PipelineIncubator {

    /** Terminal and transformer processors. */
    private static final Class<PipelineProcessor>[] defaultProcTypes = new Class[] {
        ImageEncodeProcessor.class,
        ImageDecodeProcessor.class,
        FeatureRenderProcessor2.class,
        DataSourceProcessor.class,
        WmsRenderProcessor.class,
        RasterRenderProcessor.class,
    };

    
    public static P4PipelineIncubator forLayer( ILayer layer ) {
        return new P4PipelineIncubator();
    }
    
    
    // instance *******************************************
    
    private Map<String,Object>      properties = new HashMap();
    
    
    public P4PipelineIncubator() {
        super( defaultProcTypes );
    }
    

    public P4PipelineIncubator addProperty( String key, Object value ) {
        if (properties.put( key, value ) != null) {
            throw new IllegalStateException( "Property already exists: " + key );
        }
        return this;
    }
    
    
    @Override
    protected PipelineProcessorSite createProcessorSite( ProcessorDescription procDesc ) {
        Map<String,Object> props = new HashMap( properties );
        if (procDesc.getProps() != null) {
            props.putAll( procDesc.getProps() );
        }
        return new PipelineProcessorSite( props );
    }
    
}
