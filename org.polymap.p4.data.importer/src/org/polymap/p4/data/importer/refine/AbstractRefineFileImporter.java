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
package org.polymap.p4.data.importer.refine;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import java.io.File;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.osgi.framework.ServiceReference;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.refine.importing.ImportingJob;
import com.google.refine.model.Cell;
import com.google.refine.model.Column;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.core.data.refine.RefineService;
import org.polymap.core.data.refine.impl.FormatAndOptions;
import org.polymap.core.data.refine.impl.ImportResponse;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.ui.FormDataFactory;

import org.polymap.rhei.batik.toolkit.IPanelToolkit;
import org.polymap.rhei.table.FeatureCollectionContentProvider;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.data.importer.ContextIn;
import org.polymap.p4.data.importer.ContextOut;
import org.polymap.p4.data.importer.Importer;
import org.polymap.p4.data.importer.ImporterPrompt;
import org.polymap.p4.data.importer.ImporterPrompt.PromptUIBuilder;
import org.polymap.p4.data.importer.ImporterSite;
import org.polymap.p4.data.importer.Messages;
import org.polymap.p4.data.importer.prompts.CrsPrompt;
import org.polymap.p4.data.importer.prompts.SchemaNamePrompt;
import org.polymap.p4.data.importer.shapefile.ShpFeatureTableViewer;

/**
 * @author <a href="http://stundzig.it">Steffen Stundzig</a>
 */
public abstract class AbstractRefineFileImporter<T extends FormatAndOptions>
        implements Importer {

    private static final Log log = LogFactory.getLog( AbstractRefineFileImporter.class );

    protected static final IMessages     i18nPrompt = Messages.forPrefix( "ImporterPrompt" );

    protected static final IMessages     i18nRefine = Messages.forPrefix( "ImporterRefine" );

    /*
     * GeometryFactory will be used to create the geometry attribute of each feature
     * (a Point object for the location)
     */
    private final static GeometryFactory GEOMETRYFACTORY = JTSFactoryFinder.getGeometryFactory( null );

    // all known synonyms for longitude in lower case in all languages
    // TODO: this should be part of a *longitude synonym registry* in the user
    // settings too, to better support frequent uploads
    // each user selected columnname for longitude and latitude should be saved in
    // this user settings
    private static final Set<String>     LONGITUDES = Sets.newHashSet( "geo_longitude", "lon", "longitude", "geo_x", "x" );

    // all known synonyms for latitude in lower case in all languages
    private static final Set<String>     LATITUDES = Sets.newHashSet( "geo_latitude", "lat", "latitude", "geo_y", "y" );

    protected ImporterSite               site;

    @ContextIn
    protected File                       file;

    @SuppressWarnings("rawtypes")
    @ContextOut
    protected FeatureCollection          features;

    private RefineService                service;

    private ImportingJob                 importJob;

    private T                            formatAndOptions;

    private Exception                    exception;

    private String                       longitudeColumn;

    private String                       latitudeColumn;

    private TypedContent                 typedContent;

    private Project                      project;

    private boolean                      shouldUpdateOptions = false;

    private CrsPrompt                    crsPrompt;

    private SchemaNamePrompt             schemaNamePrompt;


    @Override
    public ImporterSite site() {
        return site;
    }


    @Override
    public void init( @SuppressWarnings("hiding" ) ImporterSite site, IProgressMonitor monitor) throws Exception {
        this.site = site;

        ServiceReference<?> serviceReference = P4Plugin.instance().getBundle().getBundleContext()
                .getServiceReference( RefineService.class.getName() );
        service = (RefineService)P4Plugin.instance().getBundle().getBundleContext().getService( serviceReference );

        prepare( monitor );
    }


    protected abstract T defaultOptions();


    @Override
    public void execute( IProgressMonitor monitor ) throws Exception {
        // create all params for contextOut
        // use the typed column from the preview and cancel the import create a
        project = service.createProject( importJob, formatAndOptions, monitor );
        // reset it
        typedContent = null;
        // log.info( "project has rows: " + project.rows.size() );
        // TODO MONITOR for the features
        features = schemaNamePrompt.retypeFeatures( createFeatures(), file.getName() );
    }


    @Override
    public void createPrompts( IProgressMonitor monitor ) throws Exception {
        site.newPrompt( "coordinates" )
              .summary.put( i18nPrompt.get( "coordinatesSummary" ) )
              .description.put( i18nPrompt.get( "coordinatesDescription" ) )
              .value.put( coordinatesPromptLabel() )
              .extendedUI.put( coordinatesPromptUiBuilder() );

        crsPrompt = new CrsPrompt( site, i18nPrompt.get("crsSummary"), i18nPrompt.get( "crsDescription" ), () -> {
            try {
                return CRS.decode( "EPSG:4326" );
            }
            catch (Exception e) {
                // do nothing
            }
            return null;
        });
        
        schemaNamePrompt = new SchemaNamePrompt( site, layerName() );
    }
    
    
    private SimpleFeatureCollection createFeatures() {
        TypedContent content = typedContent();
        
        int latitudeColumnIndex = content.columnIndex( latitudeColumn() );
        int longitudeColumnIndex = content.columnIndex( longitudeColumn() );
        // coordinate columns selected, but final file parsing contains crappy data,
        // so the columns or only String
        if (latitudeColumnIndex != -1 && longitudeColumnIndex != -1
                && (!Number.class.isAssignableFrom( content.columns().get( latitudeColumnIndex ).type() )
                        || !Number.class.isAssignableFrom( content.columns().get( longitudeColumnIndex ).type() ))) {
            // TODO error message to the user
            // log.error( "skipping coordinate creation" );
            latitudeColumnIndex = -1;
            longitudeColumnIndex = -1;
        }
        boolean containsGeom = latitudeColumnIndex != -1 && longitudeColumnIndex != -1;
        
        final SimpleFeatureType TYPE = buildFeatureType( containsGeom, content.columns() );
        final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder( TYPE );
        ListFeatureCollection currentFeatures = new ListFeatureCollection( TYPE );
        // TODO FeatureTable shows always latest created on top, therefore reverse
        // the order
        List<RefineRow> rows = content.rows();
        Collections.reverse( rows );

        int count = 0;
        for (RefineRow row : rows) {
            if (containsGeom) {
                // construct the coordinate
                try {
                    Number latitude = null;
                    try {
                        latitude = (Number)row.cells().get( latitudeColumnIndex ).guessedValue();
                    }
                    catch (IndexOutOfBoundsException ioobe) {
                        // ignore, seems to be an emtpy line
                    }
                    Number longitude = null;
                    try {
                        longitude = (Number)row.cells().get( longitudeColumnIndex ).guessedValue();
                    }
                    catch (IndexOutOfBoundsException ioobe) {
                        // ignore, seems to be an emtpy line
                    }
                    Point point = (latitude != null && longitude != null) ? GEOMETRYFACTORY
                            .createPoint( new Coordinate( longitude.doubleValue(), latitude.doubleValue() ) ) : null;
                    featureBuilder.add( point );
                }
                catch (Exception e) {
                    log.error( String.format( "exception in creating point for imported file" ), e );
                    featureBuilder.add( null );
                }
            }
//            else {
//                featureBuilder.add( null );
//            }
            for (RefineCell cell : row.cells()) {
                // log.info( "cell with type " + (cell != null && cell.value !=
                // null ? cell.value.getClass() : "null"));
                featureBuilder.add( cell == null ? null : cell.guessedValue() );
            }
            // features are build with a default id, thats wrong here
            SimpleFeature simpleFeature = featureBuilder.buildFeature( null );
            currentFeatures.add( new SimpleFeatureImpl( simpleFeature.getAttributes(), TYPE, null ) );
            if (count % 10000 == 0) {
                log.info( "created " + count );
            }
            count++;
        }
        return currentFeatures;
    }


    @Override
    public void createResultViewer( Composite parent, IPanelToolkit toolkit ) {
        if (exception != null) {
            toolkit.createFlowText( parent,
                    "\nUnable to read the data.\n\n" + "**Reason**: " + exception.getMessage() );
        }
        else {
            parent.setLayout( new FormLayout() );
            Label label = toolkit.createLabel( parent,
                    i18nRefine.get( features.size() == 500 ? "rowsShrinked" : "rows",
                            features.size() ) );
            FormDataFactory.on( label );

            SimpleFeatureType schema = (SimpleFeatureType)features.getSchema();
            ShpFeatureTableViewer table = new ShpFeatureTableViewer( parent, schema );
            table.setContentProvider( new FeatureCollectionContentProvider() );
            table.setInput( features );
            FormDataFactory.on( table.getControl() ).fill().top( label, 5 );
        }
    }


    protected void prepare( IProgressMonitor monitor ) throws Exception {
        // ImportService removes the original file, so create a copy here
        File copyOfOriginalFile = new File( Files.createTempDir(), FilenameUtils.getName( file.getName() ) );
        Files.copy( file, copyOfOriginalFile );

        ImportResponse<T> response = service.importFile( copyOfOriginalFile, defaultOptions(), monitor );
        importJob = response.job();
        formatAndOptions = response.options();
    }


    @Override
    public void verify( IProgressMonitor monitor ) {
        log.info( "verify" );
        // typedContent = null;
        if (shouldUpdateOptions) {
            updateOptions( monitor );
            shouldUpdateOptions = false;
        }
        site.terminal.set( true );
        try {
            features = createFeatures();
            site.ok.set( true );
            exception = null;
        }
        catch (Exception e) {
            site.ok.set( false );
            e.printStackTrace();
            exception = e;
        }
    }


    private SimpleFeatureType buildFeatureType( boolean containsGeom, List<TypedColumn> columns ) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        // no namespace for imported features
        builder.setName( new NameImpl( layerName() ) );
        builder.setCRS( crsPrompt.selection() );
        if (containsGeom) {
            // add the default GEOM
            builder.setDefaultGeometry( "theGeom" );
            builder.add( "theGeom", Point.class );
        }
        for (TypedColumn column : columns) {
            builder.add( column.name(), column.type() );
        }
        return builder.buildFeatureType();
    }


    protected synchronized TypedContent typedContent() {
        if (typedContent == null) {
            typedContent = new TypedContent( columnsWithTypes(), rows() );
        }
        return typedContent;
    }


    private List<RefineRow> rows() {
        List<RefineRow> result = Lists.newArrayList();
        List<Row> rows = originalRows();
        for (Row row : rows) {
            RefineRow resultRow = new RefineRow();
            result.add( resultRow );
            for (Cell cell : row.cells) {
                resultRow.add( new RefineCell( cell ) );
            }
        }
        return result;
    }


    /**
     * if the project is not created, the importjob content is used
     */
    protected List<Row> originalRows() {
        return project == null ? importJob.project.rows : project.rows;
    }


    private List<TypedColumn> columnsWithTypes() {
        List<TypedColumn> columnsWithType = Lists.newArrayList();
        for (Column column : originalColumns()) {
            columnsWithType.add( new TypedColumn( column.getName() ) );
        }
        // check all cells for its type and set the column type
        // String is the default in all cases
        for (RefineRow row : rows()) {
            int i = 0;
            for (RefineCell cell : row.cells()) {
                if (cell != null && cell.guessedValue() != null) {
                    TypedColumn column = columnsWithType.get( i );
                    Class currentType = cell.guessedValue().getClass();
                    // if null, set it
                    // if string dont change it
                    // if current type = string, set it
                    // if current double and assigned long, set it
                    if (column.type() == null) {
                        column.setType( currentType );
                    }
                    else {
                        // if it was string before, it never gets overridden
                        if (!(String.class.isAssignableFrom( column.type() ))) {
                            if (Long.class.isAssignableFrom( currentType )
                                    && !(Double.class.isAssignableFrom( column.type() ))) {
                                // dont override double with long
                                column.setType( currentType );
                            }
                            else {
                                column.setType( currentType );
                            }
                        }
                    }
                }
                i++;
            }
        }
        columnsWithType.stream().filter( c -> c.type() == null ).forEach( c -> c.setType( String.class ) );
        return columnsWithType;
    }


    /**
     * if the project is not created, the importjob content is used
     */
    protected List<Column> originalColumns() {
        return project == null ? importJob.project.columnModel.columns : project.columnModel.columns;
    }


    protected String layerName() {
        if (schemaNamePrompt == null) {
            return FilenameUtils.getBaseName( file.getName() ).replace( ".", "_" );
        }
        else {
            return schemaNamePrompt.selection();
        }
    }


    protected void updateOptions( IProgressMonitor monitor ) {
        typedContent = null;
        service.updateOptions( importJob, formatAndOptions, monitor );
    }


    public T formatAndOptions() {
        return formatAndOptions;
    }


    protected String[] numberColumnNames() {
        return typedContent().columns().stream().filter( c -> Number.class.isAssignableFrom( c.type() ) )
                .map( column -> column.name() ).toArray( String[]::new );
    }


    protected PromptUIBuilder coordinatesPromptUiBuilder() {
        return new PromptUIBuilder() {

            private String latValue;

            private String lonValue;


            @Override
            public void createContents( ImporterPrompt prompt, Composite parent, IPanelToolkit tk ) {
                parent.setLayout( new FormLayout() );
                //
                String[] columnNames = numberColumnNames();
                if (columnNames.length == 0) {
                    FormDataFactory.on( tk.createLabel( parent, i18nPrompt.get( "coordinatesNocolumns" ),
                            SWT.LEFT ) ).left( 1 ).top( 5 ).width( DEFAULT_WIDTH );
                }
                else {
                    // 2 lists with all columns to select and a label in
                    // front
                    Label lonLabel = new Label( parent, SWT.LEFT );
                    lonLabel.setText( i18nRefine.get( "lonLabel" ) );

                    Combo lonValues = new Combo( parent, SWT.DROP_DOWN|SWT.READ_ONLY );
                    lonValues.setItems( columnNames );
                    lonValues.addSelectionListener( new SelectionAdapter() {
                        @Override
                        public void widgetSelected( SelectionEvent e ) {
                            Combo c = (Combo)e.getSource();
                            lonValue = c.getItem( c.getSelectionIndex() );
                        }
                    } );
                    lonValue = longitudeColumn();
                    if (!StringUtils.isBlank( lonValue )) {
                        for (int i = 0; i < lonValues.getItems().length; i++) {
                            if (lonValues.getItem( i ).equals( lonValue )) {
                                lonValues.select( i );
                            }
                        }
                    }

                    Label latLabel = new Label( parent, SWT.LEFT );
                    latLabel.setText( i18nRefine.get( "latLabel" ) );

                    Combo latValues = new Combo( parent, SWT.DROP_DOWN|SWT.READ_ONLY );
                    latValues.setItems( columnNames );

                    latValues.addSelectionListener( new SelectionAdapter() {
                        @Override
                        public void widgetSelected( SelectionEvent e ) {
                            Combo c = (Combo)e.getSource();
                            latValue = c.getItem( c.getSelectionIndex() );
                        }
                    } );
                    latValue = latitudeColumn();
                    if (!StringUtils.isBlank( latValue )) {
                        for (int i = 0; i < latValues.getItems().length; i++) {
                            if (latValues.getItem( i ).equals( latValue )) {
                                latValues.select( i );
                            }
                        }
                    }

                    FormDataFactory.on( latLabel ).left( 1 ).top( 5 ).width( DEFAULT_WIDTH );
                    FormDataFactory.on( latValues ).left( 1 ).top( latLabel, 5 ).width( DEFAULT_WIDTH );
                    FormDataFactory.on( lonLabel ).left( 1 ).top( latValues, 15 ).width( DEFAULT_WIDTH );
                    FormDataFactory.on( lonValues ).left( 1 ).top( lonLabel, 5 ).width( DEFAULT_WIDTH ).bottom( 95 );
                }
            }


            @Override
            public void submit( ImporterPrompt prompt ) {
                setLatitudeColumn( latValue );
                setLongitudeColumn( lonValue );
                prompt.value.set( coordinatesPromptLabel() );
            }
        };
    }


    protected String longitudeColumn() {
        if (longitudeColumn == null) {
            // try to find one
            for (TypedColumn column : typedContent().columns()) {
                String name = column.name();
                Class type = column.type();
                if (Number.class.isAssignableFrom( type ) && LONGITUDES.contains( name.toLowerCase() )) {
                    longitudeColumn = name;
                    break;
                }
            }
        }
        return longitudeColumn;
    }


    protected void setLongitudeColumn( String lonValue ) {
        // TODO make this persistent in the user settings
        if (!LONGITUDES.contains( lonValue )) {
            LONGITUDES.add( lonValue );
        }
        longitudeColumn = lonValue;
    }


    protected String latitudeColumn() {
        if (latitudeColumn == null) {
            for (TypedColumn column : typedContent().columns()) {
                String name = column.name();
                Class type = column.type();
                if (Number.class.isAssignableFrom( type ) && LATITUDES.contains( name.toLowerCase() )) {
                    latitudeColumn = name;
                    break;
                }
            }
        }
        return latitudeColumn;
    }


    protected void setLatitudeColumn( String latValue ) {
        // TODO make this persistent in the user settings
        if (!LATITUDES.contains( latValue )) {
            LATITUDES.add( latValue );
        }
        latitudeColumn = latValue;
    }


    protected String coordinatesPromptLabel() {
        String lat = latitudeColumn();
        String lon = longitudeColumn();
        return lat != null && lon != null ? (lat != null ? lat : "") + "/" + (lon != null ? lon : "") : "";
    }


    public void triggerUpdateOptions() {
        shouldUpdateOptions = true;
    }
}
