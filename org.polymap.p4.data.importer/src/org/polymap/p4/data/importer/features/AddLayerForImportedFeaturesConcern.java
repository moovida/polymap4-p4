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
package org.polymap.p4.data.importer.features;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.resource.JFaceResources;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

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
public class AddLayerForImportedFeaturesConcern
        extends IOperationConcernFactory {

    private static Log log = LogFactory.getLog( AddLayerForImportedFeaturesConcern.class );


    @Override
    public IUndoableOperation newInstance( IUndoableOperation op, OperationInfo info ) {
        return op instanceof ImportFeaturesOperation 
                ? new AddLayerOperationConcern( (ImportFeaturesOperation)op, info ) 
                : null;
    }

    
    /**
     * 
     */
    static class AddLayerOperationConcern
            extends OperationConcernAdapter {

        private OperationInfo           info;
        
        private ImportFeaturesOperation delegate;

        private Text                    input;

        @Mandatory
        @Scope(P4Plugin.Scope)
        protected Context<IMap>         map;

        
        public AddLayerOperationConcern( ImportFeaturesOperation op, OperationInfo info ) {
            this.delegate = op;
            this.info = info;
            BatikApplication.instance().getContext().propagate( this );
        }

        @Override
        public IStatus execute( IProgressMonitor monitor, IAdaptable a ) throws ExecutionException {
            IStatus result = info.next().execute( monitor, a );
            if (result.isOK()) {
                UIThreadExecutor.asyncFast( () -> {
                    new SimpleDialog().title.put( "Create new layer" )
                            .setContents( parent -> {
                                parent.setLayout( ColumnLayoutFactory.defaults().columns( 1, 1 ).spacing( 0 ).create() );
                                Label msg = new Label( parent, SWT.WRAP );
                                msg.setText( "Do you want to create a layer for the newly imported data?" );
                                ColumnDataFactory.on( msg ).widthHint( 250 ).heightHint( 50 );

                                Label l = new Label( parent, SWT.NONE );
                                l.setText( "Layer name" );
                                l.setFont( JFaceResources.getFontRegistry().getBold( JFaceResources.DEFAULT_FONT ) );

                                input = new Text( parent, SWT.BORDER );
                                input.setText( delegate.createdFeatureStore().getSchema().getName().getLocalPart() );
                                input.setFocus();
                                ColumnDataFactory.on( input ).widthHint( 250 );
                            })
                            .addNoAction()
                            .addYesAction( action -> {
                                createLayer();
                            })
                            .open();
                });
            }
            return result;
        }

        protected void createLayer() {
            // create default style
            // XXX 86: [Style] Default style (http://github.com/Polymap4/polymap4-p4/issues/issue/86
            // XXX this isn't a good place (see also NewLayerContribution)
            FeatureStyle featureStyle = P4Plugin.styleRepo().newFeatureStyle();
            DefaultStyle.create( featureStyle, delegate.features().getSchema() );

            NewLayerOperation op = new NewLayerOperation()
                    .label.put( input.getText() )
                    .res.put( delegate.resource() )
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
