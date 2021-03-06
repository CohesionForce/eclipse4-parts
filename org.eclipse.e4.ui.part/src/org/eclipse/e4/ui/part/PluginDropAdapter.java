/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.ui.part;

import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.ViewerDropAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TransferData;

/**
 * Adapter for adding handling of the <code>PluginTransfer</code> drag and drop
 * transfer type to a drop action.
 * <p>
 * This class may be instantiated or subclassed.
 * </p>
 */
public class PluginDropAdapter extends ViewerDropAdapter {
    /**
     * The extension point attribute that defines the drop action class.
     */
    public static final String ATT_CLASS = "class";//$NON-NLS-1$

    /**
     * The current transfer data, or <code>null</code> if none.
     */
    private TransferData currentTransfer;

    /** 
     * Creates a plug-in drop adapter for the given viewer.
     *
     * @param viewer the viewer
     */
    public PluginDropAdapter(StructuredViewer viewer) {
        super(viewer);
    }

    /* (non-Javadoc)
     * Method declared on DropTargetAdapter.
     * The user has dropped something on the desktop viewer.
     */
    public void drop(DropTargetEvent event) {
            if (PluginTransfer.getInstance().isSupportedType(
                    event.currentDataType)) {
            } else {
                super.drop(event);
            }
    }

    /**
     * Returns the current transfer.
     */
    protected TransferData getCurrentTransfer() {
        return currentTransfer;
    }

    /**
     * @see ViewerDropAdapter#performDrop
     */
    public boolean performDrop(Object data) {
        //should never be called, since we override the drop() method.
        return false;
    }

    /**
     * The <code>PluginDropAdapter</code> implementation of this
     * <code>ViewerDropAdapter</code> method is used to notify the action that some
     * aspect of the drop operation has changed. Subclasses may override.
     */
    public boolean validateDrop(Object target, int operation,
            TransferData transferType) {
        currentTransfer = transferType;
        if (currentTransfer != null
                && PluginTransfer.getInstance()
                        .isSupportedType(currentTransfer)) {
            //plugin cannot be loaded without the plugin data
            return true;
        }
        return false;
    }
}
