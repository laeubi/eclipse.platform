/*******************************************************************************
 * Copyright (c) 2005, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.filesystem;

import java.net.URI;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.IFileSystem;
import org.eclipse.core.filesystem.provider.FileSystem;
import org.eclipse.core.runtime.IPath;

/**
 * The null file system.
 * @see EFS#getNullFileSystem()
 */
public class NullFileSystem extends FileSystem {

	/**
	 * Creates the null file system.
	 */
	public NullFileSystem() {
		super();
	}

	/**
	 * Returns the instance of this file system
	 *
	 * @return The instance of this file system.
	 */
	@Deprecated(forRemoval = true, since = "will be deleted 2024-06")
	public static IFileSystem getInstance() {
		return EFS.getNullFileSystem();
	}

	@Override
	public IFileStore getStore(IPath path) {
		return new NullFileStore(path, this);
	}

	@Override
	public IFileStore getStore(URI uri) {
		return getStore(IPath.fromOSString(uri.getPath()));
	}
}
