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
package org.polymap.p4.data.importer.geopaparazzi;

import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyDescriptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.vividsolutions.jts.geom.Geometry;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.polymap.rhei.table.DefaultFeatureTableColumn;
import org.polymap.rhei.table.FeatureTableViewer;
import org.polymap.rhei.table.LazyFeatureContentProvider;

/**
 * Provides default columns for all {@link Feature} properties. Uses an
 * {@link LazyFeatureContentProvider}, which supports {@link FeatureSource} and
 * {@link FeatureCollection} inputs.
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class GPFeatureTableViewer
        extends FeatureTableViewer {

    private static Log log = LogFactory.getLog( GPFeatureTableViewer.class );
    

    public GPFeatureTableViewer( Composite parent, SimpleFeatureType schema ) {
        super( parent, /* SWT.VIRTUAL*/  SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION );

//        setContentProvider( new LazyFeatureContentProvider() );

        for (PropertyDescriptor prop : schema.getDescriptors()) {
            if (Geometry.class.isAssignableFrom( prop.getType().getBinding() )) {
                // skip Geometry
            }
            else {
                DefaultFeatureTableColumn column = new DefaultFeatureTableColumn( prop );
                column.setWeight( 1, 65 );
                addColumn( column );
            }
        }
    }

}
