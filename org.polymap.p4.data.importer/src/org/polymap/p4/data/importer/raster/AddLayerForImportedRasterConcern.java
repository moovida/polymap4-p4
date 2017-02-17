/* 
 * polymap.org
 * Copyright (C) 2015, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.data.importer.raster;

import java.util.List;

import java.io.IOException;

import org.geotools.coverage.grid.GridCoverage2D;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.FluentIterable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.resource.JFaceResources;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.polymap.core.catalog.resolve.IResourceInfo;
import org.polymap.core.data.raster.catalog.GridServiceInfo;
import org.polymap.core.operation.IOperationConcernFactory;
import org.polymap.core.operation.OperationConcernAdapter;
import org.polymap.core.operation.OperationInfo;
import org.polymap.core.operation.OperationSupport;
import org.polymap.core.project.IMap;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.style.DefaultStyle;
import org.polymap.core.style.model.FeatureStyle;
import org.polymap.core.ui.ColumnDataFactory;
import org.polymap.core.ui.ColumnLayoutFactory;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.BatikApplication;
import org.polymap.rhei.batik.Context;
import org.polymap.rhei.batik.Mandatory;
import org.polymap.rhei.batik.Scope;
import org.polymap.rhei.batik.toolkit.SimpleDialog;

import org.polymap.p4.P4Plugin;
import org.polymap.p4.layer.NewLayerOperation;
import org.polymap.p4.project.ProjectRepository;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class AddLayerForImportedRasterConcern
        extends IOperationConcernFactory {

    private static final Log log = LogFactory.getLog( AddLayerForImportedRasterConcern.class );

    @Override
    public IUndoableOperation newInstance( IUndoableOperation op, OperationInfo info ) {
        return op instanceof ImportRasterOperation 
                ? new AddLayerOperationConcern( (ImportRasterOperation)op, info ) 
                : null;
    }

    
    /**
     * 
     */
    static class AddLayerOperationConcern
            extends OperationConcernAdapter {

        private OperationInfo           info;
        
        private ImportRasterOperation   delegate;

        private Text                    input;

        @Mandatory
        @Scope(P4Plugin.Scope)
        protected Context<IMap>         map;

        private List<IResourceInfo>     res;

        private Combo                   combo;

        
        public AddLayerOperationConcern( ImportRasterOperation op, OperationInfo info ) {
            BatikApplication.instance().getContext().propagate( this );
            this.delegate = op;
            this.info = info;
        }

        @Override
        public IStatus execute( IProgressMonitor monitor, IAdaptable a ) throws ExecutionException {
            IStatus result = info.next().execute( monitor, a );
            
            if (result.isOK()) {
                try {
                    GridServiceInfo gridService = delegate.gridService.get();
                    this.res = FluentIterable.from( gridService.getResources( monitor ) ).toList();
                }
                catch (Exception e) {
                    throw new ExecutionException( "Unable to ", e );
                }
                
                UIThreadExecutor.asyncFast( () -> {
                    new SimpleDialog().title.put( "New layer" )
                            .setContents( parent -> {
                                parent.setLayout( ColumnLayoutFactory.defaults().columns( 1, 1 ).spacing( 1 ).create() );
                                Label msg = new Label( parent, SWT.WRAP );
                                msg.setText( "Do you want to create a layer for the newly imported data?" );
                                ColumnDataFactory.on( msg ).widthHint( 250 ).heightHint( 50 );

                                Label l = new Label( parent, SWT.NONE );
                                l.setText( "Coverage" );
                                l.setFont( JFaceResources.getFontRegistry().getBold( JFaceResources.DEFAULT_FONT ) );

                                combo = new Combo( parent, SWT.READ_ONLY );
                                combo.setItems( res.stream().map( r -> r.getName() ).toArray( length -> new String[length] ) );
                                combo.addSelectionListener( UIUtils.selectionListener( ev -> {
                                    input.setText( res.get( combo.getSelectionIndex() ).getName() );
                                }));
                                combo.select( 0 );

                                l = new Label( parent, SWT.NONE );
                                l.setText( "Layer name" );
                                l.setFont( JFaceResources.getFontRegistry().getBold( JFaceResources.DEFAULT_FONT ) );

                                input = new Text( parent, SWT.BORDER );
                                input.setText( res.get( combo.getSelectionIndex() ).getName() );
                                input.setFocus();
                                ColumnDataFactory.on( input ).widthHint( 250 );
                            })
                            .addNoAction()
                            .addYesAction( action -> {
                                try {
                                    IResourceInfo selected = res.get( combo.getSelectionIndex() );
                                    GridCoverage2D grid = delegate.gridReader.get().read( selected.getName(), null );
                                    createLayer( grid, selected );
                                }
                                catch (IOException e) {
                                    throw new RuntimeException( e );
                                }
                            })
                            .open();
                });
            }
            return result;
        }

        protected void createLayer( GridCoverage2D grid, IResourceInfo selected ) {
            // create default style
            // XXX 86: [Style] Default style (http://github.com/Polymap4/polymap4-p4/issues/issue/86
            // XXX this isn't a good place (see also NewLayerContribution)
            FeatureStyle featureStyle = P4Plugin.styleRepo().newFeatureStyle();
            DefaultStyle.fillGrayscaleStyle( featureStyle, grid );

            NewLayerOperation op = new NewLayerOperation()
                    .label.put( input.getText() )
                    .res.put( selected )
                    .featureStyle.put( featureStyle )
                    .uow.put( ProjectRepository.unitOfWork() )
                    .map.put( map.get() );

            OperationSupport.instance().execute( op, true, false );
        }
        
        @Override
        protected OperationInfo getInfo() {
            return info;
        }
        
    }
    
}
