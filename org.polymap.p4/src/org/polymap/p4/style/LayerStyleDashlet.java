/* 
 * polymap.org
 * Copyright (C) 2016, the @authors. All rights reserved.
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
package org.polymap.p4.style;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;

import org.polymap.rhei.batik.BatikApplication;
import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.IAppContext;
import org.polymap.rhei.batik.PanelSite;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.dashboard.DashletSite;
import org.polymap.rhei.batik.dashboard.DefaultDashlet;
import org.polymap.rhei.batik.toolkit.md.MdToolkit;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.layer.FeatureLayer;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class LayerStyleDashlet
        extends DefaultDashlet {

    private static final Log log = LogFactory.getLog( LayerStyleDashlet.class );
    
    private PanelSite                   panelSite;
    
    private LayerStylePanel             panel;

    /** Inbound: */
    @Scope( P4Plugin.Scope )
    private Context<ILayer>             layer;
    
    /** Outbound: */
    @Scope( P4Plugin.StyleScope )
    private Context<StyleEditorInput>   styleEditorInput;

    
    public LayerStyleDashlet( PanelSite panelSite ) {
        this.panelSite = panelSite;
    }


    @Override
    public void init( DashletSite site ) {
        super.init( site );
        site.title.set( "Style" );
        //site.constraints.get().add( new MinHeightConstraint( 600, 1 ) );
    }


    @Override
    public void createContents( Composite parent ) {
        panel = new LayerStylePanel();
        IAppContext context = BatikApplication.instance().getContext();
        context.propagate( panel );
        MdToolkit tk = (MdToolkit)getSite().toolkit();
        
        FeatureLayer.of( layer.get() ).thenAccept( fl -> {
            UIThreadExecutor.async( () -> {
                if (fl.isPresent()) {
                    try {
                        styleEditorInput.set( new StyleEditorInput( 
                                layer.get().styleIdentifier.get(), 
                                fl.get().featureSource() ) );
                        panel.setSite( panelSite, context );  // ???
                        panel.init();
                        panel.createContents( parent );
                    }
                    catch (Exception e) {
                        log.warn( "", e );
                        tk.createLabel( parent, "Unable to create styler." );            
                    }
                }
                else {
                    parent.setLayout( FormLayoutFactory.defaults().margins( 10 ).create() );
                    FormDataFactory.on( tk.createLabel( parent, 
                            "This layer is connected to a WMS or raster data.<br/>The style cannot be modified.", SWT.WRAP ) )
                            .fill().noBottom().height( 40 );
                }
                parent.getParent().getParent().layout( true, true );
            });
        })
        .exceptionally( e -> {
            log.warn( "", e );
            tk.createLabel( parent, "Unable to data from layer." );
            return null;
        });
    }
    
}
