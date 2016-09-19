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
package org.polymap.p4.layer;

import static org.polymap.core.runtime.event.TypeEventFilter.ifType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;
import org.polymap.core.project.ProjectNode.ProjectNodeCommittedEvent;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.IPanel;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.Memento;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.contribution.IContributionSite;
import org.polymap.rhei.batik.contribution.IToolbarContribution;
import org.polymap.rhei.batik.toolkit.ItemContainer;
import org.polymap.rhei.batik.toolkit.RadioItem;
import org.polymap.rhei.batik.toolkit.md.MdToolbar2;

import org.polymap.model2.runtime.EntityRuntimeContext.EntityStatus;
import org.polymap.model2.runtime.UnitOfWork;
import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.map.ProjectMapPanel;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class FeatureSelectionTableContrib
        implements IToolbarContribution {

    private static Log log = LogFactory.getLog( FeatureSelectionTableContrib.class );

    /** Inbound */
    @Mandatory
    @Scope( P4Plugin.Scope )
    private Context<IMap>               map;

    /** Outbound. See {@link P4Panel#featureLayer}. */
    @Scope( P4Plugin.Scope )
    private Context<FeatureLayer>       featureLayer;
    
    private IContributionSite           site;

    private MdToolbar2                  toolbar;
    
    private Map<String,RadioItem>       createdLayerIds = new HashMap();

    private UnitOfWork                  uow;
    

    @Override
    @SuppressWarnings( "hiding" )
    public void fillToolbar( IContributionSite site, MdToolbar2 toolbar ) {
        if (site.panel() instanceof ProjectMapPanel 
                && site.tagsContain( ProjectMapPanel.BOTTOM_TOOLBAR_TAG )) {
            
            this.site = site;
            this.toolbar = toolbar;
            this.uow = map.get().belongsTo();
            
            for (ILayer layer : map.get().layers) {
                createLayerItem( toolbar, layer );
            }
            
            EventManager.instance().subscribe( this, ifType( ProjectNodeCommittedEvent.class, ev -> {
                if (ev.getSource() instanceof ILayer) {
                    // update or removed
                    if (createdLayerIds.containsKey( ev.getEntityId() )) {
                        return true;
                    }
                    // created
                    ILayer layer = ev.getEntity( uow );
                    if (map.get().containsLayer( layer )) {
                        return true;
                    }
                }
                return false;
            }));
        }
    }

    
    /**
     * Handle layer create/remove/update events.
     * <p/>
     * XXX Correctness depends on the delay :( If to short then this handler is
     * called twice and creates two entries for the same layer. createdLayerIds does
     * not prevent this as it is updated asynchronously.
     *
     * @param evs
     */
    @EventHandler( display=true, delay=100 )
    protected void mapLayerChanged( List<ProjectNodeCommittedEvent> evs ) {
        if (toolbar.getControl().isDisposed()) {
            EventManager.instance().unsubscribe( FeatureSelectionTableContrib.this );
        }
        else {
            Set<String> handledLayerIds = new HashSet();
            for (ProjectNodeCommittedEvent ev : evs) {
                if (!handledLayerIds.contains( ev.getEntityId() )) {
                    handledLayerIds.add( ev.getEntityId() );
                    
                    // removed
                    if (ev.getSource().status().equals( EntityStatus.REMOVED )) {
                        RadioItem item = createdLayerIds.remove( ev.getEntityId() );
                        item.dispose();
                    }
                    // newly created
                    else if (!createdLayerIds.containsKey( ev.getEntityId() )) {
                        ILayer layer = ev.getEntity( uow );
                        createLayerItem( toolbar, layer );                    
                    }
                    // modified
                    else if (createdLayerIds.containsKey( ev.getEntityId() )) {
                        ILayer layer = ev.getEntity( uow );
                        RadioItem item = createdLayerIds.get( layer.id() );
                        item.text.set( label( layer ) );
                    }
                }
            }
        }
    }

    
    protected String label( ILayer layer ) {
        return StringUtils.abbreviate( layer.label.get(), 17 );
    }
    
    
    protected void createLayerItem( ItemContainer group, ILayer layer ) {
        Memento memento = site.panel().site().memento();
        Optional<String> selectedLayerId = memento.getOrCreateChild( getClass().getName() ).optString( "selectedLayerId" );
        
        FeatureLayer.of( layer ).thenAccept( fl -> {
            if (fl.isPresent()) {
                UIThreadExecutor.async( () -> {
                    RadioItem item = new RadioItem( group );
                    item.text.put( label( layer ) );
                    item.tooltip.put( "Show contents of " + layer.label.get() );
                    item.icon.put( P4Plugin.images().svgImage( "layers.svg", P4Plugin.TOOLBAR_ICON_CONFIG ) );
                    AtomicBoolean wasVisible = new AtomicBoolean();
                    item.onSelected.put( ev -> {
                        featureLayer.set( fl.get() );
                        createTableView();
                        saveState( layer, true );

                        wasVisible.set( layer.userSettings.get().visible.get() );
                        layer.userSettings.get().visible.set( true );
                    });
                    item.onUnselected.put( ev -> {
                        featureLayer.set( null );
                        ((ProjectMapPanel)site.panel()).closeButtomView();
                        layer.userSettings.get().visible.set( wasVisible.get() );
                        saveState( layer, false );
                    });

                    createdLayerIds.put( layer.id(), item );

                    // memento select
                    item.selected.set( selectedLayerId.orElse( "$%&" ).equals( layer.id() ) );
                });
            }
        })
        .exceptionally( e -> {
            log.warn( "No FeatureSelection for: " + layer.label.get() + " (" + e.getMessage() + ")" );
            return null;
        });
    }
    
    
    /**
     * Creates the table for the current {@link #featureLayer}.
     */
    protected void createTableView() {
        // create bottom view
        ((ProjectMapPanel)site.panel()).updateButtomView( parent -> {            
            site.toolkit().createFlowText( parent, " Loading " + featureLayer.get().layer().label.get() + "..." );
            parent.layout();

            UIThreadExecutor.async( () -> {
                UIUtils.disposeChildren( parent );
                new FeatureSelectionTable( parent, featureLayer.get(), site.panel() );
                parent.layout();
            });
        });
    }
    
    
    protected void saveState( ILayer layer, boolean visible ) {
        IPanel panel = site.panel();
        Memento memento = panel.site().memento();
        Memento childMem = memento.getOrCreateChild( getClass().getName() );
        childMem.putString( "selectedLayerId", visible ? (String)layer.id() : "__unselected__" );
        memento.save();
    }

}
