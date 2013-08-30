/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.e4.ui.part;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.core.commands.common.EventManager;
import org.eclipse.core.runtime.Platform;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.e4.ui.workbench.modeling.ESelectionService;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.SubActionBars;
import org.eclipse.ui.internal.WorkbenchPage;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.util.Util;
import org.osgi.service.event.Event;

/**
 * Abstract superclass of all multi-page workbench views.
 * <p>
 * Within the workbench there are many views which track the active part. If a
 * part is activated these views display some properties for the active part. A
 * simple example is the <code>Outline View</code>, which displays the outline
 * for the active editor. To avoid loss of context when part activation changes,
 * these views may implement a multi-page approach. A separate page is
 * maintained within the view for each source view. If a part is activated the
 * associated page for the part is brought to top. If a part is closed the
 * associated page is disposed. <code>PageBookView</code> is a base
 * implementation for multi page views.
 * </p>
 * <p>
 * <code>PageBookView</code>s provide an <code>IPageSite</code> for each of
 * their pages. This site is supplied during the page's initialization. The page
 * may supply a selection provider for this site. <code>PageBookView</code>s
 * deal with these selection providers in a similar way to a workbench page's
 * <code>SelectionService</code>. When a page is made visible, if its site has a
 * selection provider, then changes in the selection are listened for and the
 * current selection is obtained and fired as a selection change event.
 * Selection changes are no longer listened for when a page is made invisible.
 * </p>
 * <p>
 * This class should be subclassed by clients wishing to define new multi-page
 * views.
 * </p>
 * <p>
 * When a <code>PageBookView</code> is created the following methods are
 * invoked. Subclasses must implement these.
 * <ul>
 * <li><code>createDefaultPage</code> - called to create a default page for the
 * view. This page is displayed when the active part in the workbench does not
 * have a page.</li>
 * <li><code>getBootstrapPart</code> - called to determine the active part in
 * the workbench. A page will be created for this part</li>
 * </ul>
 * </p>
 * <p>
 * When a part is activated the base implementation does not know if a page
 * should be created for the part. Therefore, it delegates creation to the
 * subclass.
 * <ul>
 * <li><code>isImportant</code> - called when a workbench part is activated.
 * Subclasses return whether a page should be created for the new part.</li>
 * <li><code>doCreatePage</code> - called to create a page for a particular part
 * in the workbench. This is only invoked when <code>isImportant</code> returns
 * </code>true</code>.</li>
 * </ul>
 * </p>
 * <p>
 * When a part is closed the base implementation will destroy the page
 * associated with the particular part. The page was created by a subclass, so
 * the subclass must also destroy it. Subclasses must implement these.
 * <ul>
 * <li><code>doDestroyPage</code> - called to destroy a page for a particular
 * part in the workbench.</li>
 * </ul>
 * </p>
 */
public abstract class PageBookView {
	/**
	 * The pagebook control, or <code>null</code> if not initialized.
	 */
	private PageBook book;

	/**
	 * The page record for the default page.
	 */
	private PageRec defaultPageRec;

	/**
	 * Map from parts to part records (key type: <code>IWorkbenchPart</code>;
	 * value type: <code>PartRec</code>).
	 */
	private Map mapPartToRec = new HashMap();

	/**
	 * Map from pages to view sites Note that view sites were not added to page
	 * recs to avoid breaking binary compatibility with previous builds
	 */
	private Map mapPageToSite = new HashMap();

	/**
	 * Map from pages to the number of pageRecs actively associated with a page.
	 */
	private Map mapPageToNumRecs = new HashMap();

	/**
	 * The page rec which provided the current page or <code>null</code>
	 */
	private PageRec activeRec;

	/**
	 * If the part is hidden (usually an editor) then store it so we can
	 * continue to track it when it becomes visible.
	 */
	private MPart hiddenPart = null;

	/**
	 * Selection provider for this view's site
	 */
	// private SelectionProvider selectionProvider = new SelectionProvider();

	/**
	 * A data structure used to store the information about a single page within
	 * a pagebook view.
	 */
	protected static class PageRec {

		/**
		 * The part.
		 */
		public MPart part;

		/**
		 * The page.
		 */
		public IPage page;

		/**
		 * Creates a new page record initialized to the given part and page.
		 * 
		 * @param part
		 * @param page
		 */
		public PageRec(MPart part, IPage page) {
			this.part = part;
			this.page = page;
		}

		/**
		 * Disposes of this page record by <code>null</code>ing its fields.
		 */
		public void dispose() {
			part = null;
			page = null;
		}
	}

	@SuppressWarnings("restriction")
	@Inject
	@Optional
	public void partActivation(
			@UIEventTopic(UIEvents.UILifeCycle.ACTIVATE) Event event,
			MApplication application) {

		MPart activePart = (MPart) event
				.getProperty(UIEvents.EventTags.ELEMENT);
		partActivated(activePart);
	}

	@Inject
	@Optional
	public void partHidden(
			@UIEventTopic(UIEvents.UIElement.TOBERENDERED) Event event,
			MApplication application) {
		Object object = event.getProperty(UIEvents.EventTags.ELEMENT);
		if (object instanceof MPart) {
			MPart part = (MPart) object;
			boolean visible = (Boolean) event
					.getProperty(UIEvents.EventTags.NEW_VALUE);
			if (visible) {
				partVisible(part);
			} else {
				partHidden(part);
			}
		}
	}

	/**
	 * Creates a new pagebook view.
	 */
	protected PageBookView() {
		super();
	}

	/**
	 * Creates and returns the default page for this view.
	 * <p>
	 * Subclasses must implement this method.
	 * </p>
	 * <p>
	 * Subclasses must call initPage with the new page (if it is an
	 * <code>IPageBookViewPage</code>) before calling createControl on the page.
	 * </p>
	 * 
	 * @param book
	 *            the pagebook control
	 * @return the default page
	 */
	protected abstract IPage createDefaultPage(PageBook book);

	/**
	 * Creates a page for a given part. Adds it to the pagebook but does not
	 * show it.
	 * 
	 * @param part
	 *            The part we are making a page for.
	 * @return IWorkbenchPart
	 */
	private PageRec createPage(MPart part) {
		PageRec rec = doCreatePage(part);
		if (rec != null) {
			mapPartToRec.put(part, rec);
			preparePage(rec);
		}
		return rec;
	}

	/**
	 * Prepares the page in the given page rec for use in this view.
	 * 
	 * @param rec
	 */
	private void preparePage(PageRec rec) {
		Integer count;

		if (!doesPageExist(rec.page)) {
			// mapPageToSite.put(rec.page, site);
			count = new Integer(0);
		} else {
			count = ((Integer) mapPageToNumRecs.get(rec.page));
		}

		mapPageToNumRecs.put(rec.page, new Integer(count.intValue() + 1));
	}

	/**
	 * Initializes the given page with a page site.
	 * <p>
	 * Subclasses should call this method after the page is created but before
	 * creating its controls.
	 * </p>
	 * <p>
	 * Subclasses may override
	 * </p>
	 * 
	 * @param page
	 *            The page to initialize
	 */
	protected void initPage(IPageBookViewPage page) {
		try {
			page.init();
		} catch (PartInitException e) {
			WorkbenchPlugin.log(getClass(), "initPage", e); //$NON-NLS-1$
		}
	}

	/**
	 * The <code>PageBookView</code> implementation of this
	 * <code>IWorkbenchPart</code> method creates a <code>PageBook</code>
	 * control with its default page showing. Subclasses may extend.
	 */
	@PostConstruct
	public void createPartControl(Composite parent) {

		// Create the page book.
		book = new PageBook(parent, SWT.NONE);

		// Create the default page rec.
		IPage defaultPage = createDefaultPage(book);
		defaultPageRec = new PageRec(null, defaultPage);
		preparePage(defaultPageRec);

		// Show the default page
		showPageRec(defaultPageRec);

		// Listen to part activation events.

		showBootstrapPart();
	}

	/**
	 * The <code>PageBookView</code> implementation of this
	 * <code>IWorkbenchPart</code> method cleans up all the pages. Subclasses
	 * may extend.
	 */
	public void dispose() {

		// Deref all of the pages.
		activeRec = null;
		if (defaultPageRec != null) {
			// check for null since the default page may not have
			// been created (ex. perspective never visible)
			defaultPageRec.page.dispose();
			Object site = mapPageToSite.remove(defaultPageRec.page);
			mapPageToNumRecs.remove(defaultPageRec.page);

			defaultPageRec = null;
		}
		Map clone = (Map) ((HashMap) mapPartToRec).clone();
		Iterator itr = clone.values().iterator();
		while (itr.hasNext()) {
			PageRec rec = (PageRec) itr.next();
			removePage(rec);
		}

	}

	/**
	 * Creates a new page in the pagebook for a particular part. This page will
	 * be made visible whenever the part is active, and will be destroyed with a
	 * call to <code>doDestroyPage</code>.
	 * <p>
	 * Subclasses must implement this method.
	 * </p>
	 * <p>
	 * Subclasses must call initPage with the new page (if it is an
	 * <code>IPageBookViewPage</code>) before calling createControl on the page.
	 * </p>
	 * 
	 * @param part
	 *            the input part
	 * @return the record describing a new page for this view
	 * @see #doDestroyPage
	 */
	protected abstract PageRec doCreatePage(MPart part);

	/**
	 * Destroys a page in the pagebook for a particular part. This page was
	 * returned as a result from <code>doCreatePage</code>.
	 * <p>
	 * Subclasses must implement this method.
	 * </p>
	 * 
	 * @param part
	 *            the input part
	 * @param pageRecord
	 *            a page record for the part
	 * @see #doCreatePage
	 */
	protected abstract void doDestroyPage(MPart part, PageRec pageRecord);

	/**
	 * Returns true if the page has already been created.
	 * 
	 * @param page
	 *            the page to test
	 * @return true if this page has already been created.
	 */
	protected boolean doesPageExist(IPage page) {
		return mapPageToNumRecs.containsKey(page);
	}

	/**
	 * The <code>PageBookView</code> implementation of this
	 * <code>IAdaptable</code> method delegates to the current page, if it
	 * implements <code>IAdaptable</code>.
	 */
	public Object getAdapter(Class key) {
		// delegate to the current page, if supported
		IPage page = getCurrentPage();
		Object adapter = Util.getAdapter(page, key);
		if (adapter != null) {
			return adapter;
		}
		// if the page did not find the adapter, look for one provided by
		// this view before delegating to super.
		adapter = getViewAdapter(key);
		if (adapter != null) {
			return adapter;
		}
		return null;
	}

	/**
	 * Returns an adapter of the specified type, as provided by this view (not
	 * the current page), or <code>null</code> if this view does not provide an
	 * adapter of the specified adapter.
	 * <p>
	 * The default implementation returns <code>null</code>. Subclasses may
	 * override.
	 * </p>
	 * 
	 * @param adapter
	 *            the adapter class to look up
	 * @return a object castable to the given class, or <code>null</code> if
	 *         this object does not have an adapter for the given class
	 * @since 3.2
	 */
	protected Object getViewAdapter(Class adapter) {
		return null;
	}

	/**
	 * Returns the active, important workbench part for this view.
	 * <p>
	 * When the page book view is created it has no idea which part within the
	 * workbook should be used to generate the first page. Therefore, it
	 * delegates the choice to subclasses of <code>PageBookView</code>.
	 * </p>
	 * <p>
	 * Implementors of this method should return an active, important part in
	 * the workbench or <code>null</code> if none found.
	 * </p>
	 * <p>
	 * Subclasses must implement this method.
	 * </p>
	 * 
	 * @return the active important part, or <code>null</code> if none
	 */
	protected abstract MPart getBootstrapPart();

	/**
	 * Returns the part which contributed the current page to this view.
	 * 
	 * @return the part which contributed the current page or <code>null</code>
	 *         if no part contributed the current page
	 */
	protected MPart getCurrentContributingPart() {
		if (activeRec == null) {
			return null;
		}
		return activeRec.part;
	}

	/**
	 * Returns the currently visible page for this view or <code>null</code> if
	 * no page is currently visible.
	 * 
	 * @return the currently visible page
	 */
	public IPage getCurrentPage() {
		if (activeRec == null) {
			return null;
		}
		return activeRec.page;
	}

	/**
	 * Returns the default page for this view.
	 * 
	 * @return the default page
	 */
	public IPage getDefaultPage() {
		return defaultPageRec.page;
	}

	/**
	 * Returns the pagebook control for this view.
	 * 
	 * @return the pagebook control, or <code>null</code> if not initialized
	 */
	protected PageBook getPageBook() {
		return book;
	}

	/**
	 * Returns the page record for the given part.
	 * 
	 * @param part
	 *            the part
	 * @return the corresponding page record, or <code>null</code> if not found
	 */
	protected PageRec getPageRec(MPart part) {
		return (PageRec) mapPartToRec.get(part);
	}

	/**
	 * Returns the page record for the given page of this view.
	 * 
	 * @param page
	 *            the page
	 * @return the corresponding page record, or <code>null</code> if not found
	 */
	protected PageRec getPageRec(IPage page) {
		Iterator itr = mapPartToRec.values().iterator();
		while (itr.hasNext()) {
			PageRec rec = (PageRec) itr.next();
			if (rec.page == page) {
				return rec;
			}
		}
		return null;
	}

	/**
	 * Returns whether the given part should be added to this view.
	 * <p>
	 * Subclasses must implement this method.
	 * </p>
	 * 
	 * @param part
	 *            the input part
	 * @return <code>true</code> if the part is relevant, and <code>false</code>
	 *         otherwise
	 */
	protected abstract boolean isImportant(MPart part);

	/**
	 * The <code>PageBookView</code> implementation of this
	 * <code>IPartListener</code> method shows the page when the given part is
	 * activated. Subclasses may extend.
	 */
	public void partActivated(MPart part) {
		// Is this an important part? If not just return.
		if (!isImportant(part)) {
			return;
		}
		hiddenPart = null;

		// Create a page for the part.
		PageRec rec = getPageRec(part);
		if (rec == null) {
			rec = createPage(part);
		}

		// Show the page.
		if (rec != null) {
			showPageRec(rec);
		} else {
			showPageRec(defaultPageRec);
		}
	}

	/**
	 * The <code>PageBookView</code> implementation of this
	 * <code>IPartListener</code> method does nothing. Subclasses may extend.
	 */
	public void partBroughtToTop(MPart part) {
		// Do nothing by default
	}

	/**
	 * The <code>PageBookView</code> implementation of this
	 * <code>IPartListener</code> method deal with the closing of the active
	 * part. Subclasses may extend.
	 */
	public void partClosed(MPart part) {
		// Update the active part.
		if (activeRec != null && activeRec.part == part) {
			showPageRec(defaultPageRec);
		}

		// Find and remove the part page.
		PageRec rec = getPageRec(part);
		if (rec != null) {
			removePage(rec);
		}
		if (part == hiddenPart) {
			hiddenPart = null;
		}
	}

	/**
	 * The <code>PageBookView</code> implementation of this
	 * <code>IPartListener</code> method does nothing. Subclasses may extend.
	 */
	public void partDeactivated(MPart part) {
		// Do nothing.
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.IPartListener#partOpened(org.eclipse.ui.IWorkbenchPart)
	 */
	public void partOpened(MPart part) {
		// Do nothing by default.
	}

	/**
	 * Removes a page record. If it is the last reference to the page dispose of
	 * it - otherwise just decrement the reference count.
	 * 
	 * @param rec
	 */
	private void removePage(PageRec rec) {
		mapPartToRec.remove(rec.part);

		int newCount = ((Integer) mapPageToNumRecs.get(rec.page)).intValue() - 1;

		if (newCount == 0) {
			mapPageToSite.remove(rec.page);
			mapPageToNumRecs.remove(rec.page);

			Control control = rec.page.getControl();
			if (control != null && !control.isDisposed()) {
				// Dispose the page's control so pages don't have to do this in
				// their
				// dispose method.
				// The page's control is a child of this view's control so if
				// this view
				// is closed, the page's control will already be disposed.
				control.dispose();
			}

			// free the page
			doDestroyPage(rec.part, rec);

		} else {
			mapPageToNumRecs.put(rec.page, new Integer(newCount));
		}
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbenchPart.
	 */
	@Focus
	public void setFocus() {
		// first set focus on the page book, in case the page
		// doesn't properly handle setFocus
		if (book != null) {
			book.setFocus();
		}
		// then set focus on the page, if any
		if (activeRec != null) {
			activeRec.page.setFocus();
		}
	}

	/**
	 * Shows a page for the active workbench part.
	 */
	private void showBootstrapPart() {
		MPart part = getBootstrapPart();
		if (part != null) {
			partActivated(part);
		}
	}

	/**
	 * Shows page contained in the given page record in this view. The page
	 * record must be one from this pagebook view.
	 * <p>
	 * The <code>PageBookView</code> implementation of this method asks the
	 * pagebook control to show the given page's control, and records that the
	 * given page is now current. Subclasses may extend.
	 * </p>
	 * 
	 * @param pageRec
	 *            the page record containing the page to show
	 */
	protected void showPageRec(PageRec pageRec) {
		// If already showing do nothing
		if (activeRec == pageRec) {
			return;
		}
		// If the page is the same, just set activeRec to pageRec
		if (activeRec != null && pageRec != null
				&& activeRec.page == pageRec.page) {
			activeRec = pageRec;
			return;
		}

		// Show new page.
		activeRec = pageRec;
		if (activeRec != null) {
			if (activeRec.page != null) {
				Control pageControl = activeRec.page.getControl();
				if (pageControl != null && !pageControl.isDisposed()) {

					// Verify that the page control is not disposed
					// If we are closing, it may have already been disposed
					book.showPage(pageControl);

					// add our selection listener
					// FIXME - removed selection listener
				}
			}
		}
	}

	/**
	 * Make sure that the part is not considered if it is hidden.
	 * 
	 * @param part
	 * @since 3.5
	 */
	protected void partHidden(MPart part) {
		if (part == null || part != getCurrentContributingPart()) {
			return;
		}
		hiddenPart = part;
		showPageRec(defaultPageRec);
	}

	/**
	 * Make sure that the part is not considered if it is hidden.
	 * 
	 * @param part
	 * @since 3.5
	 */
	protected void partVisible(MPart part) {
		if (part == null || part != hiddenPart) {
			return;
		}
		partActivated(part);
	}
}
