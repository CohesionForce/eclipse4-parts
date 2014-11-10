/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Gunnar Wagenknecht - fix for bug 21756 [PropertiesView] property view sorting
 *******************************************************************************/

package org.eclipse.e4.views.properties;

import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.part.Page;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * The standard implementation of property sheet page which presents
 * a table of property names and values obtained from the current selection
 * in the active workbench part.
 * <p>
 * This page obtains the information about what properties to display from
 * the current selection (which it tracks).
 * </p>
 * <p>
 * The model for this page is a hierarchy of <code>IPropertySheetEntry</code>.
 * The page may be configured with a custom model by setting the root entry.
 * <p>
 * If no root entry is set then a default model is created which uses the
 * <code>IPropertySource</code> interface to obtain the properties of
 * the current selection. This requires that the selected objects provide an
 * <code>IPropertySource</code> adapter (or implement
 * <code>IPropertySource</code> directly). This restiction can be overcome
 * by providing this page with an <code>IPropertySourceProvider</code>. If
 * supplied, this provider will be used by the default model to obtain a
 * property source for the current selection
 * </p>
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @see IPropertySource
 * @noextend This class is not intended to be subclassed by clients.
 */
public class PropertySheetPage extends Page implements IPropertySheetPage {

    private PropertySheetViewer viewer;
    
    private PropertySheetSorter sorter;

    private IPropertySheetEntry rootEntry;

    private IPropertySourceProvider provider;

    /**
     * Creates a new property sheet page.
     */
    public PropertySheetPage() {
        super();
    }

    /* (non-Javadoc)
     * Method declared on <code>IPage</code>.
     */
    public void createControl(Composite parent) {
        // create a new viewer
        viewer = new PropertySheetViewer(parent);
        viewer.setSorter(sorter);
        
        // set the model for the viewer
        if (rootEntry == null) {
            // create a new root
            PropertySheetEntry root = new PropertySheetEntry();
            if (provider != null) {
				// set the property source provider
                root.setPropertySourceProvider(provider);
			}
            rootEntry = root;
        }
        viewer.setRootEntry(rootEntry);
        initDragAndDrop();

    }

    /**
     * The <code>PropertySheetPage</code> implementation of this <code>IPage</code> method
     * disposes of this page's entries.
     */
    public void dispose() {
        super.dispose();
        if (rootEntry != null) {
            rootEntry.dispose();
            rootEntry = null;
        }
    }

    /* (non-Javadoc)
     * Method declared on IPage (and Page).
     */
    public Control getControl() {
        if (viewer == null) {
			return null;
		}
        return viewer.getControl();
    }

    /**
     * Adds drag and drop support.
     */
    protected void initDragAndDrop() {
        int operations = DND.DROP_COPY;
        Transfer[] transferTypes = new Transfer[] { TextTransfer.getInstance() };
        DragSourceListener listener = new DragSourceAdapter() {
            public void dragSetData(DragSourceEvent event) {
                performDragSetData(event);
            }

            public void dragFinished(DragSourceEvent event) {
                //Nothing to do here
            }
        };
        DragSource dragSource = new DragSource(
                viewer.getControl(), operations);
        dragSource.setTransfer(transferTypes);
        dragSource.addDragListener(listener);
    }

    /**
     * The user is attempting to drag.  Add the appropriate
     * data to the event.
     * @param event The event sent from the drag and drop support.
     */
    void performDragSetData(DragSourceEvent event) {
        // Get the selected property
        IStructuredSelection selection = (IStructuredSelection) viewer
                .getSelection();
        if (selection.isEmpty()) {
			return;
		}
        // Assume single selection
        IPropertySheetEntry entry = (IPropertySheetEntry) selection
                .getFirstElement();

        // Place text as the data
        StringBuffer buffer = new StringBuffer();
        buffer.append(entry.getDisplayName());
        buffer.append("\t"); //$NON-NLS-1$
        buffer.append(entry.getValueAsString());

        event.data = buffer.toString();
    }

    /**
     * Updates the model for the viewer.
     * <p>
     * Note that this means ensuring that the model reflects the state
     * of the current viewer input.
     * </p>
     */
    public void refresh() {
        if (viewer == null) {
			return;
		}
        // calling setInput on the viewer will cause the model to refresh
        viewer.setInput(viewer.getInput());
    }

	public void selectionChanged(
			ISelection selected,
			MPart part) {

		if(selected == null)
			return;
		
        if (viewer == null) {
			return;
		}
        
        if (viewer.getControl().isDisposed())
        {
        	return;
        }

        // change the viewer input since the workbench selection has changed.
        if (selected instanceof IStructuredSelection) {
            viewer.setInput(((IStructuredSelection) selected).toArray());
        } else {
        	viewer.setInput(selected);
        }
    }

    /**
     * Sets focus to a part in the page.
     */
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    /**
     * Sets the given property source provider as
     * the property source provider.
     * <p>
     * Calling this method is only valid if you are using
     * this page's default root entry.
     * </p>
     * @param newProvider the property source provider
     */
    public void setPropertySourceProvider(IPropertySourceProvider newProvider) {
        provider = newProvider;
        if (rootEntry instanceof PropertySheetEntry) {
            ((PropertySheetEntry) rootEntry)
                    .setPropertySourceProvider(provider);
            // the following will trigger an update
            viewer.setRootEntry(rootEntry);
        }
    }

    /**
     * Sets the given entry as the model for the page.
     *
     * @param entry the root entry
     */
    public void setRootEntry(IPropertySheetEntry entry) {
        rootEntry = entry;
        if (viewer != null) {
			// the following will trigger an update
            viewer.setRootEntry(rootEntry);
		}
    }

    /**
	 * Sets the sorter used for sorting categories and entries in the viewer
	 * of this page.
	 * <p>
	 * The default sorter sorts categories and entries alphabetically.
	 * </p>
	 * @param sorter the sorter to set (<code>null</code> will reset to the
	 * default sorter)
     * @since 3.1
	 */
	protected void setSorter(PropertySheetSorter sorter) {
		this.sorter = sorter;
        if (viewer != null) {
        	viewer.setSorter(sorter);
        	
        	// the following will trigger an update
        	if(null != viewer.getRootEntry()) {
				viewer.setRootEntry(rootEntry);
			}
        }
	}

}
