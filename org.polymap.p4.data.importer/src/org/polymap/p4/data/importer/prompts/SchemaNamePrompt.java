/*
 * polymap.org Copyright (C) 2016, Falko Bräutigam. All rights reserved.
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

import java.util.Date;

import java.text.DateFormat;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.util.SimpleInternationalString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;

import org.apache.commons.lang3.StringUtils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.rap.rwt.RWT;

import org.polymap.core.data.rs.RFeatureStore;
import org.polymap.core.data.util.RetypingFeatureCollection;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormDataFactory;
import org.polymap.core.ui.FormLayoutFactory;

import org.polymap.rhei.batik.toolkit.IPanelToolkit;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.data.importer.ImporterPrompt;
import org.polymap.p4.data.importer.ImporterPrompt.PromptUIBuilder;
import org.polymap.p4.data.importer.ImporterPrompt.Severity;
import org.polymap.p4.data.importer.ImporterSite;
import org.polymap.p4.data.importer.Messages;

/**
 * Check duplicate schema names *before* importing them.
 * 
 * @author Steffen Stundzig
 * @author Falko Bräutigam
 */
public class SchemaNamePrompt {

    static final IMessages i18n = Messages.forPrefix( "SchemaNamePrompt" );
    
    private String          selection;

    /**
     * 
     * @param site
     * @param defaultSelection The default schema name to use.
     */
    public SchemaNamePrompt( final ImporterSite site, String defaultSelection ) {
        selection = defaultSelection;
        if (selection == null) {
            selection = "features";
        }

        site.newPrompt( "schemaName" )
            .summary.put( i18n.get( "summary" ) )
            .description.put( i18n.get( "description" ) )
            .value.put( selection )
            .severity.put( Severity.REQUIRED )
            .ok.put( !nameExists() )
            .extendedUI.put( new TextUIBuilder() );
    }


    protected boolean nameExists() {
        try {
            return P4Plugin.localCatalog().localFeaturesStore().getSchema( new NameImpl( selection ) ) != null;
        }
        catch (Exception e) {
            // do nothing, exception is thrown, if schema doesnt exist
            return false;
        }
    }


    public String selection() {
        return selection;
    }

    
    /**
     * Retype the given features: set schema name to current {@link #selection}, add
     * description the schema, set feature id.
     *
     * @param features
     * @param sourceDescription The description to be set in the adapted {@link FeatureType}.
     * @return {@link RetypingFeatureCollection}
     */
    public FeatureCollection retypeFeatures( SimpleFeatureCollection features, String sourceDescription ) {
        // no maxResults restriction
        SimpleFeatureType original = features.getSchema();
        
        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        ftb.init( original );
        ftb.setName( selection );
        DateFormat df = DateFormat.getDateInstance( DateFormat.MEDIUM, RWT.getLocale() );
        ftb.setDescription( SimpleInternationalString.wrap( "Imported from " + sourceDescription + " on " + df.format( new Date() ) ) );
        SimpleFeatureType schema = ftb.buildFeatureType();

        return new RetypingFeatureCollection<SimpleFeatureType,SimpleFeature>( features, schema ) {
            @Override
            protected SimpleFeature retype( SimpleFeature feature ) {
                SimpleFeatureBuilder fb = new SimpleFeatureBuilder( getSchema() );
                fb.init( feature );
                String fid = schema.getName().getLocalPart() + "." + RFeatureStore.idcount.getAndIncrement(); 
                return fb.buildFeature( fid );
            }
        };
    }
    

    /**
     * 
     */
    class TextUIBuilder
            implements PromptUIBuilder {

        @Override
        public void submit( ImporterPrompt prompt ) {
            prompt.ok.set( !nameExists() );
            prompt.value.put( selection );
        }


        @Override
        public void createContents( ImporterPrompt prompt, Composite parent, IPanelToolkit tk ) {
            parent.setLayout( FormLayoutFactory.defaults().spacing( 5 ).create() );
            Label desc = tk.createLabel( parent, prompt.description.get(), SWT.WRAP );
            Label l = tk.createLabel( parent, "", SWT.WRAP );

            Text text = tk.createText( parent, selection, SWT.BORDER );
            text.addModifyListener( ev -> {
                selection = ((Text)ev.getSource()).getText();
                checkName( prompt, l );
            });
            text.forceFocus();

            FormDataFactory.on( desc ).fill().noBottom().width( DEFAULT_WIDTH ).control();
            FormDataFactory.on( text ).fill().top( desc, 10 ).noBottom().width( DEFAULT_WIDTH );
            FormDataFactory.on( l ).fill().top( text ).width( DEFAULT_WIDTH );

            checkName( prompt, l );
        }


        protected void checkName( ImporterPrompt prompt, Label l ) {
            if (StringUtils.isBlank( selection )) {
                l.setText( SchemaNamePrompt.i18n.get( "noName" ) );
                //prompt.ok.set( false );
            }
            else if (nameExists()) {
                l.setText( SchemaNamePrompt.i18n.get( "wrongName" ) );
                //prompt.ok.set( false );
            }
            else {
                l.setText( SchemaNamePrompt.i18n.get( "rightName" ) );
                //prompt.ok.set( true );
            }
        }
    }

}
