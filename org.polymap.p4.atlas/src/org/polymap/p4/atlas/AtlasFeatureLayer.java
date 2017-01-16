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
package org.polymap.p4.atlas;

import java.util.EventObject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.MapMaker;

import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.config.Concern;
import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.Config2;
import org.polymap.core.runtime.config.Configurable;
import org.polymap.core.runtime.config.ConfigurationFactory;
import org.polymap.core.runtime.config.DefaultBoolean;
import org.polymap.core.runtime.config.DefaultPropertyConcern;
import org.polymap.core.runtime.config.Immutable;
import org.polymap.core.runtime.config.Mandatory;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.runtime.session.SessionContext;

import org.polymap.p4.layer.FeatureLayer;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class AtlasFeatureLayer
        extends Configurable {

    private static final Log log = LogFactory.getLog( AtlasFeatureLayer.class );
    
    private static ConcurrentMap<FeatureLayer,AtlasFeatureLayer>  instances = new MapMaker().weakKeys().makeMap();
            
    /**
     * Returns the one and only {@link AtlasFeatureLayer} for the given
     * {@link ILayer} in the current {@link SessionContext}. If not yet present this
     * computes a new instance.
     *
     * @see FeatureLayer#of(ILayer)
     * @return Newly created or cached instance.
     */
    public static CompletableFuture<Optional<AtlasFeatureLayer>> of( ILayer layer ) {
        return FeatureLayer.of( layer ).thenApply( optionalFeatureLayer -> 
                optionalFeatureLayer.map( fl -> instances.computeIfAbsent( fl, _fl -> new AtlasFeatureLayer( _fl ) ) ) );
    }


    // instance *******************************************
        
    private FeatureLayer        featureLayer;

    /** Layer is visible in the map. */
    @DefaultBoolean( false )
    @Concern( PropertyChangeEvent.Fire.class )
    public Config<Boolean>      visible;
    
    
    protected AtlasFeatureLayer( FeatureLayer featureLayer ) {
        this.featureLayer = featureLayer;
    }
    
    public FeatureLayer featureLayer() {
        return featureLayer;
    }

    /** Shortcut to {@link #featureLayer}.layer(). */
    public ILayer layer() {
        return featureLayer.layer();
    }


    /**
     * 
     */
    public static class PropertyChangeEvent
            extends EventObject {

        @Mandatory @Immutable
        public Config<Config>           prop;
        
//        @Mandatory @Immutable
//        public Config<Object>           oldValue;

        @Mandatory @Immutable
        public Config<Object>           newValue;


        public PropertyChangeEvent( AtlasFeatureLayer source) {
            super( source );
            ConfigurationFactory.inject( this );
        }

        @Override
        public AtlasFeatureLayer getSource() {
            return (AtlasFeatureLayer)super.getSource();
        }

        /**
         * 
         */
        public static class Fire
                extends DefaultPropertyConcern {

            /**
             * This is called *before* the {@link Config2} property is set. However, there is no
             * race condition between event handler thread, that might access property value, and
             * the current thread, that sets the property value, because most {@link EventHandler}s
             * are done in display thread.
             */
            @Override
            public Object doSet( Object obj, Config prop, Object newValue ) {
                PropertyChangeEvent ev = new PropertyChangeEvent( prop.info().getHostObject() );
                ev.prop.set( prop );
//                ev.oldValue.set( prop.info().getRawValue() );
                ev.newValue.set( newValue );
                log.info( "Publishing: " + prop.info().getName() + " => " + newValue );
                EventManager.instance().publish( ev );
                log.info( "Publised: " + prop.info().getName() + " => " + newValue );
                
                return newValue;
            }
        }
    }

}
