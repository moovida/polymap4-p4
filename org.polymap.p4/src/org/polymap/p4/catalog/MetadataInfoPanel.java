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
package org.polymap.p4.catalog;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.ViewerCell;

import org.polymap.core.catalog.IMetadata;
import org.polymap.core.catalog.resolve.IResourceInfo;
import org.polymap.core.catalog.ui.MetadataContentProvider;
import org.polymap.core.catalog.ui.MetadataDescriptionProvider;
import org.polymap.core.catalog.ui.MetadataLabelProvider;
import org.polymap.core.project.IMap;
import org.polymap.core.ui.SelectionAdapter;
import org.polymap.core.ui.StatusDispatcher;

import org.polymap.rhei.batik.BatikApplication;
import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.PanelPath;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.app.SvgImageRegistryHelper;
import org.polymap.rhei.batik.contribution.ContributionManager;
import org.polymap.rhei.batik.dashboard.Dashboard;
import org.polymap.rhei.batik.dashboard.DashletSite;
import org.polymap.rhei.batik.dashboard.DefaultDashlet;
import org.polymap.rhei.batik.toolkit.MinHeightConstraint;
import org.polymap.rhei.batik.toolkit.MinWidthConstraint;
import org.polymap.rhei.batik.toolkit.PriorityConstraint;
import org.polymap.rhei.batik.toolkit.md.ActionProvider;
import org.polymap.rhei.batik.toolkit.md.MdListViewer;

import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;
import org.polymap.p4.layer.NewLayerContribution;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class MetadataInfoPanel
        extends P4Panel {

    private static Log log = LogFactory.getLog( MetadataInfoPanel.class );

    public static final PanelIdentifier ID = PanelIdentifier.parse( "metadata" );
    
    public static final String          DASHBOARD_ID = "org.polymap.p4.catalog.metadata";
    
    public static final int             TEXTFIELD_HEIGHT = 100;
    
    /** Inbound: */
    @Scope( P4Plugin.Scope )
    private Context<IMap>               map;
    
    /** Inbound: */
    @Scope( P4Plugin.Scope )
    private Context<IMetadata>          md;

    /** Outbound: */
    @Scope( P4Plugin.Scope )
    private Context<IResourceInfo>      selectedResource;

    private Dashboard                   dashboard;

    

    @Override
    public void createContents( Composite parent ) {
        site().title.set( md.get().getTitle() );
        site().setSize( SIDE_PANEL_WIDTH, SIDE_PANEL_WIDTH, SIDE_PANEL_WIDTH2 );
        ContributionManager.instance().contributeTo( this, this );
        
        dashboard = new Dashboard( getSite(), DASHBOARD_ID );
        dashboard.addDashlet( new MetadataInfoDashlet( md.get() )
                .addConstraint( new PriorityConstraint( 100 ) ) );
//        dashboard.addDashlet( new ResourcesDashlet()
//                .addConstraint( new PriorityConstraint( 0 ) ) );
        dashboard.createContents( parent );
        ContributionManager.instance().contributeTo( dashboard, this );
    }


    /**
     * 
     */
    protected class ResourcesDashlet
            extends DefaultDashlet 
            implements IOpenListener {

        private MdListViewer            viewer;

        @Override
        public void init( DashletSite site ) {
            super.init( site );
            site.title.set( "Data sets" );
            site.addConstraint( new MinWidthConstraint( P4Panel.SIDE_PANEL_WIDTH, 1 ) );
            site.constraints.get().add( new MinHeightConstraint( P4Panel.SIDE_PANEL_WIDTH-50, 1 ) );
            site.border.set( false );
        }

        @Override
        public void createContents( Composite parent ) {
            parent.setLayout( new FillLayout() ); //.defaults().create() );
            viewer = tk().createListViewer( parent, SWT.VIRTUAL, SWT.FULL_SELECTION, SWT.SINGLE );
            viewer.setContentProvider( new MetadataContentProvider( P4Plugin.allResolver() ) );
            viewer.firstLineLabelProvider.set( new MetadataLabelProvider() );
            viewer.secondLineLabelProvider.set( new MetadataDescriptionProvider() );
            viewer.iconProvider.set( new MetadataIconProvider() );
            viewer.firstSecondaryActionProvider.set( new CreateLayerAction() );
            viewer.addOpenListener( this );
            viewer.setAutoExpandLevel( 3 );
            viewer.setInput( md.get() );
            
//            viewer.getTree().setLayoutData( FormDataFactory.filled().height( SIDE_PANEL_WIDTH-50 ).create() );
            //viewer.expandToLevel( 2 );
        }

        @Override
        public void open( OpenEvent ev ) {
            SelectionAdapter.on( ev.getSelection() ).forEach( elm -> {
                if (elm instanceof IResourceInfo) {
                    selectedResource.set( (IResourceInfo)elm );
                    getContext().openPanel( MetadataInfoPanel.this.site().path(), ResourceInfoPanel.ID );                        
                }
                else {
                    viewer.toggleItemExpand( elm );
                }
            });
        }
    }
    

    /**
     * 
     */
    protected class CreateLayerAction
            extends ActionProvider {

        @Override
        public void update( ViewerCell cell ) {
            Object elm = cell.getElement();
            if (elm instanceof IResourceInfo) {
                cell.setImage( P4Plugin.images().svgImage( "plus-circle.svg", SvgImageRegistryHelper.OK24 ) );
            }
        }

        @Override
        public void perform( MdListViewer viewer, Object elm ) {
            IResourceInfo res = (IResourceInfo)elm;
            NewLayerContribution.createLayer( res, map.get(), ev -> {
                if (ev.getResult().isOK()) {
                    PanelPath parentPath = site().path().removeLast( 1 );
                    BatikApplication.instance().getContext().closePanel( parentPath );
                }
                else {
                    StatusDispatcher.handleError( "Unable to create new layer.", ev.getResult().getException() );
                }
            });
        }    
    }
    
}
