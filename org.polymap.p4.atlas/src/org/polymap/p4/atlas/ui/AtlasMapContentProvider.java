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
package org.polymap.p4.atlas.ui;

import static org.polymap.core.runtime.event.TypeEventFilter.ifType;

import java.util.List;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ProjectNode.ProjectNodeCommittedEvent;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.p4.atlas.AtlasFeatureLayer;
import org.polymap.p4.atlas.AtlasFeatureLayer.PropertyChangeEvent;

/**
 * Provides the content of an {@link IMap}.
 * <p/>
 * This also tracks the state of the layers and triggers {@link MapViewer#refresh()}
 * on {@link ProjectNodeCommittedEvent} and {@link PropertyChangeEvent}. 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class AtlasMapContentProvider
        implements IStructuredContentProvider {

    private static final Log log = LogFactory.getLog( AtlasMapContentProvider.class );
    
    private IMap                map;

    private MapViewer           viewer;


    @Override
    public void inputChanged( @SuppressWarnings("hiding") Viewer viewer, Object oldInput, Object newInput ) {
        if (map != null) {
            dispose();
        }
        
        this.map = (IMap)newInput;
        this.viewer = (MapViewer)viewer;
        
        // listen to AtlasFeatureLayer#visible
        EventManager.instance().subscribe( this, ifType( PropertyChangeEvent.class, ev -> {
            String layerId = ev.getSource().layer().id();
            log.info( "Check (visible): " + ev.getSource().layer().label.get() );
            return map.layers.stream()
                    .filter( l -> l.id().equals( layerId ) )
                    .findAny().isPresent();
        }));
    }

    
    @Override
    public void dispose() {
        log.info( "..." );
        this.map = null;
        EventManager.instance().unsubscribe( this );
    }


    @Override
    public Object[] getElements( Object inputElement ) {
        return map.layers.stream()
                .filter( l -> { 
                    try { 
                        Optional<AtlasFeatureLayer> afl = AtlasFeatureLayer.of( l ).get();
                        return afl.isPresent() ? afl.get().visible.get() : false; 
                    }
                    catch (Exception e) { 
                        log.warn( "", e ); return false; 
                    }
                })
                .toArray();
    }


    @EventHandler( display=true, delay=500 )
    protected void onPropertyChange( List<PropertyChangeEvent> evs ) {
        viewer.refresh();
    }
    
}
