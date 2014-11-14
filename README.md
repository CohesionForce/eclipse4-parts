#eclipse4-parts
==============

This repository contains selected views and dialogs that have been migrated from Eclipse 3.x to Eclipse 4.  

##Contents

The org.eclipse.ui.e4.part plugin contains:

1. Progress View - Shows a list of running jobs, along with a modal dialog for user jobs.

2. Progress Region - Animated tool bar region to notify the user when jobs are running.

3. Outline View - Shows an outline of the selected element.

4. Properties View - Shows the properties of a selected element.

5. Dialogs - Various dialogs

  A. ListSelectionDialog - Ported from Eclipse 3.x
  
  B. ElementListSelectionDialog - Ported from Eclipse 3.x

The org.eclipse.emf.edit.e4 plugin contains:

  This plugin contains a version of AdapterFactoryContentProvider and AdapterFactoryLabelProvider that import the IPropertySource and IPropertySourceProvider from the org.eclipse.ui.e4.part plugin so that they can be used with the OutlineView and PropertiesView.
  
##Usage

  The OutlineView and PropertiesView work in much the same way as the 3.x approach, except that the active MPart must contain an object that implements IAdaptable.
  
```
  @PostConstruct
  public void createContents(MPart part, ...
    part.setObject(this);
    .
    .
  }

  @Override
	public Object getAdapter(Class key) {
		if (key.equals(IContentOutlinePage.class)) {
			return getContentOutlinePage();
		}
		if (key.equals(IPropertySheetPage.class)) {
			return getPropertySheetPage();
		}
		return null;
	}
```


