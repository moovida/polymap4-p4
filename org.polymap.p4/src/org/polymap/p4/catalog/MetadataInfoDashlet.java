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

import java.io.CharArrayWriter;
import java.text.DateFormat;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Joiner;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.ui.forms.widgets.ColumnLayoutData;

import org.polymap.core.catalog.IMetadata;
import org.polymap.core.runtime.text.MarkdownBuilder;
import org.polymap.core.ui.ColumnLayoutFactory;

import org.polymap.rhei.batik.dashboard.DashletSite;
import org.polymap.rhei.batik.dashboard.DefaultDashlet;
import org.polymap.rhei.batik.toolkit.MinWidthConstraint;
import org.polymap.rhei.field.DateValidator;
import org.polymap.rhei.field.PlainValuePropertyAdapter;
import org.polymap.rhei.field.StringFormField;
import org.polymap.rhei.field.TextFormField;
import org.polymap.rhei.form.DefaultFormPage;
import org.polymap.rhei.form.IFormPageSite;

import org.polymap.p4.P4Panel;

/**
 * Basic fields of the {@link IMetadata}. 
 */
public class MetadataInfoDashlet
        extends DefaultDashlet {

    private IMetadata           md;


    public MetadataInfoDashlet( IMetadata md ) {
        this.md = md;
    }

    @Override
    public void init( DashletSite site ) {
        super.init( site );
        site.title.set( P4Panel.title( "Data source", md.getTitle() ) );
        //site.addConstraint( new PriorityConstraint( 100 ) );
        site.addConstraint( new MinWidthConstraint( 300, 1 ) );
        site.border.set( false );
    }

    @Override
    public void createContents( Composite parent ) {
        parent.setLayout( new FillLayout() );
        
        CharArrayWriter out = new CharArrayWriter( 1024 );
        MarkdownBuilder markdown = new MarkdownBuilder( out );
        
        markdown.paragraph( p -> {
            p.em( em -> {
               em.join( " ", md.getType().orElse( null ), md.getFormats() );
            });
        });
        markdown.paragraph( p -> {
            String description = md.getDescription().orElse( null );
            if (description != null) {
                description = StringUtils.abbreviate( description, 500 );
            }
            p.add( description );
        });
//        markdown.paragraph( () -> {
//            DateFormat df = SimpleDateFormat.getDateInstance( SimpleDateFormat.MEDIUM, RWT.getLocale() );
//            markdown.bullet( "created: {0} - modified: {1}", 
//                    md.getCreated().map( v -> df.format( v ) ).orElse( "?" ),
//                    md.getModified().map( v -> df.format( v ) ).orElse( "?" ) );
//        });

        for (IMetadata.Field f : IMetadata.Field.values()) {
            md.getDescription( f ).ifPresent( v -> {
                markdown.h3( f.name() ).paragraph( v );
            });
        }
        getSite().toolkit().createFlowText( parent, out.toString() );

//        BatikFormContainer form = new BatikFormContainer( new Form() );
//        form.createContents( parent );
//        form.setEnabled( false );
    }

    
    /**
     * 
     */
    protected class Form
            extends DefaultFormPage {

        @Override
        public void createFormContents( IFormPageSite site ) {
            super.createFormContents( site );

            Composite body = site.getPageBody();
            body.setLayout( ColumnLayoutFactory.defaults().spacing( 3 ).create() );

            site.newFormField( new PlainValuePropertyAdapter( "description", md.getDescription().orElse( "-" ) ) )
                    .field.put( new TextFormField() )
                    .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, MetadataInfoPanel.TEXTFIELD_HEIGHT ) );

            site.newFormField( new PlainValuePropertyAdapter( "keywords", 
                    Joiner.on( ", " ).skipNulls().join( md.getKeywords() ) ) ).create();

            site.newFormField( new PlainValuePropertyAdapter( "creator", md.getDescription( IMetadata.Field.Creator ).orElse( "-" ) ) )
                    .field.put( new TextFormField() )
                    .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, MetadataInfoPanel.TEXTFIELD_HEIGHT ) );

            site.newFormField( new PlainValuePropertyAdapter( "publisher", md.getDescription( IMetadata.Field.Publisher ).orElse( "-" ) ) )
                    .field.put( new TextFormField() )
                    .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, MetadataInfoPanel.TEXTFIELD_HEIGHT ) );

            site.newFormField( new PlainValuePropertyAdapter( "modified", md.getModified().orElse( null ) ) )
                    .field.put( new StringFormField() )
                    .validator.put( new DateValidator( DateFormat.MEDIUM ) )
                    .create();

            //                site.newFormField( new PlainValuePropertyAdapter( "publisher", md.get().getPublisher() ) )
            //                        .field.put( new TextFormField() )
            //                        .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, TEXTFIELD_HEIGHT ) );
            //        
            //                site.newFormField( new PlainValuePropertyAdapter( "rights", md.get().getRights() ) )
            //                        .field.put( new TextFormField() )
            //                        .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, TEXTFIELD_HEIGHT ) );
        }
    }

}