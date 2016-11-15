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

import static org.polymap.p4.catalog.MetadataInfoPanel.TEXTFIELD_HEIGHT;

import java.text.DateFormat;

import com.google.common.base.Joiner;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.ui.forms.widgets.ColumnLayoutData;

import org.polymap.core.catalog.IMetadata;
import org.polymap.core.catalog.resolve.IMetadataResourceResolver;
import org.polymap.core.ui.ColumnLayoutFactory;

import org.polymap.rhei.batik.dashboard.DashletSite;
import org.polymap.rhei.batik.dashboard.DefaultDashlet;
import org.polymap.rhei.batik.toolkit.MinWidthConstraint;
import org.polymap.rhei.field.DateValidator;
import org.polymap.rhei.field.PlainValuePropertyAdapter;
import org.polymap.rhei.field.StringFormField;
import org.polymap.rhei.field.TextFormField;
import org.polymap.rhei.field.VerticalFieldLayout;
import org.polymap.rhei.form.DefaultFormPage;
import org.polymap.rhei.form.IFormPageSite;
import org.polymap.rhei.form.batik.BatikFormContainer;

import org.polymap.p4.P4Panel;

/**
 * Basic fields of the {@link IMetadata}.
 * 
 * @author Falko Bräutigam
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
        site.addConstraint( new MinWidthConstraint( P4Panel.SIDE_PANEL_WIDTH, 1 ) );
        //site.addConstraint( new MinHeightConstraint( P4Panel.SIDE_PANEL_WIDTH-50, 1 ) );
        site.border.set( false );
    }

    @Override
    public void createContents( Composite parent ) {
//        parent.setLayout( FormLayoutFactory.defaults().create() );
//        ScrolledComposite scrolled = getSite().toolkit().createScrolledComposite( parent, SWT.V_SCROLL );
//        FormDataFactory.on( scrolled ).fill().height( P4Panel.SIDE_PANEL_WIDTH-150 );
//        
//        CharArrayWriter out = new CharArrayWriter( 1024 );
//        MarkdownBuilder markdown = new MarkdownBuilder( out );
//        
//        markdown.paragraph( p -> {
//            p.em( em -> {
//               em.join( " ", md.getType().orElse( null ), md.getFormats() );
//            });
//        });
//        markdown.h3( md.getTitle() );
//        markdown.paragraph( p -> {
//            String description = md.getDescription().orElse( null );
////            if (description != null) {
////                description = StringUtils.abbreviate( description, 500 );
////            }
//            p.add( "<ul style=\"font-size:13px !important;\">{0}</ul>", description );
//        });
////        markdown.paragraph( () -> {
////            DateFormat df = SimpleDateFormat.getDateInstance( SimpleDateFormat.MEDIUM, RWT.getLocale() );
////            markdown.bullet( "created: {0} - modified: {1}", 
////                    md.getCreated().map( v -> df.format( v ) ).orElse( "?" ),
////                    md.getModified().map( v -> df.format( v ) ).orElse( "?" ) );
////        });
//
//        for (IMetadata.Field f : IMetadata.Field.values()) {
//            md.getDescription( f ).ifPresent( v -> {
//                markdown.h3( f.name() ).paragraph( v );
//            });
//        }
//        getSite().toolkit().createFlowText( ((Composite)scrolled.getContent()), out.toString() );

        BatikFormContainer form = new BatikFormContainer( new Form() );
        form.createContents( parent );
        form.setEnabled( false );
    }

    
    /**
     * 
     */
    protected class Form
            extends DefaultFormPage {

        @Override
        public void createFormContents( IFormPageSite site ) {
            super.createFormContents( site );

            site.setDefaultFieldLayout( VerticalFieldLayout.INSTANCE );
            Composite body = site.getPageBody();
            body.setLayout( ColumnLayoutFactory.defaults().spacing( 3 ).create() );

            site.newFormField( new PlainValuePropertyAdapter( "Type", 
                    md.getType().orElse( "" ) + " " + Joiner.on( ", " ).skipNulls().join( md.getFormats() ) ) ).create();

            String url = md.getConnectionParams().get( IMetadataResourceResolver.CONNECTION_PARAM_URL );
            if (url != null && url.startsWith( "http" )) {
                site.newFormField( new PlainValuePropertyAdapter( "Online resource", url ) )
                        .field.put( new TextFormField() )
                        .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, TEXTFIELD_HEIGHT ) );
            }
            
            md.getDescription().ifPresent( description -> {
                site.newFormField( new PlainValuePropertyAdapter( "description", description ) )
                        .field.put( new TextFormField() )
                        .tooltip.put( description )
                        .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, TEXTFIELD_HEIGHT ) );
            });
            
            if (!md.getKeywords().isEmpty()) {
                site.newFormField( new PlainValuePropertyAdapter( "keywords", 
                        Joiner.on( ", " ).skipNulls().join( md.getKeywords() ) ) ).create();
            }

            for (IMetadata.Field f : IMetadata.Field.values()) {
                md.getDescription( f ).ifPresent( value -> {                
                    site.newFormField( new PlainValuePropertyAdapter( f.name(), value ) )
                            .field.put( new TextFormField() )
                            .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, TEXTFIELD_HEIGHT ) );
                });
            }

//            site.newFormField( new PlainValuePropertyAdapter( "publisher", md.getDescription( IMetadata.Field.Publisher ).orElse( "-" ) ) )
//                    .field.put( new TextFormField() )
//                    .create().setLayoutData( new ColumnLayoutData( SWT.DEFAULT, MetadataInfoPanel.TEXTFIELD_HEIGHT ) );

            md.getModified().ifPresent( modified -> {
                site.newFormField( new PlainValuePropertyAdapter( "modified", modified ) )
                        .field.put( new StringFormField() )
                        .validator.put( new DateValidator( DateFormat.MEDIUM ) )
                        .create();
            });

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