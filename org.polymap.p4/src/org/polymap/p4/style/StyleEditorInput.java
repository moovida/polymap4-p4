/*
 * polymap.org Copyright (C) 2016-2017, the @authors. All rights reserved.
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
package org.polymap.p4.style;

import org.polymap.core.runtime.config.Config;
import org.polymap.core.runtime.config.Configurable;
import org.polymap.core.runtime.config.Immutable;
import org.polymap.core.runtime.config.Mandatory;

/**
 * Container to transport relevant data to the {@link StyleEditor}.
 * 
 * @author Steffen Stundzig
 * @author Falko Bräutigam
 */
public abstract class StyleEditorInput
        extends Configurable {

    @Mandatory
    @Immutable
    public Config<String>       styleIdentifier;

}
