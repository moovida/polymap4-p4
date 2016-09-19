/* 
 * Copyright (C) 2015, the @authors. All rights reserved.
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
package org.polymap.p4.data.importer;

import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import org.polymap.core.runtime.config.Concern;
import org.polymap.core.runtime.config.Config2;
import org.polymap.core.runtime.config.Configurable;
import org.polymap.core.runtime.config.DefaultBoolean;
import org.polymap.core.runtime.config.DefaultString;
import org.polymap.core.runtime.config.Mandatory;

import org.polymap.rhei.batik.toolkit.IPanelToolkit;

import org.polymap.p4.P4Panel;

/**
 * 
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public abstract class ImporterPrompt
        extends Configurable {
    
    public enum Severity {
        /**
         * No action required, just an info. No special visual representation
         * currently.
         */
        INFO,
        /**
         * User should verify. No special visual representation currently.
         */
        VERIFY,
        /**
         * User action required because user MUST input something or given value is
         * invalid. A required prompt blocks the import if it is not
         * {@link ImporterPrompt#ok}. In this case prompt and importer are visually
         * highlighted.
         */
        REQUIRED;
    }

    /**
     * The severity of this prompt. Defaults to {@link Severity#INFO}. Should be
     * {@link Severity#REQUIRED} if user MUST input something or given value is
     * invalid.
     */
    @Mandatory
    @Concern( ConfigChangeEvent.Fire.class )
    public Config2<ImporterPrompt,Severity> severity;

    /** 
     * Short summary of the idea of this prompt. 
     */
    @Mandatory
    @DefaultString( "" )
    @Concern( ConfigChangeEvent.Fire.class )
    public Config2<ImporterPrompt,String>   summary;

    /**
     * What to decide here? What is the effect and consequences? Are there defaults
     * or 'best practices'?
     */
    @Concern( ConfigChangeEvent.Fire.class )
    public Config2<ImporterPrompt,String>   description;

    /**
     * The default/current selection or value as human readable string.
     */
    @Concern( ConfigChangeEvent.Fire.class )
    public Config2<ImporterPrompt,String>   value;

    /**
     * The {@link #extendedUI} of this prompt should set it 'true' when this prompt
     * has been changed and the importer is allowed to verify/execute. Set to false
     * only if the user input is invalid.
     */
    @Mandatory
    @DefaultBoolean( false )
    @Concern( ConfigChangeEvent.Fire.class )
    public Config2<ImporterPrompt,Boolean>  ok;
    
    @Concern( ConfigChangeEvent.Fire.class )
    public Config2<ImporterPrompt,PromptUIBuilder> extendedUI;
    

    protected ImporterPrompt() {
        severity.set( Severity.INFO );
    }

    
    abstract ImporterContext context();

    
    /**
     * 
     */
    public static interface PromptUIBuilder {
        
        public static final int         DEFAULT_WIDTH = P4Panel.SIDE_PANEL_WIDTH2 - 50;
        
        /**
         * 
         *
         * @param prompt
         * @param parent The parent with {@link FillLayout} set. Change layout as needed.
         * @param tk The toolkit to create prompt UI control with.
         */
        public void createContents( ImporterPrompt prompt, Composite parent, IPanelToolkit tk  );
        
        
        /**
         * Submit changes of the UI to the internal state of the
         * {@link ImporterPrompt}. This should update {@link ImporterPrompt#ok} and
         * {@link ImporterPrompt#value} appropriate.
         *
         * @param prompt
         * @throws Exception
         */
        public void submit( ImporterPrompt prompt );
        
    }

}