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
package org.polymap.p4.catalog;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;

import com.google.common.base.Joiner;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.ui.forms.widgets.ColumnLayoutData;

import org.polymap.core.catalog.resolve.IResourceInfo;
import org.polymap.core.ui.ColumnLayoutFactory;

import org.polymap.rhei.batik.dashboard.DashletSite;
import org.polymap.rhei.batik.dashboard.DefaultDashlet;
import org.polymap.rhei.batik.toolkit.MinWidthConstraint;
import org.polymap.rhei.field.PlainValuePropertyAdapter;
import org.polymap.rhei.field.StringFormField;
import org.polymap.rhei.field.TextFormField;
import org.polymap.rhei.field.VerticalFieldLayout;
import org.polymap.rhei.form.DefaultFormPage;
import org.polymap.rhei.form.IFormPageSite;
import org.polymap.rhei.form.batik.BatikFormContainer;

import org.polymap.p4.P4Panel;

/**
 * 
 * 
 * @author Falko Bräutigam
 */
public class ResourceInfoDashlet
        extends DefaultDashlet {

    private IResourceInfo           res;
    
    
    public ResourceInfoDashlet( IResourceInfo res ) {
        super();
        this.res = res;
    }

    
    @Override
    public void init( DashletSite site ) {
        super.init( site );
        site.title.set( P4Panel.title( "Data set", res.getTitle() ) );
        site.constraints.get().add( new MinWidthConstraint( 400, 1 ) );
        site.border.set( false );
    }

    
    @Override
    public void createContents( Composite parent ) {
        parent.setLayout( new FillLayout() );
        
//        CharArrayWriter out = new CharArrayWriter( 1024 );
//        MarkdownBuilder markdown = new MarkdownBuilder( out );
//        
//        markdown.paragraph( res.getDescription().orElse( null ) )
//                .join( ", ", res.getKeywords() );
//        
//        getSite().toolkit().createFlowText( parent, out.toString() );
        
        BatikFormContainer form = new BatikFormContainer( new Form() );
        form.createContents( parent );
        form.setEnabled( false );
    }

    
    /**
     * 
     */
    class Form
            extends DefaultFormPage {

        @Override
        public void createFormContents( IFormPageSite site ) {
            super.createFormContents( site );

            site.setDefaultFieldLayout( VerticalFieldLayout.INSTANCE );
            Composite body = site.getPageBody();
            body.setLayout( ColumnLayoutFactory.defaults().spacing( 3 ).create() );

            //site.newFormField( new PlainValuePropertyAdapter( "title", res.getTitle() ) ).create();

            site.newFormField( new PlainValuePropertyAdapter( "description", res.getDescription().orElse( "" ) ) )
                    .field.put( new TextFormField() )
                    .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, MetadataInfoPanel.TEXTFIELD_HEIGHT ) );

            if (!res.getKeywords().isEmpty()) {
                String keywords = Joiner.on( ", " ).skipNulls().join( res.getKeywords() );
                site.newFormField( new PlainValuePropertyAdapter( "keywords", keywords ) )
                        .field.put( new StringFormField() )
                        .create();  //.setLayoutData( new ColumnLayoutData( SWT.DEFAULT, MetadataInfoPanel.TEXTFIELD_HEIGHT ) );
            }
            
            ReferencedEnvelope bounds = res.getBounds();
            String bs = CRS.toSRS( bounds.getCoordinateReferenceSystem() )
                    + " : " + bounds.getMinX() + " .. " + bounds.getMaxX() + ", " + bounds.getMinY() + " .. " + bounds.getMaxY();
            site.newFormField( new PlainValuePropertyAdapter( "bounds", bs ) )
                    .field.put( new StringFormField() )
                    .create();

        }
    }
    
}
