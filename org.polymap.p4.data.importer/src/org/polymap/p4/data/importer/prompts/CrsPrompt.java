/* 
 * polymap.org
 * Copyright (C) 2016, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.data.importer.prompts;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Joiner;

import org.polymap.core.data.util.Geometries;
import org.polymap.core.runtime.i18n.IMessages;

import org.polymap.p4.data.importer.ImporterPrompt;
import org.polymap.p4.data.importer.ImporterPrompt.Severity;
import org.polymap.p4.data.importer.ImporterSite;
import org.polymap.p4.data.importer.Messages;

/**
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 * @author Steffen Stundzig
 */
public class CrsPrompt {

    private static final Log log = LogFactory.getLog( CrsPrompt.class );

    private static final IMessages    i18n = Messages.forPrefix( "CrsPrompt" );

    private ImporterSite              site;

    private CoordinateReferenceSystem selection;

    /** description -> code */
    private Map<String,String>        crsNames;


    public CrsPrompt( ImporterSite site, CoordinateReferenceSystem initialSelection ) {
        this.site = site;
        this.selection = initialSelection;
        
        initCrsNames();

        Severity severity = Severity.REQUIRED;
        if (selection != null) {
            severity = Severity.VERIFY;
            
            // check authority
            String srs = CRS.toSRS( selection );
            if (srs != null && !srs.startsWith( "EPSG:" )) {
                severity = Severity.REQUIRED;
                // XXX CRS.lookupEpsgCode() crashes because of Mercator1SPGoogle bundle classloading issue
                // would it be better to use lookupEpsgCode() anyway? 
                for (Map.Entry<String,String> entry : crsNames.entrySet()) {
                    try {
                        CoordinateReferenceSystem candidate = Geometries.crs( entry.getValue() );
                        if (CRS.equalsIgnoreMetadata( candidate, selection )) {
                            this.selection = candidate;
                            severity = Severity.VERIFY;
                            break;
                        }
                    }
                    catch (Exception e) {
                        log.warn( "", e );
                    }
                }
            }
        }
        
        site.newPrompt( "crs" )
                .summary.put( i18n.get( "summary" ) )
                .description.put( i18n.get( "description" ) )
                .value.put( labelOf( selection ) )
                .severity.put( severity )
                .extendedUI.put( new FilteredListPromptUIBuilder() {
                    
                    @Override
                    public void submit( ImporterPrompt prompt ) {
                        prompt.ok.set( true );
                        prompt.value.put( labelOf( selection ) );
                    }
                    
                    @Override
                    protected Set<String> listItems() {
                        return crsNames.keySet();
                    }
                    
                    @Override
                    protected List<String> filterSelectable( String text ) {
                        List<String> result = super.filterSelectable( text );
                        return result.size() > 100 ? result.subList( 0, 100 ) : result;
                    }

                    @Override
                    protected String initiallySelectedItem() {
                        return labelOf( selection );
                    }
                    
                    @Override
                    protected void handleSelection( String selected ) {
                        try {
                            String code = crsNames.get( selected );
                            selection = Geometries.crs( code );
                            assert selection != null;
                        }
                        catch (Exception e) {
                            throw new RuntimeException( e );
                        }
                    }

                    @Override
                    protected String description() {
                        return i18n.get( "filterDescription" );
                    }

                    @Override
                    protected String summary() {
                        return i18n.get( "filterSummary" );
                    }
                });
    }

    
    /**
     * The selected {@link CoordinateReferenceSystem}. 
     */
    public CoordinateReferenceSystem selection() {
        return selection;
    }


    protected String labelOf( CoordinateReferenceSystem crs ) {
        return crs != null
                ? CRS.toSRS( crs )
                : "???";
                
//        String code = CRS.toSRS( crs );
//        String crsName = crsNames.entrySet().stream()
//                .filter( entry -> entry.getValue().equals( code ) )
//                .map( entry -> entry.getKey() )
//                .findAny().orElse( null );
//        if (crsName == null) {
//            crsName = code;
//            crsNames.put( crsName, crsName );
//        }
//        return crsName;
    }
    

    /**
     * All CRS Names from all available CRS authorities.
     */
    protected void initCrsNames() {
        crsNames = new TreeMap<String,String>();
        for (Object object : ReferencingFactoryFinder.getCRSAuthorityFactories( null )) {
            CRSAuthorityFactory factory = (CRSAuthorityFactory)object;
            try {
                Set<String> codes = factory.getAuthorityCodes( CoordinateReferenceSystem.class );
                for (Object codeObj : codes) {
                    String code = (String)codeObj;
                    
                    // XXX falko: dirty hack to allow just EPSG:XXX CRSs
                    if (code != null && code.startsWith( "EPSG" )) {
                        try {
                            String description = Joiner.on( "" ).join( factory.getDescriptionText( code ).toString(), " (", code, ")" );
                            crsNames.put( description, code );
                        }
                        catch (Exception e1) {
                            // XXX falko: no UNNAMED CRSs
                            continue;
                        }
                    }
                }
            }
            catch (FactoryException e) {
                throw new RuntimeException( e );
            }
        }
    }

}
