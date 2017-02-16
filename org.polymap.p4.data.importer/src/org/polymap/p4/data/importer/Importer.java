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

import org.eclipse.core.runtime.IProgressMonitor;

import org.polymap.rhei.batik.toolkit.IPanelToolkit;

/**
 * 
 * @see ContextIn
 * @see ContextOut
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public interface Importer {

    public ImporterSite site();
    
    /**
     * Initializes this Importer. This should set {@link ImporterSite#summary},
     * {@link ImporterSite#description} and {@link ImporterSite#icon}. This is called
     * right after contruction.
     *
     * @param site
     * @param monitor
     * @throws Exception
     */
    public void init( ImporterSite site, IProgressMonitor monitor ) throws Exception;
    
    /**
     * Creates the initial prompts of this Importer. This is called once after
     * {@link #init(ImporterSite, IProgressMonitor)}.
     *
     * @param monitor
     * @throws Exception
     */
    public void createPrompts( IProgressMonitor monitor ) throws Exception;    
    
    
    /**
     * Verifies the data/context and the state of the prompts. This is called when a
     * prompt has changed its status. This method may update the prompts, should set
     * {@link ImporterSite#ok} and prepare contents for the
     * {@link #createResultViewer(Composite)}.
     *
     * @param monitor The monitor has been
     *        {@link IProgressMonitor#beginTask(String, int)} already with a default
     *        task name and UNKNOWN size. The method implementation may overwrite
     *        this if necessary, or just call
     *        {@link IProgressMonitor#subTask(String)} and
     *        {@link IProgressMonitor#worked(int)} to report progress. Should be
     *        frequently checked for cancelation.
     */
    public void verify( IProgressMonitor monitor );

    /**
     * Creates a preview of the imported data.
     * <p/>
     * This method must not calculate result or perform any other long running task.
     * Verification and calculation should be done by
     * {@link #verify(IProgressMonitor)}.
     *
     * @param parent The parent of the new controls. Has vertical {@link FillLayout}
     *        set. Change this as necessary.
     * @param toolkit The toolkit to use to create new controls.
     */
    public void createResultViewer( Composite parent, IPanelToolkit toolkit );

    
    /**
     * Collects the results of this importer in {@link ContextOut}. This method is
     * called only if {@link #verify(IProgressMonitor)} did set status ok.
     * 
     * @param monitor The monitor to report progress to. Should be frequently checked
     *        for cancelation.
     */
    public void execute( IProgressMonitor monitor ) throws Exception;
    
}
