/* 
 * polymap.org
 * Copyright (C) 2017, Falko Bräutigam. All rights reserved.
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
package org.polymap.p4.style;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.polymap.core.runtime.UIThreadExecutor.async;
import static org.polymap.core.runtime.event.TypeEventFilter.ifType;
import static org.polymap.core.ui.FormDataFactory.on;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.viewers.IElementComparer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerCell;

import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.Section;

import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.runtime.event.EventManager;
import org.polymap.core.runtime.i18n.IMessages;
import org.polymap.core.style.Messages;
import org.polymap.core.style.model.FeatureStyle;
import org.polymap.core.style.model.Style;
import org.polymap.core.style.model.StyleComposite;
import org.polymap.core.style.model.StyleGroup;
import org.polymap.core.style.model.StylePropertyChange;
import org.polymap.core.style.model.StylePropertyValue;
import org.polymap.core.style.ui.StylePropertyField;
import org.polymap.core.style.ui.StylePropertyFieldSite;
import org.polymap.core.style.ui.UIOrderComparator;
import org.polymap.core.ui.ColumnDataFactory;
import org.polymap.core.ui.ColumnLayoutFactory;
import org.polymap.core.ui.FormLayoutFactory;
import org.polymap.core.ui.HSLColor;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.toolkit.IPanelSection;
import org.polymap.rhei.batik.toolkit.md.ActionProvider;
import org.polymap.rhei.batik.toolkit.md.CheckboxActionProvider;
import org.polymap.rhei.batik.toolkit.md.MdListViewer;
import org.polymap.rhei.batik.toolkit.md.MdToolbar2;
import org.polymap.rhei.batik.toolkit.md.MdToolkit;

import org.polymap.model2.Property;
import org.polymap.model2.runtime.PropertyInfo;
import org.polymap.p4.P4Plugin;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 * @author Steffen Stundzig
 */
public abstract class StyleEditor<I extends StyleEditorInput> {

    private static final Log log = LogFactory.getLog( StyleEditor.class );

    private final static IMessages      i18nField = Messages.forPrefix( "Field" );

    public static final String          TOOLBAR = StyleEditor.class.getSimpleName();
    
    protected I                         editorInput;

    protected FeatureStyle              featureStyle;

    protected MdListViewer              list;

    protected MdToolbar2                toolbar;
    
    protected IPanelSection             editorSection;

    protected List<StylePropertyField>  fields = new ArrayList();

    protected MdToolkit                 tk;
    

    public StyleEditor( I editorInput ) {
        this.editorInput = editorInput;
        featureStyle = P4Plugin.styleRepo().featureStyle( editorInput.styleIdentifier.get() )
                .orElseThrow( () -> new IllegalStateException( "Layer has no style.") );
    }

    
    public void dispose() {
        EventManager.instance().unsubscribe( this );
    }

    
    public void store() {
        featureStyle.store();
    }
    
    
    protected abstract void enableSubmit( boolean enabled );


    @EventHandler( display=true, delay=100 )
    protected void featureStyleCanged( List<StylePropertyChange> evs ) {
        boolean enabled = fields.stream().allMatch( field -> field.isValid() );
        enableSubmit( enabled );
    }


    public void createContents( Composite parent, @SuppressWarnings( "hiding" ) MdToolkit tk ) {
        this.tk = tk;
        
        // toolbar
        toolbar = tk.createToolbar( parent, SWT.TOP );
        
        // style list
        list = tk.createListViewer( parent, SWT.SINGLE, SWT.FULL_SELECTION );
        list.setContentProvider( new FeatureStyleContentProvider() );
        list.iconProvider.set( new FeatureStyleLabelProvider( tk ) );
        list.firstLineLabelProvider.set( new FeatureStyleLabelProvider( tk ) );
        //list.secondLineLabelProvider.set( new FeatureStyleDescriptionProvider( tk() ) );
        list.secondSecondaryActionProvider.set( new ActiveActionProvider() );
        list.firstSecondaryActionProvider.set( new RemoveActionProvider() );
        list.setComparer( new StyleIdentityComparer() );
        list.addSelectionChangedListener( ev -> {
            try {
                Optional<?> elm = org.polymap.core.ui.SelectionAdapter.on( ev.getSelection() ).first();
                if (!elm.isPresent()) {
                    editorSection.setTitle( "" );
                    UIUtils.disposeChildren( editorSection.getBody() );
                }
                else if (elm.get() instanceof StyleGroup) {
                    // ...
                }
                else if (elm.get() instanceof Style) {
                    createStyleEditor( editorSection.getBody(), (Style)elm.get() );   
                }
            }
            catch (Exception e) {
                log.warn( "", e );
            }
        });
        list.setInput( featureStyle );
        list.expandAll();
        if (!featureStyle.members().isEmpty()) {
            async( () -> list.setSelection( new StructuredSelection( featureStyle.members().iterator().next() ) ) );
        }
        
        //
        editorSection = tk.createPanelSection( parent, "" );
        
        // listen to StylePropertyChange
        EventManager.instance().subscribe( this, ifType( StylePropertyChange.class, 
                ev -> ev.getSource() == featureStyle ) );
        
        // layout
        parent.setLayout( FormLayoutFactory.defaults().margins( 0, 0 ).spacing( 0 ).create() );
        on( toolbar.getControl() ).left( 0, 3 ).right( 100, -3 ).top( 0 );
        on( list.getControl() ).fill().top( toolbar.getControl() ).bottom( toolbar.getControl(), 150 );
        on( editorSection.getControl() ).fill().top( list.getControl(), 8 );
    }
    
    
    protected void createStyleEditor( Composite parent, Style style ) {
        editorSection.setTitle( defaultString( style.title.get(), "Style settings" ) );
        UIUtils.disposeChildren( parent );
        
        parent.setLayout( ColumnLayoutFactory.defaults().columns( 1, 1 ).margins( 0, 0 ).spacing( 8 ).create() );
        
        Composite headLine = tk.createComposite( parent, SWT.NONE );
        headLine.setLayout( FormLayoutFactory.defaults().margins( 0, 0 ).spacing( 5 ).create() );
        
        // title
        Text title = new Text( headLine, SWT.BORDER );
        //title.setBackground( bg );
        title.setText( style.title.get() );
        title.addModifyListener( new ModifyListener() {
            @Override
            public void modifyText( ModifyEvent ev ) {
                // XXX sanitize user input string (?)
                style.title.set( title.getText() );
                list.update( style, null );
                editorSection.setTitle( style.title.get() );
            }
        });
        title.setToolTipText( i18nField.get( "styleNameTooltip" ) );
        
        // description
        Text descr = new Text( headLine, SWT.BORDER );
        //descr.setBackground( bg );
        descr.setForeground( new HSLColor( parent.getForeground() ).adjustLuminance( 30 ).toSWT() );
        descr.setText( style.description.get() );
        descr.addModifyListener( new ModifyListener() {
            @Override
            public void modifyText( ModifyEvent ev ) {
                // XXX sanitize user input string (?)
                style.description.set( descr.getText() );
                list.update( style, null );
            }
        } );
        descr.setToolTipText( i18nField.get( "styleDescriptionTooltip" ) );

        on( title ).left( 0 ).right( 30 );
        on( descr ).left( title, 3 ).right( 100 );

        fields.clear();
        createEditorFields( parent, style, 0 );
        parent.layout( true, true );
        //parent.getParent().getParent().getParent().layout( true, true );
        
       // site().layout( true );
    }

    
    protected abstract StylePropertyFieldSite createFieldSite( Property<StylePropertyValue> prop );
    

    protected void createEditorFields( Composite parent, StyleComposite style, int level ) {
        SortedSet<PropertyInfo<?>> sorted = new TreeSet( new UIOrderComparator() );
        sorted.addAll( style.info().getProperties());
        
        for (PropertyInfo<?> propInfo : sorted) {

            // StylePropertyValue
            if (StylePropertyValue.class.isAssignableFrom( propInfo.getType() )) {
                Property<StylePropertyValue> prop = (Property<StylePropertyValue>)propInfo.get( style );
                StylePropertyField field = new StylePropertyField( createFieldSite( prop ) );
                fields.add( field );
                Control control = field.createContents( parent );

                // the widthHint is a minimal width; without the fields expand the enclosing section
                control.setLayoutData( ColumnDataFactory.defaults().widthHint( 100 ).create() );
            }
            
            // StyleComposite
            else if (StyleComposite.class.isAssignableFrom( propInfo.getType() )) {
                Section section = tk.createSection( parent, 
                        i18nField.get( propInfo.getDescription().orElse( propInfo.getName() ) ), 
                        ExpandableComposite.TWISTIE, Section.SHORT_TITLE_BAR, Section.FOCUS_TITLE, SWT.BORDER );
                section.setToolTipText( i18nField.get( propInfo.getDescription().orElse( propInfo.getName() ) + "Tooltip" ) );
                section.setExpanded( false );                

                section.setBackground( new HSLColor( parent.getBackground() )
                        .adjustSaturation( -10f ).adjustLuminance( -4f ).toSWT() );
                
                ((Composite)section.getClient()).setLayout( ColumnLayoutFactory.defaults()
                        .columns( 1, 1 ).margins( 0, 0, 5, 0 ).spacing( 5 ).create() );

                createEditorFields( 
                        (Composite)section.getClient(), 
                        ((Property<StyleComposite>)propInfo.get( style )).get(), level + 1 );
            }
        }
    }

    /**
     * 
     */
    protected class ActiveActionProvider
            extends CheckboxActionProvider {
        
        public ActiveActionProvider() {
            super( P4Plugin.images().svgImage( "eye.svg", P4Plugin.TOOLBAR_ICON_CONFIG ), P4Plugin.images().svgImage( "eye-off.svg", P4Plugin.TOOLBAR_ICON_CONFIG ) );
        }
        
        @Override
        protected boolean initSelection( MdListViewer viewer, Object elm ) {
            return ((Style)elm).active.get();
        }

        @Override
        protected void onSelection( MdListViewer viewer, Object elm, boolean selected ) {
            ((Style)elm).active.set( isSelected( elm ) );
        }
    }
    
    
    /**
     * 
     */
    protected class RemoveActionProvider
            extends ActionProvider {

        @Override
        public void perform( MdListViewer viewer, Object elm ) {
            ((Style)elm).removed.set( true );
            //list.getTree().deselectAll();
            list.remove( elm );
            //list.getTree().layout( true );            
        }

        @Override
        public void update( ViewerCell cell ) {
            cell.setImage( P4Plugin.images().svgImage( "delete.svg", P4Plugin.TOOLBAR_ICON_CONFIG ) );
        }
    }
    
    
    /**
     * 
     */
    protected class StyleIdentityComparer
            implements IElementComparer {

        @Override
        public int hashCode( Object elm ) {
            return elm.hashCode();
        }

        @Override
        public boolean equals( Object a, Object b ) {
            return a == b;
        }
    }

}
