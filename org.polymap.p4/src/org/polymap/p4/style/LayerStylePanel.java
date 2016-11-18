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
package org.polymap.p4.style;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.polymap.core.project.ILayer;
import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.contribution.ContributionManager;
import org.polymap.rhei.batik.toolkit.Snackbar.Appearance;
import org.polymap.p4.P4Panel;
import org.polymap.p4.P4Plugin;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 * @author Steffen Stundzig
 */
public class LayerStylePanel
        extends P4Panel {

    private static final Log log = LogFactory.getLog( LayerStylePanel.class );

    public static final PanelIdentifier ID = PanelIdentifier.parse( "layerStyle" );
    
    /** Inbound: */
    @Mandatory
    @Scope( P4Plugin.StyleScope )
    private Context<FeatureStyleEditorInput> editorInput;
    
    private FeatureStyleEditor          editor;

    private Button                      fab;


//    @Override
//    public boolean beforeInit() {
//        IPanel parent = getContext().getPanel( getSite().getPath().removeLast( 1 ) );
//        if (parent instanceof LayerInfoPanel) {
//            site().title.set( "" );
//            site().tooltip.set( "Edit styling" );
//            site().icon.set( P4Plugin.images().svgImage( "palette.svg", P4Plugin.HEADER_ICON_CONFIG ) );
//            return true;
//        }
//        return false;
//    }


    @Override
    public void init() {
        super.init();
        site().setSize( SIDE_PANEL_WIDTH, SIDE_PANEL_WIDTH, Integer.MAX_VALUE );
        this.editor = new FeatureStyleEditor( editorInput.get() ) {
            @Override
            protected void enableSubmit( boolean enabled ) {
                if (fab != null && !fab.isDisposed()) {
                    fab.setEnabled( enabled );
                    fab.setVisible( enabled );
                }
            }
        };
    }


    @Override
    public void dispose() {
        super.dispose();
        if (editor != null) {
            editor.dispose();
            editor = null;
        }
    }


    @Override
    public void createContents( Composite parent ) {
        site().title.set( "Style" );  // + ": " + layer.get().label.get() );
        ContributionManager.instance().contributeTo( this, this );
        
        editor.createContents( parent, tk() );        
        //ContributionManager.instance().contributeTo( toolbar, this, TOOLBAR );
        
        // fab
        fab = tk().createFab();
        fab.setVisible( false );
        fab.setToolTipText( "Save changes" );
        fab.addSelectionListener( new SelectionAdapter() {
            @Override
            public void widgetSelected( SelectionEvent ev ) {
                editor.store();

                tk().createSnackbar( Appearance.FadeIn, "Saved" );
                fab.setEnabled( false );
                //fab.setVisible( false );
                
                ILayer layer = featureLayer.get().layer();
                layer.styleIdentifier.set( layer.styleIdentifier.get() );
            }
        });
    }
    
}
