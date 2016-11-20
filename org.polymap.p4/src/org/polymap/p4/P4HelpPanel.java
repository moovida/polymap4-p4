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
package org.polymap.p4;

import org.polymap.rhei.batik.BatikPlugin;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.help.HelpPanel;

/**
 * 
 *
 * @author Falko Bräutigam
 */
public class P4HelpPanel
        extends HelpPanel {

    public static final PanelIdentifier ID = PanelIdentifier.parse( "help" );

    @Override
    public boolean beforeInit() {
        if (super.beforeInit()) {
            site().icon.set( BatikPlugin.images().svgImage( "help-circle-outline.svg", P4Plugin.HEADER_ICON_CONFIG ) );
            return true;
        }
        return false;
    }

    
    @Override
    public void init() {
        site().setSize( P4Panel.SIDE_PANEL_WIDTH, P4Panel.SIDE_PANEL_WIDTH2, P4Panel.SIDE_PANEL_WIDTH2 );
    }

}
