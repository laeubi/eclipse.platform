/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.core.resources;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.ISavedState;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.ICVSFile;

/**
 * This class performs several functions related to determining the modified
 * status of files under CVS control. First, it listens for change delta's for
 * files and brodcasts them to all listeners. It also registers as a save
 * participant so that deltas generated before the plugin are loaded are not
 * missed. Secondly, it listens for CVS resource state change events and uses
 * these to properly mark files and folders as modified.
 */
public class FileModificationManager implements IResourceChangeListener, ISaveParticipant {
	
	private static final QualifiedName UPDATE_TIMESTAMP = new QualifiedName(CVSProviderPlugin.ID, "update-timestamp"); //$NON-NLS-1$
	
	private Set modifiedResources = new HashSet();

	// consider the following changes types and ignore the others (e.g. marker and description changes are ignored)
	protected int INTERESTING_CHANGES = 	IResourceDelta.CONTENT | 
																	IResourceDelta.MOVED_FROM | 
																	IResourceDelta.MOVED_TO |
																	IResourceDelta.OPEN | 
																	IResourceDelta.REPLACED |
																	IResourceDelta.TYPE;

	/**
	 * Listen for file modifications and fire modification state changes
	 * 
	 * @see org.eclipse.core.resources.IResourceChangeListener#resourceChanged(org.eclipse.core.resources.IResourceChangeEvent)
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			event.getDelta().accept(new IResourceDeltaVisitor() {
				public boolean visit(IResourceDelta delta) throws CoreException {
					IResource resource = delta.getResource();
					
					if (resource.getType()==IResource.PROJECT) {
						IProject project = (IProject)resource;
						if (!project.isAccessible()) {
							return false;
						}
						if ((delta.getFlags() & IResourceDelta.OPEN) != 0) {
							return false;
						} 
						if (RepositoryProvider.getProvider(project, CVSProviderPlugin.getTypeId()) == null) {
							return false;
						}
					}
					
					if (resource.getType()==IResource.FILE && delta.getKind() == IResourceDelta.CHANGED && resource.exists()) {
						int flags = delta.getFlags();
						if((flags & INTERESTING_CHANGES) != 0) {
							resourceChanged(resource, false);
						}
					} else if (delta.getKind() == IResourceDelta.ADDED) {
						resourceChanged(resource, true);
					} else if (delta.getKind() == IResourceDelta.REMOVED) {
						// provide notifications for deletions since they may not have been managed
						// The move/delete hook would have updated the parent counts properly
						modifiedResources.add(resource);
					}

					return true;
				}
			});
			if (!modifiedResources.isEmpty()) {
				CVSProviderPlugin.broadcastModificationStateChanges(
					(IResource[])modifiedResources.toArray(new IResource[modifiedResources.size()]));
				modifiedResources.clear();
			}
		} catch (CoreException e) {
			CVSProviderPlugin.log(e.getStatus());
		}

	}
	
	/**
	 * We register a save participant so we can get the delta from workbench
	 * startup to plugin startup.
	 * @throws CoreException
	 */
	public void registerSaveParticipant() throws CoreException {
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		ISavedState ss = ws.addSaveParticipant(CVSProviderPlugin.getPlugin(), this);
		if (ss != null) {
			ss.processResourceChangeEvents(this);
		}
		ws.removeSaveParticipant(CVSProviderPlugin.getPlugin());
	}
	
	/**
	 * @see org.eclipse.core.resources.ISaveParticipant#doneSaving(org.eclipse.core.resources.ISaveContext)
	 */
	public void doneSaving(ISaveContext context) {
	}
	/**
	 * @see org.eclipse.core.resources.ISaveParticipant#prepareToSave(org.eclipse.core.resources.ISaveContext)
	 */
	public void prepareToSave(ISaveContext context) throws CoreException {
	}
	/**
	 * @see org.eclipse.core.resources.ISaveParticipant#rollback(org.eclipse.core.resources.ISaveContext)
	 */
	public void rollback(ISaveContext context) {
	}
	/**
	 * @see org.eclipse.core.resources.ISaveParticipant#saving(org.eclipse.core.resources.ISaveContext)
	 */
	public void saving(ISaveContext context) throws CoreException {
	}

	
	/**
	 * Method updated flags the objetc as having been modfied by the updated
	 * handler. This flag is read during the resource delta to determine whether
	 * the modification made the file dirty or not.
	 * 
	 * @param mFile
	 */
	public void updated(ICVSFile mFile) {
		try {
			if (mFile instanceof EclipseFile) {
				IFile file = (IFile)mFile.getIResource();
				file.setSessionProperty(UPDATE_TIMESTAMP, new Long(file.getModificationStamp()));
			}
		} catch (CVSException e) {
			CVSProviderPlugin.log(e);
		} catch (CoreException e) {
			// TODO need to log whole core exception but htis is not pretty
			CVSProviderPlugin.log(CVSException.wrapException(e).getStatus());
		}
	}
	
	/*
	 * Handle added and changed resources by signaling the change to the corresponding
	 * CVS resource and recording the change for broadcast to interested listeners.
	 */
	private void resourceChanged(IResource resource, boolean addition) throws CoreException {
		if (isCleanUpdate(resource)) return;
		try {
			EclipseResource cvsResource = (EclipseResource)CVSWorkspaceRoot.getCVSResourceFor(resource);
			cvsResource.handleModification(addition);
			modifiedResources.add(resource);
		} catch (CVSException e) {
			throw e.toCoreException();
		}
	}

	/**
	 * If the file was the result of a clean update, the cached timestamp will
	 * be removed.
	 * 
	 * @param resource
	 * @return boolean
	 */
	private boolean isCleanUpdate(IResource resource) {
		if(resource.getType() != IResource.FILE) return false;
		long modStamp = resource.getModificationStamp();
		Long whenWeWrote;
		try {
			whenWeWrote = (Long)resource.getSessionProperty(UPDATE_TIMESTAMP);
			resource.setSessionProperty(UPDATE_TIMESTAMP, null);
		} catch(CoreException e) {
			CVSProviderPlugin.log(e.getStatus());
			whenWeWrote = null;
		}
		return (whenWeWrote!=null && whenWeWrote.longValue() == modStamp);
	}
}

