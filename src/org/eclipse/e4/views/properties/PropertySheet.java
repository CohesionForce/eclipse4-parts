/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Markus Alexander Kuppe (Versant Corp.) - https://bugs.eclipse.org/248103
 *     Semion Chichelnitsky (semion@il.ibm.com) - bug 272564
 *     Craig Foote (Footeware.ca) - https://bugs.eclipse.org/325743
 *******************************************************************************/
package org.eclipse.e4.views.properties;

import java.util.HashSet;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IRegistryEventListener;
import org.eclipse.core.runtime.RegistryFactory;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.part.IPage;
import org.eclipse.e4.ui.part.IPageBookViewPage;
import org.eclipse.e4.ui.part.PageBook;
import org.eclipse.e4.ui.part.PageBookView;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISaveablePart;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.internal.views.ViewsPlugin;
import org.eclipse.ui.part.ShowInContext;
import org.osgi.service.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for the Property Sheet View.
 * <p>
 * This standard view has id <code>"org.eclipse.ui.views.PropertySheet"</code>.
 * </p>
 * <p>
 * Note that property <it>sheets</it> and property sheet pages are not the
 * same thing as property <it>dialogs</it> and their property pages (the property
 * pages extension point is for contributing property pages to property dialogs).
 * Within the property sheet view, all pages are <code>IPropertySheetPage</code>s.
 * </p>
 * <p>
 * Property sheet pages are discovered by the property sheet view automatically
 * when a part is first activated. The property sheet view asks the active part
 * for its property sheet page; this is done by invoking
 * <code>getAdapter(IPropertySheetPage.class)</code> on the part. If the part
 * returns a page, the property sheet view then creates the controls for that
 * property sheet page (using <code>createControl</code>), and adds the page to
 * the property sheet view. Whenever this part becomes active, its corresponding
 * property sheet page is shown in the property sheet view (which may or may not
 * be visible at the time). A part's property sheet page is discarded when the
 * part closes. The property sheet view has a default page (an instance of
 * <code>PropertySheetPage</code>) which services all parts without a property
 * sheet page of their own.
 * </p>
 * <p>
 * The workbench will automatically instantiates this class when a Property
 * Sheet view is needed for a workbench window. This class is not intended
 * to be instantiated or subclassed by clients.
 * </p>
 *
 * @see IPropertySheetPage
 * @see PropertySheetPage
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @noextend This class is not intended to be subclassed by clients.
 */
@SuppressWarnings("restriction")
public class PropertySheet extends PageBookView implements ISelectionListener, IRegistryEventListener {

	
	Logger logger = LoggerFactory.getLogger(PropertySheet.class);
	
    /**
     * Extension point used to modify behavior of the view
     */
    private static final String EXT_POINT = "org.eclipse.ui.propertiesView"; //$NON-NLS-1$
    
    /**
     * The initial selection when the property sheet opens
     */
    private ISelection bootstrapSelection;

    /**
     * The current part for which this property sheets is active
     */
	private MPart currentPart;

	/**
	 * Set of workbench parts, which should not be used as a source for PropertySheet
	 */
	private HashSet ignoredViews;
	
	/**
	 * Part that hosts the property sheet view
	 */
	private MPart propertySheetPart;
	
    /**
     * Creates a property sheet view.
     */
    public PropertySheet() {
        super();
        RegistryFactory.getRegistry().addListener(this, EXT_POINT);
    }

    /* (non-Javadoc)
     * Method declared on PageBookView.
     * Returns the default property sheet page.
     */
    protected IPage createDefaultPage(PageBook book) {
    	logger.debug("Creating default pagebook page for property sheet");
        IPageBookViewPage page = (IPageBookViewPage) ViewsPlugin.getAdapter(this,
                IPropertySheetPage.class, false);
        if(page == null) {
        	page =  new PropertySheetPage();
        }
        initPage(page);
        page.createControl(book);
        return page;
    }

	@SuppressWarnings("restriction")
	@Inject
	@Optional
	public void partActivation(
			@UIEventTopic(UIEvents.UILifeCycle.ACTIVATE) Event event,
			MApplication application) {
		MPart activePart = (MPart) event
				.getProperty(UIEvents.EventTags.ELEMENT);
		logger.debug("Updating based on new active part: {}", activePart.getElementId());
		partActivated(activePart);
	}

	/**
     * The <code>PropertySheet</code> implementation of this <code>IWorkbenchPart</code>
     * method creates a <code>PageBook</code> control with its default page showing.
     */
    @PostConstruct
    public void createPartControl(Composite parent, MPart part) {
    	propertySheetPart = part;
    //    super.createPartControl(parent);
    }
    
    @Focus
    public void onFocus()
    {
    	super.setFocus();
    }

    /* (non-Javadoc)
     * Method declared on IWorkbenchPart.
     */
    public void dispose() {
        // run super.
        super.dispose();

        RegistryFactory.getRegistry().removeListener(this);
        
        currentPart = null;
    }

    /* (non-Javadoc)
     * Method declared on PageBookView.
     */
    protected PageRec doCreatePage(MPart part) {
    	logger.debug("Creating property sheet for {}", part.getElementId());
    	IPropertySheetPage page = (IPropertySheetPage) ViewsPlugin.getAdapter(part.getObject(),
                IPropertySheetPage.class, false);
        if (page != null) {
            if (page instanceof IPageBookViewPage) {
				initPage((IPageBookViewPage) page);
			}
            page.createControl(getPageBook());
            return new PageRec(part, (IPage) page);
        } else {
        	logger.debug("No property sheet found for {}", part.getElementId());
        }

        // Use the default page
        return null;
    }

    /* (non-Javadoc)
     * Method declared on PageBookView.
     */
    protected void doDestroyPage(MPart part, PageRec rec) {
        IPropertySheetPage page = (IPropertySheetPage) rec.page;
        page.dispose();
        rec.dispose();
    }

    /* (non-Javadoc)
     * Method declared on PageBookView.
     * Returns the active part on the same workbench page as this property
     * sheet view.
     */
//    protected MPart getBootstrapPart() {
//        IWorkbenchPage page = getSite().getPage();
//        if (page != null) {
//            bootstrapSelection = page.getSelection();
//            return page.getActivePart();
//        }
//        return null;
//    }

    /* (non-Javadoc)
     * Method declared on IViewPart.
     */
    public void init() throws PartInitException {
//   		site.getPage().addPostSelectionListener(this);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.ui.part.ViewPart#saveState(org.eclipse.ui.IMemento)
	 * since 3.4
	 */
	public void saveState(IMemento memento) {
		// close all but the primary/parent property sheet
//		String secondaryId = getViewSite().getSecondaryId();
//		if (null == secondaryId) {
//			super.saveState(memento);
//		} else {
//			getViewSite().getPage().hideView(this);
//		}
	}

    /* (non-Javadoc)
     * Method declared on PageBookView.
     * The property sheet may show properties for any view other than this view.
     */
    protected boolean isImportant(MPart part) {
		// Don't interfere with other property views
    	String partID = part.getElementId();
		return !isViewIgnored(partID);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#partClosed(org.eclipse.ui.IWorkbenchPart)
	 * since 3.4
	 */
	public void partClosed(MPart part) {
		if (part.equals(currentPart)) {
			currentPart = null;
		}
		super.partClosed(part);
	}
    
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#partVisible(org.eclipse.ui.IWorkbenchPart)
	 * since 3.4
	 */
	protected void partVisible(MPart part) {
	    super.partVisible(part);
	}
	
    /* (non-Javadoc)
     * @see org.eclipse.ui.part.PageBookView#partHidden(org.eclipse.ui.IWorkbenchPart)
     * since 3.4
     */
    protected void partHidden(MPart part) {
    	// Explicitly ignore parts becoming hidden as this
    	// can cause issues when the Property View is maximized
    	// See bug 325743 for more details
    }
    
	/**
     * The <code>PropertySheet</code> implementation of this <code>IPartListener</code>
     * method first sees if the active part is an <code>IContributedContentsView</code>
     * adapter and if so, asks it for its contributing part.
     */
    public void partActivated(MPart part) {
    	if(part == propertySheetPart)
    	{
    		// Don't need to do anything if just the PropertySheet has
    		// been activated
    		return;
    	}

    	logger.debug("Handling part activation of {}", part.getElementId());
		super.partActivated(part);

        if(isImportant(part)) {
        	currentPart = part;
        	// reset the selection (to allow selectionChanged() accept part change for empty selections)
        }
        
        // When the view is first opened, pass the selection to the page
        if (bootstrapSelection != null) {
            IPropertySheetPage page = (IPropertySheetPage) getCurrentPage();
            if (page != null) {
//				page.selectionChanged(part, bootstrapSelection);
			}
            bootstrapSelection = null;
        }
    }

//	private void updateContentDescription() {
//		if (currentPart != null) {
//			setContentDescription(NLS.bind(PropertiesMessages.Selection_description, currentPart.getLabel()));
//		} else {
//			setContentDescription(""); //$NON-NLS-1$
//		}
//	}
    
    /**
	 * The <code>PropertySheet</code> implementation of this
	 * <code>PageBookView</code> method handles the <code>ISaveablePart</code>
	 * adapter case by calling <code>getSaveablePart()</code>.
	 * 
	 * @since 3.2
	 */
	protected Object getViewAdapter(Class key) {
		if (ISaveablePart.class.equals(key)) {
			return getSaveablePart();
		}
		return super.getViewAdapter(key);
	}

	/**
	 * Returns an <code>ISaveablePart</code> that delegates to the source part
	 * for the current page if it implements <code>ISaveablePart</code>, or
	 * <code>null</code> otherwise.
	 * 
	 * @return an <code>ISaveablePart</code> or <code>null</code>
	 * @since 3.2
	 */
	protected ISaveablePart getSaveablePart() {
		MPart part = getCurrentContributingPart();
		if (part instanceof ISaveablePart) {
			return (ISaveablePart) part;
		}
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * @since 3.4
	 */
//	public ShowInContext getShowInContext() {
//		return new PropertyShowInContext(currentPart, currentSelection);
//	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * @since 3.4
	 */
	public boolean show(ShowInContext aContext) {
		if (aContext instanceof PropertyShowInContext) {
			PropertyShowInContext context = (PropertyShowInContext) aContext;
//			partActivated(context.getPart());
			selectionChanged(context.getPart(), context.getSelection());
			return true;
		}
		return false;
	}

	private HashSet getIgnoredViews() {
		if (ignoredViews == null) {
			ignoredViews = new HashSet();
	        IExtensionRegistry registry = RegistryFactory.getRegistry();
	        IExtensionPoint ep = registry.getExtensionPoint(EXT_POINT);
			if (ep != null) {
				IExtension[] extensions = ep.getExtensions();
				for (int i = 0; i < extensions.length; i++) {
					IConfigurationElement[] elements = extensions[i].getConfigurationElements();
					for (int j = 0; j < elements.length; j++) {
						if ("excludeSources".equalsIgnoreCase(elements[j].getName())) { //$NON-NLS-1$
							String id = elements[j].getAttribute("id"); //$NON-NLS-1$
							if (id != null)
								ignoredViews.add(id);
						}
					}
				}
			}
		}
		return ignoredViews;
	}

	private boolean isViewIgnored(String partID) {
		return getIgnoredViews().contains(partID);
	}
	
	/**
	 * @see org.eclipse.core.runtime.IRegistryEventListener#added(org.eclipse.core.runtime.IExtension[])
	 * @since 3.5
	 */
	public void added(IExtension[] extensions) {
		ignoredViews = null;
	}

	/**
	 * @see org.eclipse.core.runtime.IRegistryEventListener#added(org.eclipse.core.runtime.IExtensionPoint[])
	 * @since 3.5
	 */
	public void added(IExtensionPoint[] extensionPoints) {
		ignoredViews = null;
	}

	/**
	 * @see org.eclipse.core.runtime.IRegistryEventListener#removed(org.eclipse.core.runtime.IExtension[])
	 * @since 3.5
	 */
	public void removed(IExtension[] extensions) {
		ignoredViews = null;
	}

	/**
	 * @see org.eclipse.core.runtime.IRegistryEventListener#removed(org.eclipse.core.runtime.IExtensionPoint[])
	 * @since 3.5
	 */
	public void removed(IExtensionPoint[] extensionPoints) {
		ignoredViews = null;
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected MPart getBootstrapPart() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
