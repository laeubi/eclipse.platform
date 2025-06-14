/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.core.subscribers;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.internal.core.Policy;
import org.eclipse.team.internal.core.TeamPlugin;

/**
 * Compare local and remote contents.
 *
 * This comparator makes use of the <code>IStorage</code> provided by
 * an <code>IResourceVariant</code> or an <code>IFileRevision</code>to obtain the remote contents.
 * This means that the comparison may contact the server unless the contents
 * were cached locally by a previous operation. The caching of remote
 * contents is subscriber specific.
 */
public abstract class AbstractContentComparator {
	private boolean ignoreWhitespace = false;

	public AbstractContentComparator(boolean ignoreWhitespace) {
		this.ignoreWhitespace = ignoreWhitespace;
	}

	public boolean compare(IResource e1, IResourceVariant e2, IProgressMonitor monitor) {
		return compareObjects(e1, e2, monitor);
	}

	public boolean compare(IResource e1, IFileRevision e2, IProgressMonitor monitor) {
		return compareObjects(e1, e2, monitor);
	}

	private boolean compareObjects(Object e1, Object e2, IProgressMonitor monitor) {
		monitor.beginTask(null, 100);
		try (InputStream is1 = getContents(e1, Policy.subMonitorFor(monitor, 30));
				InputStream is2 = getContents(e2, Policy.subMonitorFor(monitor, 30))) {
			return contentsEqual(Policy.subMonitorFor(monitor, 40), is1, is2, shouldIgnoreWhitespace());
		} catch (IOException e) {
			return false;
		} catch (TeamException e) {
			TeamPlugin.log(e);
			return false;
		} finally {
			monitor.done();
		}
	}

	protected boolean shouldIgnoreWhitespace() {
		return ignoreWhitespace;
	}

	abstract protected boolean contentsEqual(IProgressMonitor monitor, InputStream is1, InputStream is2,
			boolean ignoreWhitespace);

	private InputStream getContents(Object resource, IProgressMonitor monitor)
			throws TeamException {
		try {
			if (resource instanceof IFile) {
				return new BufferedInputStream(((IFile) resource).getContents());
			} else if (resource instanceof IResourceVariant remote) {
				if (!remote.isContainer()) {
					return new BufferedInputStream(remote.getStorage(monitor)
							.getContents());
				}
			} else if (resource instanceof IFileRevision remote) {
				return new BufferedInputStream(remote.getStorage(monitor)
						.getContents());
			}
			return null;
		} catch (CoreException e) {
			throw TeamException.asTeamException(e);
		}
	}
}