package org.eclipse.e4.ui.part;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle.
 */
public class Activator implements BundleActivator {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.eclipse.e4.ui.part"; //$NON-NLS-1$

	private static Activator plugin;
	
	private static BundleContext context;
	
	/**
	 * The constructor
	 */
	public Activator() {
	}

	public void start(BundleContext context) throws Exception {
		Activator.context = context;
		plugin = this;
	}

	public void stop(BundleContext context) throws Exception {
		plugin = null;
	}

	/**
	 * Returns the shared Activator instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}
	
	/**
	 * Returns the shared BundleContext used to manage the lifecycle
	 * of this bundle.
	 * 
	 * @return the shared bundle context
	 */
	public static BundleContext getContext() {
		return context;
	}

}
