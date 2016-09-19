/*
 * polymap.org 
 * Copyright (C) 2015 individual contributors as indicated by the @authors tag.
 * All rights reserved.
 * 
 * This is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 */
package org.polymap.p4.data.importer.prompts;

import static org.polymap.core.ui.FormDataFactory.on;
import static org.polymap.rhei.batik.app.SvgImageRegistryHelper.DISABLED12;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Splitter;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;

import org.polymap.rhei.batik.toolkit.ActionText;
import org.polymap.rhei.batik.toolkit.ClearTextAction;
import org.polymap.rhei.batik.toolkit.IPanelToolkit;
import org.polymap.rhei.batik.toolkit.TextActionItem;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.data.importer.ImporterPrompt;
import org.polymap.p4.data.importer.ImporterPrompt.PromptUIBuilder;
import org.polymap.p4.data.importer.Messages;

/**
 * 
 * @author Joerg Reichert <joerg@mapzone.io>
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public abstract class FilteredListPromptUIBuilder
        implements PromptUIBuilder {

    protected static final IMessages       i18n = Messages.forPrefix( "ImporterPrompt" );
    
    protected Set<String>                  items;

    protected org.eclipse.swt.widgets.List list;

    
    protected abstract Set<String> listItems();

    protected abstract String initiallySelectedItem();

    protected abstract void handleSelection( String selectedItem );

    
    @Override
    public void createContents( ImporterPrompt prompt, Composite parent, IPanelToolkit tk ) {
        parent.setLayout( FormLayoutFactory.defaults().spacing( 0 ).create() );
        
        Label desc = FormDataFactory.on( tk.createLabel( parent, prompt.description.get(), SWT.WRAP ) )
                .top( 0 ).left( 0 ).width( DEFAULT_WIDTH ).control();

        items = listItems();
        String initiallySelected = initiallySelectedItem();
        if (StringUtils.isBlank( initiallySelected )) {
            initiallySelected = i18n.get("filterAction");
        }
        
        Label label = on( tk.createLabel( parent, summary(), SWT.NONE ) ).fill().top( desc, 15 ).noBottom().control();
        
        ActionText actionText = tk.createActionText( parent, initiallySelected, SWT.BORDER ) ;
        on( actionText.getControl() ).left( 0 ).top( label ).right( 100 );
        new TextActionItem( actionText, TextActionItem.Type.DEFAULT )
            .action.put( ev -> updateList( actionText.getText().getText() ) )
            .text.put( initiallySelected )
            .tooltip.put( description() )
            .icon.put( P4Plugin.images().svgImage( "magnify.svg", DISABLED12 ) );

        new ClearTextAction( actionText ).action.put( ev -> {
            actionText.getText().setText( "" );
            updateList( "" );
        } );
        // XXX performDelayMillies doesnt work currently
        actionText.performOnEnter.set( false );
        actionText.performDelayMillis.set( 100 );
       
        actionText.getText().addModifyListener( new ModifyListener() {
            @Override
            public void modifyText( ModifyEvent ev ) {
                updateList( actionText.getText().getText() );
            }
        } );

        list = on( tk.createList( parent, SWT.V_SCROLL, SWT.H_SCROLL ) )
                .left(0).top( actionText.getControl(), 10 ).right( 100 ).width( DEFAULT_WIDTH ).height( 200 ).control();
                
        setListItems( filterSelectable( initiallySelected ) );
        list.setSelection( new String[] { initiallySelected } );
        list.addSelectionListener( new SelectionAdapter() {
            @Override
            public void widgetSelected( SelectionEvent ev ) {
                String item = list.getItem( list.getSelectionIndex() );
                handleSelection( item );
            }
        } );
    }
    
    private void updateList( final String searchText ) {
        setListItems( filterSelectable( searchText ) );
        if (list.getItems().length > 0) {
            list.select( 0 );
            handleSelection( list.getItem( list.getSelectionIndex() ) );
        }
    }


    protected abstract String description();

    protected abstract String summary();

    protected void setListItems( List<String> filtered ) {
        String[] array = filtered.toArray( new String[ filtered.size() ] );
        list.setItems( array );
    }

    
    protected List<String> filterSelectable( String text ) {
        List<String> tags = Splitter.on( " " ).omitEmptyStrings().trimResults().splitToList( text.toLowerCase() );
        List<String> result = new ArrayList( 128 );
        outer: for (String item : items) {
            for (String tag : tags) {
                if (!item.toLowerCase().contains( tag )) {
                    continue outer;
                }
            }
            result.add( item );
        }
        return result;
    }
    
}
