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

import static org.polymap.core.runtime.UIThreadExecutor.asyncFast;

import java.util.function.Consumer;

import java.io.IOException;

import org.geotools.data.FeatureSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;

import org.eclipse.core.runtime.jobs.IJobChangeEvent;

import org.polymap.core.catalog.resolve.IResourceInfo;
import org.polymap.core.data.util.NameImpl;
import org.polymap.core.operation.OperationSupport;
import org.polymap.core.project.IMap;
import org.polymap.core.style.DefaultStyle;
import org.polymap.core.style.model.FeatureStyle;
import org.polymap.core.ui.StatusDispatcher;

import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.IPanel;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.contribution.IContributionSite;
import org.polymap.rhei.batik.contribution.IFabContribution;
import org.polymap.rhei.batik.toolkit.Snackbar;
import org.polymap.rhei.batik.toolkit.md.MdToolkit;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.catalog.ResourceInfoPanel;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class NewLayerContribution
        implements IFabContribution {

    private static Log log = LogFactory.getLog( NewLayerContribution.class );
    
    @Mandatory
    @Scope( P4Plugin.Scope )
    private Context<IMap>               map;
    
    @Mandatory
    @Scope( P4Plugin.Scope )
    private Context<IResourceInfo>      res;

    
    @Override
    public void fillFab( IContributionSite site, IPanel panel ) {
        if (panel instanceof ResourceInfoPanel) {
            Button fab = ((MdToolkit)site.toolkit()).createFab( P4Plugin.images().svgImage( "layers.svg", P4Plugin.HEADER_ICON_CONFIG ) );
            fab.setToolTipText( "Create a new layer for this data set" );
            fab.addSelectionListener( new SelectionAdapter() {
                @Override
                public void widgetSelected( SelectionEvent ev ) {
                    execute( site );
                }
            });
        }
    }


    protected void execute( IContributionSite site ) {
        createLayer( res.get(), map.get(), ev -> {
            if (ev.getResult().isOK()) {
//                PanelPath parentPath = site.panelSite().path().removeLast( 1 );
//                BatikApplication.instance().getContext().closePanel( parentPath );
                
                ((MdToolkit)site.toolkit()).createSnackbar( Snackbar.Appearance.FadeIn, "Layer has been created" );
            }
            else {
                StatusDispatcher.handleError( "Unable to create new layer.", ev.getResult().getException() );
            }
        });
    }

    
    public static void createLayer( IResourceInfo res, IMap map, Consumer<IJobChangeEvent> finalizer ) {
        // create default style
        // XXX 86: [Style] Default style (http://github.com/Polymap4/polymap4-p4/issues/issue/86
        // see AddLayerOperationConcern
        FeatureStyle featureStyle = P4Plugin.styleRepo().newFeatureStyle();
        try {
            FeatureSource fs = P4Plugin.localCatalog().localFeaturesStore().getFeatureSource( new NameImpl( res.getName() ) );
            DefaultStyle.create( featureStyle, fs.getSchema() );
        }
        catch (IOException e) {
            DefaultStyle.createAllStyles( featureStyle );
        }
        
        NewLayerOperation op = new NewLayerOperation()
                .res.put( res )
                .featureStyle.put( featureStyle )
                .uow.put( map.belongsTo() )
                .map.put( map );

        OperationSupport.instance().execute2( op, true, false, ev2 -> asyncFast( () -> {
            finalizer.accept( ev2 );
        }));
    }
    
}
