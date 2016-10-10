/*
 * polymap.org Copyright (C) 2015, Falko Bräutigam. All rights reserved.
 *
 * This is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 3.0 of the License, or (at your option) any later
 * version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 */
package org.polymap.p4.data.importer.prompts;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.Supplier;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.collections4.set.ListOrderedSet;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;

import org.polymap.core.runtime.Polymap;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormDataFactory;

import org.polymap.rhei.batik.toolkit.IPanelToolkit;

import org.polymap.p4.data.importer.ImporterPrompt;
import org.polymap.p4.data.importer.ImporterPrompt.Severity;
import org.polymap.p4.data.importer.ImporterSite;
import org.polymap.p4.data.importer.Messages;

/**
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 * @author Steffen Stundzig
 */
public class CharsetPrompt {

    private static final IMessages i18n = Messages.forPrefix( "CharsetPrompt" );

    private static final IMessages i18nPrompt = Messages.forPrefix( "ImporterPrompt" );

    private static final String    SEPARATOR = "";

    public static Charset          DEFAULT = StandardCharsets.ISO_8859_1;

    private Charset                selection;

    /** displayName -> charset */
    private Map<String,Charset>    charsets;

    private ListOrderedSet<String> displayNames;


    public CharsetPrompt( final ImporterSite site, final String summary, final String description,
            Supplier<Charset> charsetSupplier ) {
        this( site, summary, description, charsetSupplier, null );
    }

    
    public CharsetPrompt( final ImporterSite site, final String summary, final String description,
            Supplier<Charset> charsetSupplier, Supplier<List<String>> potentialEncodingProblemsSupplier ) {

        selection = charsetSupplier.get();
        if (selection == null) {
            selection = DEFAULT;
        }

        initCharsets();

        site.newPrompt( "charset" )
            .summary.put( summary )
            .description.put( description )
            .value.put( selection.name() )
            .severity.put( Severity.VERIFY )
            .extendedUI.put( new FilteredListPromptUIBuilder() {

                 @Override
                 public void submit( ImporterPrompt prompt ) {
                     prompt.ok.set( true );
                     prompt.value.put( selection.displayName( Polymap.getSessionLocale() ) );
                 }


                 @Override
                 protected Set<String> listItems() {
                     return displayNames;
                 }


                 @Override
                 protected String initiallySelectedItem() {
                     return selection.displayName( Polymap.getSessionLocale() );
                 }


                 @Override
                 protected void handleSelection( String selectedDisplayname ) {
                     selection = charsets.get( selectedDisplayname );
                     assert selection != null;
                 }
                 
                 @Override
                public void createContents( ImporterPrompt prompt, Composite parent, IPanelToolkit tk ) {
                    super.createContents( prompt, parent, tk );
                    if (potentialEncodingProblemsSupplier != null) {
                        List<String> potentialEncodingProblems = potentialEncodingProblemsSupplier.get();
                        if (potentialEncodingProblems != null && !potentialEncodingProblems.isEmpty()) {
                            Label desc = FormDataFactory.on( tk.createLabel( parent, i18n.get( "encodingSamplesDescription" ), SWT.WRAP ) ).top( list, 25 ).left( 0 ).width( 350 ).control();
                            TableViewer viewer = new TableViewer( parent, SWT.H_SCROLL |
                                    SWT.V_SCROLL );
                            // create preview table
                            ColumnViewerToolTipSupport.enableFor( viewer );
                            TableLayout layout = new TableLayout();
                            viewer.getTable().setLayout( layout );
                            viewer.getTable().setHeaderVisible( true );
                            viewer.getTable().setLinesVisible( true );
    
                            layout.addColumnData( new ColumnPixelData( 350 ) );
                            TableViewerColumn viewerColumn = new TableViewerColumn( viewer, SWT.H_SCROLL );
                            viewerColumn.getColumn().setText( i18n.get( "encodingSamples" ) );
                            viewerColumn.setLabelProvider( new ColumnLabelProvider() {
    
                                @Override
                                public String getToolTipText( Object element ) {
                                    return super.getText( element );
                                }
                            } );
    
                            viewer.setContentProvider( ArrayContentProvider.getInstance() );
                            viewer.setInput( potentialEncodingProblems );
    
                            FormDataFactory.on( viewer.getTable() ).left( 1 ).top( desc, 15 ).width( 350 ).height( 200 ).bottom( 100 );
//                            parent.pack();
                    }}
                }

                 @Override
                 protected String description() {
                     return i18nPrompt.get( "filterDescription" );
                 }

                 @Override
                 protected String summary() {
                     return i18nPrompt.get( "filterSummary" );
                 }

             } );
    }


    private void initCharsets() {
        charsets = new TreeMap<String,Charset>();

        for (Charset charset : Charset.availableCharsets().values()) {
            charsets.put( displayName( charset ), charset );
        }

        displayNames = new ListOrderedSet<String>();
        // add all defaults on top
        displayNames.add( displayName( StandardCharsets.ISO_8859_1 ) );
        displayNames.add( displayName( StandardCharsets.US_ASCII ) );
        displayNames.add( displayName( StandardCharsets.UTF_8 ) );
        displayNames.add( displayName( StandardCharsets.UTF_16 ) );
        displayNames.add( displayName( StandardCharsets.UTF_16BE ) );
        displayNames.add( displayName( StandardCharsets.UTF_16LE ) );

        // a separator
        charsets.put( SEPARATOR, selection );
        displayNames.add( SEPARATOR );

        // add the rest
        for (String displayName : charsets.keySet()) {
            displayNames.add( displayName );
        }
    }


    private String displayName( Charset charset ) {
        StringBuffer name = new StringBuffer( charset.displayName( Polymap.getSessionLocale() ) );
        if (!charset.aliases().isEmpty()) {
            name.append( " ( " );
            StringJoiner joiner = new StringJoiner( ", " );
            for (String alias : charset.aliases()) {
                joiner.add( alias );
            }
            name.append( joiner.toString() );
            name.append( " )" );
        }
        return name.toString();
    }


    /**
     * The selected {@link Charset}.
     */
    public Charset selection() {
        return selection;
    }

}
