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
package org.polymap.p4.map;

import static org.polymap.core.runtime.event.TypeEventFilter.ifType;

import java.util.List;
import java.util.Objects;

import java.beans.PropertyChangeEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.polymap.core.mapeditor.MapViewer;
import org.polymap.core.project.ILayer;
import org.polymap.core.project.ILayer.LayerUserSettings;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ProjectNode;
import org.polymap.core.project.ProjectNode.ProjectNodeCommittedEvent;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.style.model.FeatureStyleCommitedEvent;

/**
 * Provides the content of an {@link IMap}.
 * <p/>
 * This also tracks the state of the layers and triggers {@link MapViewer#refresh()}
 * on {@link ProjectNodeCommittedEvent} and {@link PropertyChangeEvent}. 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class ProjectContentProvider
        implements IStructuredContentProvider {

    private static Log log = LogFactory.getLog( ProjectContentProvider.class );
    
    private IMap                map;

    private MapViewer           viewer;

    private ProjectNodeListener projectNodeListener;

    private PropertyListener    propertyListener;

    private StyleListener       styleListener;

    
    @Override
    public void inputChanged( @SuppressWarnings("hiding") Viewer viewer, Object oldInput, Object newInput ) {
        if (map != null) {
            dispose();
        }
        
        this.map = (IMap)newInput;
        this.viewer = (MapViewer)viewer;
        
        // listen to ProjectNodeCommitEvent
        projectNodeListener = new ProjectNodeListener();
        EventManager.instance().subscribe( projectNodeListener, ifType( ProjectNodeCommittedEvent.class, ev -> {
                ProjectNode src = ev.getEntity( map.belongsTo() );
                return src instanceof IMap && map.id().equals( src.id() ) 
                    || src instanceof ILayer && map.containsLayer( (ILayer)src );
                // XXX check if structural change or just label changed
        }));

        // listen to LayerUserSettings#visible
        propertyListener = new PropertyListener();
        EventManager.instance().subscribe( propertyListener, ifType( PropertyChangeEvent.class, ev -> {
            if (ev.getSource() instanceof LayerUserSettings) {
                String layerId = ((LayerUserSettings)ev.getSource()).layerId();
                return map.layers.stream()
                        .filter( l -> l.id().equals( layerId ) )
                        .findAny().isPresent();
            }
            return false;
        }));
        
        styleListener = new StyleListener();
        EventManager.instance().subscribe( styleListener, ifType( FeatureStyleCommitedEvent.class, ev -> {
            return map.layers.stream()
                    .filter( l -> l.userSettings.get().visible.get() )
                    .filter( l -> Objects.equals( l.styleIdentifier.get(), ev.getSource().id() )  )
                    .findAny().isPresent();
        }));
    }

    
    @Override
    public Object[] getElements( Object inputElement ) {
        return map.layers.stream()
                .filter( l -> l.userSettings.get().visible.get() ).toArray();
    }


    @Override
    public void dispose() {
        log.info( "..." );
        this.map = null;
        EventManager.instance().unsubscribe( projectNodeListener );
        EventManager.instance().unsubscribe( propertyListener );
        EventManager.instance().unsubscribe( styleListener );
    }

    /**
     * 
     */
    class ProjectNodeListener {
        @EventHandler( display=true, delay=100 )
        protected void onCommit( List<ProjectNodeCommittedEvent> evs ) {
//            for (ProjectNodeCommittedEvent ev : evs) {
//                log.info( "ev: " + ev.getEntityId() );
//            }
            viewer.refresh();
        }
    }

    /**
     * 
     */
    class StyleListener {
        @EventHandler( display=true, delay=100 )
        protected void onCommit( List<FeatureStyleCommitedEvent> evs ) {
            log.info( "..." );
            for (FeatureStyleCommitedEvent ev : evs) {
                for (ILayer layer : map.layers) {
                    if (Objects.equals( layer.styleIdentifier.get(), ev.getSource().id() ) ) {
                        log.info( "refresh: " + layer.label.get() );
                        viewer.refresh( layer );
                    }
                }
            }
        }
    }

    /**
     * 
     */
    class PropertyListener {
        @EventHandler( display=true, delay=100 )
        protected void onPropertyChange( List<PropertyChangeEvent> evs ) {
            viewer.refresh();
            
            // FIXME check if layer was just created and onCommit() did it already
//            for (PropertyChangeEvent ev : evs) {
//                String layerId = ((LayerUserSettings)ev.getSource()).layerId();
//                ILayer layer = map.layers.stream().filter( l -> l.id().equals( layerId ) ).findAny().get();
//                if (viewer.getLayers().conta ins( layer )) {
//                    viewer.refresh( layer );
//                }
//                else {
//                    viewer.refresh();
//                }
//                viewer.refresh();
//            }
        }
    }
    
}
