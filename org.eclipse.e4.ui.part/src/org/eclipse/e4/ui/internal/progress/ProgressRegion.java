/*******************************************************************************
 * Copyright (c) 2004, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM - Initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.ui.internal.progress;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.e4.ui.internal.progress.AnimationItem;
import org.eclipse.e4.ui.internal.progress.AnimationManager;
import org.eclipse.e4.ui.internal.progress.JobInfo;
import org.eclipse.e4.ui.internal.progress.ProgressAnimationItem;
import org.eclipse.e4.ui.internal.progress.ProgressCanvasViewer;
import org.eclipse.e4.ui.internal.progress.ProgressManagerUtil;
import org.eclipse.e4.ui.internal.progress.ProgressViewerContentProvider;
import org.eclipse.e4.ui.internal.progress.ProgressViewerLabelProvider;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.workbench.modeling.EPartService;

/**
 * The ProgressRegion is class for the region of the workbench where the
 * progress line and the animation item are shown.
 */
public class ProgressRegion {
    ProgressCanvasViewer viewer;

    ProgressAnimationItem animationItem;

    @Inject
    EPartService partService;
    
    Composite region;

	/**
	 * the side the receiver is placed on
	 */
	private int side = SWT.BOTTOM;
	
	private boolean forceHorizontal;

    /**
     * Create a new instance of the receiver.
     */
    public ProgressRegion() {
        //No default behavior.
    }

    /**
     * Create the contents of the receiver in the parent. Use the window for the
     * animation item.
     * 
     * @param parent
     *            The parent widget of the composite.
     * @param window
     *            The WorkbenchWindow this is in.
     * @return Control
     */
    @PostConstruct
    public Control createContents(Composite parent) {

        // Test whether or not 'advanced' graphics are available
        // If not then we'll 'force' the ProgressBar to always be
        // HORIZONTAL...
        //TODO: This should likely be at some 'global' level state
        GC gc = new GC(parent);
        gc.setAdvanced(true);
        forceHorizontal = !gc.getAdvanced();
        gc.dispose();
        
        region = new Composite(parent, SWT.NONE) {
        	@Override
        	public Point computeSize(int wHint, int hHint, boolean changed) {
				Point size = super.computeSize(wHint, hHint, changed);
				if (isHorizontal(side))
					size.y = 20;
				else {
					size.x = 20;
				}
				return size;
			}
		};
		
        GridLayout gl = new GridLayout();
        gl.marginHeight = 0;
        gl.marginWidth = 0;
        if (isHorizontal(side))
        	gl.numColumns = 3;
        region.setLayout(gl);

        viewer = new ProgressCanvasViewer(region, SWT.NO_FOCUS, 1, 36, isHorizontal(side) ? SWT.HORIZONTAL : SWT.VERTICAL);
        viewer.setUseHashlookup(true);
        Control viewerControl = viewer.getControl();
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        Point viewerSizeHints = viewer.getSizeHints();
        if (isHorizontal(side)) {
        	gd.widthHint = viewerSizeHints.x;
        	gd.heightHint = viewerSizeHints.y;
        } else {
        	gd.widthHint = viewerSizeHints.y;
        	gd.heightHint = viewerSizeHints.x;
        }
        viewerControl.setLayoutData(gd);

        int widthPreference = AnimationManager.getInstance()
                .getPreferredWidth() + 25;
        animationItem = new ProgressAnimationItem(this, isHorizontal(side) ? SWT.HORIZONTAL : SWT.VERTICAL);
        animationItem.createControl(region);

        animationItem.setAnimationContainer(new AnimationItem.IAnimationContainer() {
        	@Override
        	public void animationDone() {
                //Add an extra refresh to the viewer in case
                //of stale input if the controls are not disposed
                if (viewer.getControl().isDisposed()) {
					return;
				}
                viewer.refresh();
            }

        	@Override
            public void animationStart() {
                // Nothing by default here.
            }
        });
        if (isHorizontal(side)) {
        	gd = new GridData(GridData.FILL_VERTICAL);
            gd.widthHint = widthPreference;
        } else {
        	gd = new GridData(GridData.FILL_HORIZONTAL);
            gd.heightHint = widthPreference;
        }

        animationItem.getControl().setLayoutData(gd);

        viewerControl.addMouseListener(new MouseAdapter() {
        	@Override
        	public void mouseDoubleClick(MouseEvent e) {
                processDoubleClick();
            }
        });

        //Never show debug info
        IContentProvider provider = new ProgressViewerContentProvider(viewer,
                false,false);
        viewer.setContentProvider(provider);
        viewer.setInput(provider);
        viewer.setLabelProvider(new ProgressViewerLabelProvider(viewerControl));
        viewer.setComparator(ProgressManagerUtil.getProgressViewerComparator());
        viewer.addFilter(new ViewerFilter() {
            public boolean select(Viewer viewer, Object parentElement, Object element) {
                if (element instanceof JobInfo) {
                    JobInfo info= (JobInfo)element;
                    if (info.isBlocked() || info.getJob().getState() == Job.WAITING) {
                    	return false;
                    }
                }
                return true;
            }
        	
		});
        return region;
    }

    /**
     * Return the animationItem for the receiver.
     * 
     * @return AnimationItem
     */
    public AnimationItem getAnimationItem() {
        return animationItem;
    }

    /**
     * Return the control for the receiver.
     * 
     * @return Control
     */
    public Control getControl() {
        return region;
    }

    /**
     * Process the double click event.
     */
    public void processDoubleClick() {
    	MPart part = partService.findPart("org.eclipse.e4.ui.progress");
    	if(part == null)
    	{
    		part = (MPart) partService.createSharedPart("org.eclipse.e4.ui.progress");
    	}
    	if(part != null)
    	{
    		partService.activate(part);
    	}
    }

    //TODO - find out what to do with this
	public void dock(int dropSide) {
		int oldSide = side;
		side = dropSide;
		if (oldSide == dropSide || (isVertical(oldSide) && isVertical(dropSide)) || (isHorizontal(oldSide) && isHorizontal(dropSide)))
			return;
		recreate();
	}

	/**
	 * Answer true if the side is a horizonal one
	 * 
	 * @param dropSide
	 * @return <code>true</code> if the side is horizontal
	 */
	private boolean isHorizontal(int dropSide) {
		if (forceHorizontal)
			return true;
		return dropSide == SWT.TOP || dropSide == SWT.BOTTOM;
	}


	/**
	 * Answer true if the side is a horizonal one
	 * 
	 * @param dropSide
	 * @return <code>true</code> if the side is horizontal
	 */
	private boolean isVertical(int dropSide) {
		if (forceHorizontal)
			return false;
		return dropSide == SWT.LEFT || dropSide == SWT.RIGHT;
	}

	/**
	 * Recreate the receiver given the new side
	 */
	private void recreate() {
		if (region != null && !region.isDisposed()) {
			Composite parent = region.getParent();
			boolean animating = animationItem.animationRunning();
	        AnimationManager.getInstance().removeItem(animationItem);
			region.dispose();
			createContents(parent);
			if (animating)
				animationItem.animationStart();
		}
	}
	
	@PreDestroy
	public void destroy()
	{
		AnimationManager.getInstance().shutdown();
	}
}
