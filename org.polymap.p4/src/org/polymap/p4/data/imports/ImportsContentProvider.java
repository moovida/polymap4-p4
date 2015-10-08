/* 
 * Copyright (C) 2015, the @authors. All rights reserved.
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
package org.polymap.p4.data.imports;

import static org.polymap.core.runtime.UIThreadExecutor.logErrorMsg;

import java.util.EventObject;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.jface.viewers.ILazyTreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.runtime.UIJob;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;

import org.polymap.p4.data.imports.ImporterContext.ContextChangeEvent;

/**
 * Provides content of {@link ImporterContext}, {@link Importer} and
 * {@link ImporterPrompt}.
 * <p/>
 * Refreshes the viewer on {@link ContextChangeEvent} and {@link ConfigChangeEvent}.
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
class ImportsContentProvider
        implements ILazyTreeContentProvider {

    private static Log log = LogFactory.getLog( ImportsContentProvider.class );

    public static final Object          LOADING = new Object();
    
    public static final Object[]        CACHE_LOADING = {LOADING};
    
    private TreeViewer                  viewer;
    
    private ImporterContext             context;

    private ConcurrentMap<Object,Object[]> cache = new ConcurrentHashMap( 32 );

    
    protected ImportsContentProvider() {
        EventManager.instance().subscribe( this, ev -> 
                ev instanceof ContextChangeEvent || 
                ev instanceof ConfigChangeEvent ); 
    }

    
    @EventHandler( display=true )
    protected void contentChanged( EventObject ev ) {
        log.info( "Remove cache for: " + ev.getSource().getClass().getSimpleName() );
        cache.remove( ev.getSource() );
        
        // new contextIn (root) or prompt (child) -> structural change 
        if (ev instanceof ContextChangeEvent) {
            assert ev.getSource() instanceof ImporterContext;
            viewer.refresh( ev.getSource(), true );
        }
        // labels or icon
        else if (ev instanceof ConfigChangeEvent) {
            //  ImporterSite
            if (ev.getSource() instanceof ImporterSite) {
                viewer.update( ((ImporterSite)ev.getSource()).context(), null );
            }
            // ImporterPrompt
            else if (ev.getSource() instanceof ImporterPrompt) {
                ImporterPrompt prompt = (ImporterPrompt)ev.getSource();
                viewer.update( prompt, null );
                viewer.update( prompt.context(), null );  // status might have changed 
            }
            else {
                throw new RuntimeException( "Unknown source of ConfigChangeEvent: " + ev.getSource() );
            }
        }
        else {
            throw new RuntimeException( "Unknown event type: " + ev );
        }
    }
    
    
    @Override
    public void dispose() {
        EventManager.instance().unsubscribe( this );
    }

    
    @Override
    public void inputChanged( @SuppressWarnings("hiding") Viewer viewer, Object oldInput, Object newInput ) {
        this.viewer = (TreeViewer)viewer;
        this.context = (ImporterContext)newInput;
    }


    @Override
    public void updateChildCount( Object elm, int currentChildCount ) {
        // check cache
        if (elm == LOADING) {
            return;
        }
        Object[] cached = cache.get( elm );
        if (cached != null && (cached.length == currentChildCount || cached == CACHE_LOADING)) {
            return;    
        }
        
        // start: input == ImporterContext -> next ImporterContext
        if (elm == context) {
            updateChildrenLoading( elm );
            UIJob job = new UIJob( "Progress import" ) {
                @Override
                protected void runWithException( IProgressMonitor monitor ) throws Exception {
                    assert context == elm;
                    List<ImporterContext> importers = context.findNext( monitor );
                    updateChildren( elm, importers.toArray(), currentChildCount );
                }
            };
            job.scheduleWithUIUpdate();
        }
        // ImporterContext -> ImportPrompts
        else if (elm instanceof ImporterContext) {
            updateChildrenLoading( elm );
            UIJob job = new UIJob( "Progress import" ) {
                @Override
                protected void runWithException( IProgressMonitor monitor ) throws Exception {
                    List<ImporterPrompt> prompts = ((ImporterContext)elm).prompts( monitor );
                    updateChildren( elm, prompts.toArray(), currentChildCount );
                }
            };
            job.scheduleWithUIUpdate();
        }
        // ImportPrompt
        else if (elm instanceof ImporterPrompt) {
            viewer.setChildCount( elm, 0 );
        }
        else {
            throw new RuntimeException( "Unknown element type: " + elm );
        }
    }
    

    protected void updateChildrenLoading( Object elm ) {
        cache.put( elm, CACHE_LOADING );
        UIThreadExecutor.asyncFast( () -> viewer.setChildCount( elm, 1 ) );
    }


    /**
     * Updates the {@link #cache} and the child count for this elm in the viewer/tree.
     */
    protected void updateChildren( Object elm, Object[] children, int currentChildCount  ) {
        cache.put( elm, children );
        
        UIThreadExecutor.asyncFast( () -> { 
            viewer.setChildCount( elm, children.length );
            if (children.length > 0) {
                viewer.replace( elm, 0, children[0] );  // replace the LOADING elm
            }
        }, logErrorMsg( "" ) );
    }


    @Override
    public void updateElement( Object parent, int index ) {
        Object[] children = cache.get( parent );
        if (children != null && children.length > 0) {
            viewer.replace( parent, index, children[index] );

            boolean hasChildren = !(children[index] instanceof ImporterPrompt);
            viewer.setHasChildren( children[index], hasChildren );
        }
    }

    
    @Override
    public Object getParent( Object element ) {
        // XXX Auto-generated method stub
        throw new RuntimeException( "not yet implemented." );
    }

}