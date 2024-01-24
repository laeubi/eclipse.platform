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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.IFileSystem;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.filesystem.provider.FileStore;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

/**
 * A null file store represents a file whose location is unknown,
 * such as a location based on an undefined variable.  This store
 * acts much like /dev/null on *nix: writes are ignored, reads return
 * empty streams, and modifications such as delete, mkdir, will fail.
 */
public class NullFileStore extends FileStore {
	private final IPath path;
	protected final IFileSystem fileSystem;

	/**
	 * Creates a null file store
	 * @param path The path of the file in this store
	 */
	public NullFileStore(IPath path, IFileSystem fileSystem) {
		Assert.isNotNull(path);
		Assert.isLegal(fileSystem instanceof NullFileSystem, "fileSystem must extend NullFileSystem"); //$NON-NLS-1$
		this.fileSystem = fileSystem;
		this.path = path;
	}

	/**
	 * For test only! Extenders need to extend {@link #getFileSystem()} as well to return their desired instance of the file system!
	 * @param path The path of the file in this store
	 */
	@Deprecated(forRemoval = true, since = "will be deleted 2024-06")
	public NullFileStore(IPath path) {
		this.fileSystem = EFS.getNullFileSystem();
		this.path = path;
	}

	@Override
	public IFileInfo[] childInfos(int options, IProgressMonitor monitor) {
		return EMPTY_FILE_INFO_ARRAY;
	}

	@Override
	public String[] childNames(int options, IProgressMonitor monitor) {
		return EMPTY_STRING_ARRAY;
	}

	@Override
	public void delete(int options, IProgressMonitor monitor) throws CoreException {
		//super implementation will always fail
		super.delete(options, monitor);
	}

	@Override
	public IFileInfo fetchInfo(int options, IProgressMonitor monitor) {
		FileInfo result = new FileInfo(getName());
		result.setExists(false);
		return result;
	}

	@Override
	public IFileStore getChild(String name) {
		return new NullFileStore(path.append(name), getFileSystem());
	}

	@Override
	public IFileSystem getFileSystem() {
		return fileSystem;
	}

	@Override
	public String getName() {
		return String.valueOf(path.lastSegment());
	}

	@Override
	public IFileStore getParent() {
		return path.segmentCount() == 0 ? null : new NullFileStore(path.removeLastSegments(1), getFileSystem());
	}

	@Override
	public IFileStore mkdir(int options, IProgressMonitor monitor) throws CoreException {
		//super implementation will always fail
		return super.mkdir(options, monitor);
	}

	@Override
	public InputStream openInputStream(int options, IProgressMonitor monitor) {
		return new ByteArrayInputStream(new byte[0]);
	}

	@Override
	public OutputStream openOutputStream(int options, IProgressMonitor monitor) {
		return new OutputStream() {
			@Override
			public void write(int b) {
				//do nothing
			}
		};
	}

	@Override
	public void putInfo(IFileInfo info, int options, IProgressMonitor monitor) throws CoreException {
		//super implementation will always fail
		super.putInfo(info, options, monitor);
	}

	@Override
	public String toString() {
		return path.toString();
	}

	@Override
	public URI toURI() {
		try {
			return new URI(EFS.SCHEME_NULL, null, path.isEmpty() ? "/" : path.toString(), null); //$NON-NLS-1$
		} catch (URISyntaxException e) {
			//should never happen
			Policy.log(IStatus.ERROR, "Invalid URI", e); //$NON-NLS-1$
			return null;
		}
	}
}
