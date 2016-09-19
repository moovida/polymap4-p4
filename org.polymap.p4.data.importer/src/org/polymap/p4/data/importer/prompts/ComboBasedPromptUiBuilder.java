/*
 * polymap.org Copyright (C) @year@ individual contributors as indicated by
 * the @authors tag. All rights reserved.
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

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.polymap.core.data.refine.impl.FormatAndOptions;
import org.polymap.core.ui.FormDataFactory;

import org.polymap.rhei.batik.toolkit.IPanelToolkit;

import org.polymap.p4.data.importer.ImporterPrompt;
import org.polymap.p4.data.importer.ImporterPrompt.PromptUIBuilder;
import org.polymap.p4.data.importer.refine.AbstractRefineFileImporter;

/**
 * An abstract Builder for different prompts in the CSV and Excel imports.
 * 
 * @author <a href="http://stundzig.it">Steffen Stundzig</a>
 */
public abstract class ComboBasedPromptUiBuilder
        implements PromptUIBuilder {

    protected String                                                 value;

    protected AbstractRefineFileImporter<? extends FormatAndOptions> importer;


    public ComboBasedPromptUiBuilder( AbstractRefineFileImporter<? extends FormatAndOptions> importer ) {
        this.importer = importer;
    }


    @Override
    public void createContents( ImporterPrompt prompt, Composite parent, IPanelToolkit tk ) {
        parent.setLayout( new FormLayout() );
        Label desc = FormDataFactory.on( tk.createLabel( parent, prompt.description.get(), SWT.WRAP ) )
                .top( 0 ).left( 0 ).right( 100 ).width( DEFAULT_WIDTH ).control();

        Combo combo = new Combo( parent, SWT.SINGLE | SWT.BORDER | SWT.DROP_DOWN );
        FormDataFactory.on( combo ).left( 1 ).top( desc, 15 ).width( DEFAULT_WIDTH );

        List<String> allValues = allValues();
        combo.setItems( allValues.stream().toArray( String[]::new ) );
        combo.addSelectionListener( new SelectionAdapter() {
            @Override
            public void widgetSelected( SelectionEvent e ) {
                value = allValues.get( combo.getSelectionIndex() );
            }
        } );
        value = initialValue();
        int index = allValues.indexOf( value );
        if (index != -1) {
            combo.select( index );
        }
        prompt.value.set( value );
    }


    @Override
    public void submit( ImporterPrompt prompt ) {
        onSubmit( prompt );
        importer.triggerUpdateOptions();
        prompt.ok.set( true );
        prompt.value.set( String.valueOf( value ) );
    }


    protected abstract String initialValue();


    protected abstract List<String> allValues();


    protected abstract void onSubmit( ImporterPrompt prompt );

}
