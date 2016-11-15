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
package org.polymap.p4.layer;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.operation.projection.ProjectionException;
import org.opengis.geometry.BoundingBox;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.polymap.core.catalog.resolve.IResourceInfo;
import org.polymap.core.project.ILayer;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.config.Config2;
import org.polymap.core.runtime.config.Immutable;
import org.polymap.core.style.model.FeatureStyle;
import org.polymap.core.ui.StatusDispatcher;

import org.polymap.rhei.batik.toolkit.DefaultToolkit;
import org.polymap.rhei.batik.toolkit.SimpleDialog;

import org.polymap.p4.catalog.AllResolver;

/**
 * Extends the basic {@link org.polymap.core.project.ops.NewLayerOperation
 * NewLayerOperation} by
 * <ul>
 * <li>default {@link FeatureStyle} building</li>
 * <li>WMS resource/layer check</li>
 * <li>resource id creation</li>
 * <li>adopting map extent</li>
 * </ul>
 * <p/>
 * Do not set {@link org.polymap.core.project.ops.NewLayerOperation#initializer
 * #initializer} directly!
 * <p/>
 * Impl. note: This is not an operation concern because we need the additional
 * {@link IResourceInfo}.
 *
 * @author Falko Bräutigam
 */
public class NewLayerOperation
        extends org.polymap.core.project.ops.NewLayerOperation {

    private static final Log log = LogFactory.getLog( NewLayerOperation.class );
    
    /**
     * Inbound: (optional) Label of the newly created layer.
     */
    @Immutable
    public Config2<NewLayerOperation,String>        label;

    /**
     * Inbound: (optional) Linked to {@link ILayer#styleIdentifier} of the newly
     * created layer. Stored {@link #onSuccess()}.
     */
    @Immutable
    public Config2<NewLayerOperation,FeatureStyle>  featureStyle;

    /** Inbound: */
    @Immutable
    public Config2<NewLayerOperation,IResourceInfo> res;

    /** Internal: allow concerns to build identifier for the given {@link #res}. */ 
    @Immutable
    public Config2<NewLayerOperation,String>        resId;


    @Override
    public IStatus doWithCommit( IProgressMonitor monitor, IAdaptable info ) throws Exception {
        // check WMS resource addable
        if (res.isPresent() && res.get().getName() == null) {
            UIThreadExecutor.sync( () -> {
                new SimpleDialog()
                        .title.put( "WMS resource" )
                        .addOkAction( () -> true )
                        .setContents( parent -> {
                            Label msg = new Label( parent, SWT.NONE );
                            // XXX add markup enabled
                            msg.setText( "This WMS resource has no name.<br/> It does not seem to be a layer." );
                        })
                        .open();
                return null;
            });
            return Status.OK_STATUS;
        }

        // build resource identifier
        if (!resId.isPresent()) {
            resId.set( AllResolver.resourceIdentifier( res.get() ) );
        }
        initializer.set( (ILayer proto) -> {
            proto.label.set( label
                    .orElse( () -> res.get().getName() ) );
            proto.description.set( res
                    .map( r -> r.getDescription().orElse( null ) )
                    .orElse( null ) );
            proto.resourceIdentifier.set( resId
                    .orElse( () -> AllResolver.resourceIdentifier( res.get() ) ) );
            proto.styleIdentifier.set( featureStyle
                    .map( fs -> fs.id() )
                    .orElse( null ) );
            return proto;
        });

        // super
        IStatus superResult = super.doWithCommit( monitor, info );
        
        // map bbox
        if (superResult.isOK()) {
            adaptMapBBox( monitor );
        }
        return superResult;
    }


    @Override
    protected void onSuccess() {
        super.onSuccess();
        featureStyle.ifPresent( fs -> fs.store() );
    }
    
    
    protected void adaptMapBBox( IProgressMonitor monitor ) throws Exception {
        if (!res.isPresent()) {
            res.set( AllResolver.instance().resInfo( layer.get(), monitor ).get() );
        }
        
        try {
            ReferencedEnvelope nativeLayerBounds = res.get().getBounds();
            if (nativeLayerBounds == null || nativeLayerBounds.isNull()) {
                // no or null bounds signals that schema has no geom or something
                log.info( "Resource has no bounds: " + res.get() );
                return;
            }
            
            ReferencedEnvelope layerBounds = nativeLayerBounds.transform( map.get().maxExtent().getCoordinateReferenceSystem(), true );
            log.info( "transformed: " + layerBounds );

            // already ok?
            if (map.get().maxExtent().equals( (BoundingBox)layerBounds )) {
                // XXX maybe consider any buffer so that bounds do not have to be *exactly* the same
            }
            // layer outside map?
            else if (!map.get().maxExtent().contains( (BoundingBox)layerBounds )) {
                UIThreadExecutor.syncFast( () -> {
                    new SimpleDialog()
                        .title.put( "Layer extent" )
                        .setContents( parent -> {
                            Label l = DefaultToolkit._adapt( new Label( parent, SWT.WRAP ), false, false );
                            l.setText( "<p>Layer extent exceeds current map.<br/>Do you want to expand map to include layer?</p>" );
                        })
                        .addNoAction()
                        .addYesAction( ev -> {
                            try {
                                ReferencedEnvelope mapExtent = map.get().maxExtent();
                                mapExtent.expandToInclude( layerBounds );
                                map.get().setMaxExtent( mapExtent );
                            }
                            catch (Exception e) {
                                StatusDispatcher.handleError( "Unable to set map max extent.", e );
                            }
                        })
                        .open();
                });
            }
            // map exceeds layer
            else {
                UIThreadExecutor.syncFast( () -> {
                    new SimpleDialog()
                        .title.put( "Map extent" )
                        .setContents( parent -> {
                            Label l = DefaultToolkit._adapt( new Label( parent, SWT.WRAP ), false, false );
                            l.setText( "<p>Map exceeds this layer's extent.<br/>Do you want to limit map to layer extent?</p>" );
                        })
                        .addNoAction()
                        .addYesAction( ev -> {
                            try {
                                map.get().setMaxExtent( layerBounds );
                            }
                            catch (Exception e) {
                                StatusDispatcher.handleError( "Unable to set map max extent.", e );
                            }
                        })
                        .open();
                });            
            }
        }
        catch (ProjectionException e) {
            log.warn( "", e );
            UIThreadExecutor.syncFast( () -> {
                new SimpleDialog()
                    .title.put( "Layer extent" )
                    .setContents( parent -> {
                        Label l = DefaultToolkit._adapt( new Label( parent, SWT.WRAP ), false, false );
                        l.setText( "Unable to determine layer extent.<br/>Map extent is not adapted to the extent<br/>of the new layer." );
                    })
                    .addCancelAction( "CLOSE" )
                    .open();
            });            
        }

        
//        // no max extent -> set 
//        monitor.subTask( Messages.get( "NewLayerOperation_checkingMaxExtent" ) );
//        if (map.get().maxExtent.get() == null) {
//            if (layerBounds != null && !layerBounds.isNull() && !layerBounds.isEmpty()) {
//                log.info( "### Map: maxExtent= " + layerBounds );
//                map.get().maxExtent.set( layerBounds );
//                // XXX set map status
//            }
//            else {
//                Display display = (Display)info.getAdapter( Display.class );
//                display.syncExec( new Runnable() {
//                    public void run() {
//                        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
//                        MessageBox box = new MessageBox( shell, SWT.OK );
//                        box.setText( "No layer bounds." );
//                        box.setMessage( "Layer has no bounding box.\n Max extent of the map could not be set.\nThis may lead to unspecified map behaviour." );
//                        box.open();
//                    }
//                });
//            }
//        }
//        // check if max extent contains layer
//        else {
//            try {
//                if (!layerBounds.isNull() && layerBounds.getMaxX() < Double.POSITIVE_INFINITY
//                        && !map.get().maxExtent.get().contains( (BoundingBox)layerBounds )) {
//                    ReferencedEnvelope bbox = new ReferencedEnvelope( layerBounds );
//                    bbox.expandToInclude( map.get().maxExtent.get() );
//                    final ReferencedEnvelope newMaxExtent = bbox;
//
//                    Display display = (Display)info.getAdapter( Display.class );
//                    display.syncExec( new Runnable() {
//                        public void run() {
//                            Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
//                            MessageBox box = new MessageBox( shell, SWT.YES | SWT.NO );
//                            box.setText( Messages.get( "NewLayerOperation_BBoxDialog_title" ) );
//                            box.setMessage( Messages.get( "NewLayerOperation_BBoxDialog_msg" ) );
//                            int answer = box.open();
//                            if (answer == SWT.YES) {
//                                map.setMaxExtent( newMaxExtent );
//                            }
//                        }
//                    });
//                }
//            }
//            catch (Exception e) {
//                log.warn( e.getLocalizedMessage(), e );
//            }
////        }
//        monitor.worked( 1 );
    }
    
}
