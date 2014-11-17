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

package org.eclipse.e4.views.contentoutline;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.part.IPage;
import org.eclipse.e4.ui.part.MessagePage;
import org.eclipse.e4.ui.part.PageBook;
import org.eclipse.e4.ui.part.PageBookView;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.swt.widgets.Composite;
import org.osgi.service.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for the Content Outline View.
 * <p>
 * This standard view has id <code>"org.eclipse.ui.views.ContentOutline"</code>.
 * </p>
 * When a <b>content outline view</b> notices an editor being activated, it asks
 * the editor whether it has a <b>content outline page</b> to include in the
 * outline view. This is done using <code>getAdapter</code>:
 * 
 * <pre>
 * IEditorPart editor = ...;
 * IContentOutlinePage outlinePage = (IContentOutlinePage) editor.getAdapter(IContentOutlinePage.class);
 * if (outlinePage != null) {
 *    // editor wishes to contribute outlinePage to content outline view
 * }
 * </pre>
 * 
 * If the editor supports a content outline page, the editor instantiates and
 * configures the page, and returns it. This page is then added to the content
 * outline view (a pagebook which presents one page at a time) and immediately
 * made the current page (the content outline view need not be visible). If the
 * editor does not support a content outline page, the content outline view
 * shows a special default page which makes it clear to the user that the
 * content outline view is disengaged. A content outline page is free to report
 * selection events; the content outline view forwards these events along to
 * interested parties. When the content outline view notices a different editor
 * being activated, it flips to the editor's corresponding content outline page.
 * When the content outline view notices an editor being closed, it destroys the
 * editor's corresponding content outline page. </p>
 * <p>
 * The workbench will automatically instantiate this class when a Content
 * Outline view is needed for a workbench window. This class was not intended to
 * be instantiated or subclassed by clients.
 * </p>
 * 
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ContentOutline extends PageBookView {

	Logger logger = LoggerFactory.getLogger(ContentOutline.class);
	/**
	 * Message to show on the default page.
	 */
	private String defaultText = "No Content to display";

	/**
	 * The current part for which this property sheets is active
	 */
	private MPart currentPart;

	/**
	 * Part that hosts the property sheet view
	 */
	private MPart contentOutlinePart;

	/**
	 * Creates a content outline view with no content outline pages.
	 */
	public ContentOutline() {
		super();
	}

	/*
	 * (non-Javadoc) Method declared on PageBookView.
	 */
	protected IPage createDefaultPage(PageBook book) {
		MessagePage page = new MessagePage();
		page.createControl(book);
		page.setMessage(defaultText);
		return page;
	}

	/**
	 * The <code>PageBookView</code> implementation of this
	 * <code>IWorkbenchPart</code> method creates a <code>PageBook</code>
	 * control with its default page showing.
	 */
	@PostConstruct
	public void createPartControl(Composite parent, MPart part) {
		contentOutlinePart = part;
		// super.createPartControl(parent);
	}

	@Focus
	public void onFocus() {
		super.setFocus();
	}

	@Inject
	@Optional
	public void partActivation(
			@UIEventTopic(UIEvents.UILifeCycle.ACTIVATE) Event event,
			MApplication application) {
		MPart activePart = (MPart) event
				.getProperty(UIEvents.EventTags.ELEMENT);
		logger.debug("Updating based on new active part: {}",
				activePart.getElementId());
		partActivated(activePart);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.part.PageBookView#partClosed(org.eclipse.ui.IWorkbenchPart
	 * ) since 3.4
	 */
	public void partClosed(MPart part) {
		if (part.equals(currentPart)) {
			currentPart = null;
		}
		super.partClosed(part);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.part.PageBookView#partVisible(org.eclipse.ui.IWorkbenchPart
	 * ) since 3.4
	 */
	protected void partVisible(MPart part) {
		super.partVisible(part);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.part.PageBookView#partHidden(org.eclipse.ui.IWorkbenchPart
	 * ) since 3.4
	 */
	protected void partHidden(MPart part) {
		// Explicitly ignore parts becoming hidden as this
		// can cause issues when the Property View is maximized
		// See bug 325743 for more details
	}

	/*
	 * (non-Javadoc) Method declared on PageBookView.
	 */
	protected PageRec doCreatePage(MPart part) {
		logger.debug("Creating outline page for {}", part.getElementId());
		// Try to get an outline page.
		if (part.getObject() != null && part.getObject() instanceof IAdaptable) {
			IContentOutlinePage page = (IContentOutlinePage) ((IAdaptable) part
					.getObject()).getAdapter(IContentOutlinePage.class);
			
			if (page != null) {
				page.createControl(getPageBook());
				return new PageRec(part, (IPage) page);
			} else {
				logger.debug("No content outline found for {}",
						part.getElementId());
			}
		}
		// There is no content outline
		return null;
	}

	/*
	 * (non-Javadoc) Method declared on PageBookView.
	 */
	protected void doDestroyPage(MPart part, PageRec rec) {
		IContentOutlinePage page = (IContentOutlinePage) rec.page;
		page.dispose();
		rec.dispose();
	}

	/*
	 * (non-Javadoc) Method declared on PageBookView.
	 */
	protected MPart getBootstrapPart() {
		return null;
	}

	protected boolean isImportant(MPart part) {
		return true;
	}

	/*
	 * (non-Javadoc) Method declared on IViewPart. Treat this the same as part
	 * activation.
	 */
	public void partBroughtToTop(MPart part) {
		partActivated(part);
	}

	/**
	 * The <code>PropertySheet</code> implementation of this
	 * <code>IPartListener</code> method first sees if the active part is an
	 * <code>IContributedContentsView</code> adapter and if so, asks it for its
	 * contributing part.
	 */
	public void partActivated(MPart part) {
		if (part == contentOutlinePart) {
			// Don't need to do anything if just the PropertySheet has
			// been activated
			return;
		}

		logger.debug("Handling part activation of {}", part.getElementId());
		super.partActivated(part);

		if (isImportant(part)) {
			currentPart = part;
			// reset the selection (to allow selectionChanged() accept part
			// change for empty selections)
		}

	}
}
