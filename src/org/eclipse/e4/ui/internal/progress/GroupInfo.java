/*******************************************************************************
 * Copyright (c) 2003, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Michael Fraenkel <fraenkel@us.ibm.com> - Fix for bug 60698 -
 *     [Progress] ConcurrentModificationException in NewProgressViewer.
 *******************************************************************************/
package org.eclipse.e4.ui.internal.progress;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.osgi.util.NLS;
import org.eclipse.e4.ui.internal.progress.FinishedJobs;
import org.eclipse.e4.ui.internal.progress.JobInfo;
import org.eclipse.e4.ui.internal.progress.JobTreeElement;
import org.eclipse.e4.ui.internal.progress.ProgressManager;
import org.eclipse.e4.ui.internal.progress.ProgressMessages;

/**
 * The GroupInfo is the object used to display group properties.
 */

class GroupInfo extends JobTreeElement implements IProgressMonitor {

	private List<JobInfo> infos = new ArrayList<JobInfo>();

	private Object lock = new Object();

	private String taskName = ProgressMessages.SubTaskInfo_UndefinedTaskName;

	boolean isActive = false;

	double total = -1;

	double currentWork;

	@Override
	boolean hasChildren() {
		synchronized (lock) {
			return !infos.isEmpty();
		}

	}

	@Override
	Object[] getChildren() {
		synchronized (lock) {
			return infos.toArray();
		}
	}

	@Override
	String getDisplayString() {
		if (total < 0) {
			return taskName;
		}
		String[] messageValues = new String[2];
		messageValues[0] = taskName;
		messageValues[1] = String.valueOf(getPercentDone());
		return NLS.bind(ProgressMessages.JobInfo_NoTaskNameDoneMessage,
				messageValues);

	}

	/**
	 * Return an integer representing the amount of work completed.
	 * 
	 * @return int
	 */
	int getPercentDone() {
		return (int) (currentWork * 100 / total);
	}

	@Override
	boolean isJobInfo() {
		return false;
	}

	@Override
	public void beginTask(String name, int totalWork) {
		if (name == null)
			taskName = ProgressMessages.SubTaskInfo_UndefinedTaskName;
		else
			taskName = name;
		total = totalWork;
		synchronized (lock) {
			isActive = true;
		}
		ProgressManager.getInstance().refreshGroup(this);

	}

	@Override
	public void done() {
		synchronized (lock) {
			isActive = false;
		}
		updateInProgressManager();

	}

	/**
	 * Update the receiver in the progress manager. If all of the jobs are
	 * finished and the receiver is not being kept then remove it.
	 */
	private void updateInProgressManager() {
		Iterator<JobInfo> infoIterator = infos.iterator();
		while (infoIterator.hasNext()) {
			JobInfo next = infoIterator.next();
			if (!(next.getJob().getState() == Job.NONE)) {
				ProgressManager.getInstance().refreshGroup(this);
				return;
			}
		}

		if (FinishedJobs.getInstance().isKept(this))
			ProgressManager.getInstance().refreshGroup(this);
		else
			ProgressManager.getInstance().removeGroup(this);
	}

	@Override
	public void internalWorked(double work) {
		synchronized (lock) {
			currentWork += work;
		}

	}

	@Override
	public boolean isCanceled() {
		// Just a group so no cancel state
		return false;
	}

	@Override
	public void setCanceled(boolean value) {
		cancel();
	}

	@Override
	public void setTaskName(String name) {
		synchronized (this) {
			isActive = true;
		}
		if (name == null)
			taskName = ProgressMessages.SubTaskInfo_UndefinedTaskName;
		else
			taskName = name;

	}

	@Override
	public void subTask(String name) {
		// Not interesting for this monitor
	}

	@Override
	public void worked(int work) {
		internalWorked(work);
	}

	/**
	 * Remove the job from the list of jobs.
	 * 
	 * @param job
	 */
	void removeJobInfo(final JobInfo job) {
		synchronized (lock) {
			infos.remove(job);
			if (infos.isEmpty()) {
				done();
			}
		}
	}

	/**
	 * Remove the job from the list of jobs.
	 * 
	 * @param job
	 */
	void addJobInfo(final JobInfo job) {
		synchronized (lock) {
			infos.add(job);
		}
	}

	@Override
	boolean isActive() {
		return isActive;
	}

	@Override
	public void cancel() {
		Object[] jobInfos = getChildren();
		for (int i = 0; i < jobInfos.length; i++) {
			((JobInfo) jobInfos[i]).cancel();
		}
		// Call the refresh so that this is updated immediately
		updateInProgressManager();
	}

	@Override
	public boolean isCancellable() {
		return true;
	}

	/**
	 * Get the task name for the receiver.
	 * 
	 * @return String
	 */
	String getTaskName() {
		return taskName;
	}

}
