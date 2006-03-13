/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.mappings;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.*;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.team.core.diff.IDiffTree;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.core.subscribers.ChangeSet;
import org.eclipse.team.internal.core.subscribers.DiffChangeSet;
import org.eclipse.team.internal.ui.mapping.ResourceModelLabelProvider;

public class ChangeSetLabelProvider extends ResourceModelLabelProvider {

	private Image changeSetImage;

	protected String getDelegateText(Object element) {
		if (element instanceof DiffChangeSet) {
			DiffChangeSet set = (DiffChangeSet) element;
			return set.getName();
		}
		return super.getDelegateText(element);
	}
	
	protected Image getDelegateImage(Object element) {
		if (element instanceof DiffChangeSet) {
			return getChangeSetImage();
		}
		return super.getDelegateImage(element);
	}

	private Image getChangeSetImage() {
		if (changeSetImage == null) {
			ImageDescriptor imageDescriptor = CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_CHANGELOG);
			if (imageDescriptor != null)
				changeSetImage = imageDescriptor.createImage();
		}
		return changeSetImage;
	}
	
	public void dispose() {
		if (changeSetImage != null) {
			changeSetImage.dispose();
		}
		super.dispose();
	}
	
	protected boolean isBusy(Object element) {
		if (element instanceof DiffChangeSet) {
			DiffChangeSet dcs = (DiffChangeSet) element;
			IResource[] resources = dcs.getResources();
			for (int i = 0; i < resources.length; i++) {
				IResource resource = resources[i];
				if (getContext().getDiffTree().getProperty(resource.getFullPath(), IDiffTree.P_BUSY_HINT))
					return true;
			}
			return false;
		}
		return super.isBusy(element);
	}
	
	protected boolean hasDecendantConflicts(Object element) {
		if (element instanceof DiffChangeSet) {
			DiffChangeSet dcs = (DiffChangeSet) element;
			IResource[] resources = dcs.getResources();
			for (int i = 0; i < resources.length; i++) {
				IResource resource = resources[i];
				if (getContext().getDiffTree().getProperty(resource.getFullPath(), IDiffTree.P_HAS_DESCENDANT_CONFLICTS))
					return true;
			}
			return false;
		}
		return super.hasDecendantConflicts(element);
	}
	
	protected int getMarkerSeverity(Object element) {
		if (element instanceof DiffChangeSet) {
			DiffChangeSet dcs = (DiffChangeSet) element;
			Set projects = new HashSet();
			IResource[] resources = dcs.getResources();
			int severity = -1;
			for (int i = 0; i < resources.length; i++) {
				IResource resource = resources[i];
				IProject project = resource.getProject();
				if (!projects.contains(project)) {
					projects.add(project);
					int next = super.getMarkerSeverity(project);
					if (next == IMarker.SEVERITY_ERROR)
						return IMarker.SEVERITY_ERROR;
					if (next == IMarker.SEVERITY_WARNING)
						severity = next;
				}
			}
			return severity;
		}
		return super.getMarkerSeverity(element);
	}
	
	protected void updateLabels(Object[] elements) {
		super.updateLabels(addSetsContainingElements(elements));
	}

	private Object[] addSetsContainingElements(Object[] elements) {
		Set result = new HashSet();
		for (int i = 0; i < elements.length; i++) {
			Object object = elements[i];
			result.add(object);
			if (object instanceof IProject) {
				IProject project = (IProject) object;
				ChangeSet[] sets = getSetsContaing(project);
				for (int j = 0; j < sets.length; j++) {
					ChangeSet set = sets[j];
					result.add(set);
				}
			}
		}
		return result.toArray();
	}

	private ChangeSet[] getSetsContaing(IProject project) {
		return getContentProvider().getSetsShowingPropogatedStateFrom(project.getFullPath());
	}

	private ChangeSetContentProvider getContentProvider() {
		return (ChangeSetContentProvider)getExtensionSite().getExtension().getContentProvider();
	}

}
