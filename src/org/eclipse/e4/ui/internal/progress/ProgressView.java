/*******************************************************************************
 * Copyright (c) 2003, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.ui.internal.progress;

import java.io.IOException;
import java.net.URL;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.part.Activator;
import org.eclipse.e4.ui.workbench.modeling.ESelectionService;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;

/**
 * The ProgressView is the class that shows the details of the current workbench
 * progress.
 */
public class ProgressView {

	DetailedProgressViewer viewer;

	Action cancelAction;

	Action clearAllAction;

	@Inject
	ESelectionService selectionService;
	
	@PostConstruct
	public void createPartControl(Composite parent, IEclipseContext context) {

		viewer = new DetailedProgressViewer(parent, SWT.MULTI | SWT.H_SCROLL);
		ContextInjectionFactory.inject(viewer, context);

		viewer.setComparator(ProgressManagerUtil.getProgressViewerComparator());

		viewer.getControl().setLayoutData(
				new GridData(SWT.FILL, SWT.FILL, true, true));

		initContentProvider();
		createClearAllAction();
		createCancelAction();
		initContextMenu();
		initPulldownMenu();
		initToolBar();
		
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				selectionService.setSelection(event.getSelection());
			}
			
		});
	}

	@Focus
	public void setFocus() {
		if (viewer != null) {
			viewer.setFocus();
		}
	}

	/**
	 * Sets the content provider for the viewer.
	 */
	protected void initContentProvider() {
		ProgressViewerContentProvider provider = new ProgressViewerContentProvider(
				viewer, true, true);
		viewer.setContentProvider(provider);
		viewer.setInput(ProgressManager.getInstance());
	}

	/**
	 * Initialize the context menu for the receiver.
	 */
	private void initContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		menuMgr.add(cancelAction);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				JobInfo info = getSelectedInfo();
				if (info == null) {
					return;
				}
			}
		});
		menuMgr.add(new Separator("seperator"));
		// TODO - what do we do for the site context menu?
		// getSite().registerContextMenu(menuMgr, viewer);
		viewer.getControl().setMenu(menu);
	}

	private void initPulldownMenu() {
		// IMenuManager menuMgr =
		// getViewSite().getActionBars().getMenuManager();
		// menuMgr.add(clearAllAction);
		// menuMgr.add(new ViewPreferencesAction() {
		// /*
		// * (non-Javadoc)
		// *
		// * @see
		// org.eclipse.ui.internal.preferences.ViewPreferencesAction#openViewPreferencesDialog()
		// */
		// public void openViewPreferencesDialog() {
		// new JobsViewPreferenceDialog(viewer.getControl().getShell())
		// .open();
		//
		// }
		// });
		//
	}

	private void initToolBar() {
		// IActionBars bars = getViewSite().getActionBars();
		// IToolBarManager tm = bars.getToolBarManager();
		// tm.add(clearAllAction);
	}

	/**
	 * Return the selected objects. If any of the selections are not JobInfos or
	 * there is no selection then return null.
	 * 
	 * @return JobInfo[] or <code>null</code>.
	 */
	private IStructuredSelection getSelection() {
		// If the provider has not been set yet move on.
		Object currentSelection = selectionService.getSelection();
		if (currentSelection instanceof IStructuredSelection) {
			return (IStructuredSelection) currentSelection;
		}
		return null;
	}

	/**
	 * Get the currently selected job info. Only return it if it is the only
	 * item selected and it is a JobInfo.
	 * 
	 * @return JobInfo
	 */
	JobInfo getSelectedInfo() {
		IStructuredSelection selection = getSelection();
		if (selection != null && selection.size() == 1) {
			JobTreeElement element = (JobTreeElement) selection
					.getFirstElement();
			if (element.isJobInfo()) {
				return (JobInfo) element;
			}
		}
		return null;
	}

	/**
	 * Create the cancel action for the receiver.
	 */
	private void createCancelAction() {
		cancelAction = new Action(ProgressMessages.ProgressView_CancelAction) {
			@Override
			public void run() {
				viewer.cancelSelection();
			}
		};

	}

	/**
	 * Create the clear all action for the receiver.
	 */
	private void createClearAllAction() {
		clearAllAction = new Action(
				ProgressMessages.ProgressView_ClearAllAction) {
			
			@Override
			public void run() {
				FinishedJobs.getInstance().clearAll();
			}
		};
		clearAllAction
				.setToolTipText(ProgressMessages.NewProgressView_RemoveAllJobsToolTip);
		try {
			URL url = FileLocator.toFileURL(Activator.getContext().getBundle()
					.getEntry("icons/full/elcl16/progress_remall.gif")); //$NON-NLS-1$
			ImageDescriptor id = ImageDescriptor.createFromURL(url);
			if (id != null) {
				clearAllAction.setImageDescriptor(id);
			}
			url = FileLocator.toFileURL(Activator.getContext().getBundle()
					.getEntry("icons/full/dlcl16/progress_remall.gif"));
			id = ImageDescriptor.createFromURL(url);
			if (id != null) {
				clearAllAction.setDisabledImageDescriptor(id);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return Returns the viewer.
	 */
	public DetailedProgressViewer getViewer() {
		return viewer;
	}

}
