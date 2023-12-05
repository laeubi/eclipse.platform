/*******************************************************************************
 *  Copyright (c) 2000, 2012 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.resources.regression;

import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.junit.Assert.assertThrows;

import java.io.InputStream;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.internal.resources.Resource;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform.OS;
import org.eclipse.core.tests.resources.ResourceTest;

/**
 * When moving a resource "x" from parent "a" to parent "b", if "x" or any of
 * its children can't be deleted, both "a" and "b" become out-of-sync and resource info is lost.
 */
public class Bug_032076 extends ResourceTest {

	public void testFileBugOnWindows() throws Exception {
		if (!OS.isWindows()) {
			return;
		}

		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject(getUniqueString());
		IFolder sourceParent = project.getFolder("source_parent");
		IFolder destinationParent = project.getFolder("destination_parent");
		// this file will be made irremovable
		IFile sourceFile = sourceParent.getFile("file1.txt");
		IFile destinationFile = destinationParent.getFile(sourceFile.getName());

		ensureExistsInWorkspace(new IResource[] { sourceFile, destinationParent }, true);
		deleteOnTearDown(project.getLocation());

		// add a marker to a file to ensure the move operation is not losing anything
		String attributeKey = getRandomString();
		String attributeValue = getRandomString();
		long markerId = -1;
		IMarker bookmark = sourceFile.createMarker(IMarker.BOOKMARK);
		bookmark.setAttribute(attributeKey, attributeValue);
		markerId = bookmark.getId();

		// opens the file so it cannot be removed on Windows
		try (InputStream input = sourceFile.getContents()) {
			assertThrows(CoreException.class,
					() -> sourceFile.move(destinationFile.getFullPath(), IResource.FORCE, getMonitor()));

			// the source parent is in sync
			assertTrue("3.0", sourceParent.isSynchronized(IResource.DEPTH_INFINITE));
			// the target parent is in sync
			assertTrue("3.1", destinationParent.isSynchronized(IResource.DEPTH_INFINITE));

			// file has been copied to destination
			assertTrue("3.4", destinationFile.exists());

			// ensure marker info has not been lost
			IMarker marker = destinationFile.findMarker(markerId);
			assertNotNull("3.6", marker);
			assertEquals("3.7", attributeValue, marker.getAttribute(attributeKey));

			// non-removable file has been moved (but not in file system - they are out-of-sync)
			assertTrue("4.1", sourceFile.exists());
			assertTrue("4.2", sourceFile.isSynchronized(IResource.DEPTH_ZERO));

			// refresh the source parent
			sourceParent.refreshLocal(IResource.DEPTH_INFINITE, getMonitor());

			// file is still found in source tree
			assertTrue("4.7", sourceFile.exists());
		}
	}

	public void testFolderBugOnWindows() throws Exception {
		if (!OS.isWindows()) {
			return;
		}

		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject(getUniqueString());
		IFolder sourceParent = project.getFolder("source_parent");
		IFolder destinationParent = project.getFolder("destination_parent");
		IFolder folder = sourceParent.getFolder("folder");
		IFolder destinationFolder = destinationParent.getFolder(folder.getName());
		// this file will be made un-removable
		IFile file1 = folder.getFile("file1.txt");
		// but not this one
		IFile file2 = folder.getFile("file2.txt");

		ensureExistsInWorkspace(new IResource[] { file1, file2, destinationParent }, true);
		deleteOnTearDown(project.getLocation());

		// add a marker to a file to ensure the move operation is not losing anything
		String attributeKey = getRandomString();
		String attributeValue = getRandomString();
		long markerId = -1;
		IMarker bookmark = file1.createMarker(IMarker.BOOKMARK);
		bookmark.setAttribute(attributeKey, attributeValue);
		markerId = bookmark.getId();

		// opens a file so it (and its parent) cannot be removed on Windows
		try (InputStream input = file1.getContents()) {
			assertThrows(CoreException.class,
					() -> folder.move(destinationFolder.getFullPath(), IResource.FORCE, getMonitor()));

			// the source parent is in sync
			assertTrue("3.0", sourceParent.isSynchronized(IResource.DEPTH_INFINITE));
			// the target parent is in-sync
			assertTrue("3.1", destinationParent.isSynchronized(IResource.DEPTH_INFINITE));

			// resources have been copied to destination
			assertTrue("3.3", destinationFolder.exists());
			assertTrue("3.4", destinationFolder.getFile(file1.getName()).exists());
			assertTrue("3.5", destinationFolder.getFile(file2.getName()).exists());

			// ensure marker info has not been lost
			IMarker marker = destinationFolder.getFile(file1.getName()).findMarker(markerId);
			assertNotNull("3.6", marker);
			assertEquals("3.7", attributeValue, marker.getAttribute(attributeKey));

			// non-removable resources still exist in source
			assertTrue("4.1", folder.exists());
			assertTrue("4.2", file1.exists());
			//this file should be successfully moved
			assertTrue("4.3", !file2.exists());

			// refresh the source parent
			sourceParent.refreshLocal(IResource.DEPTH_INFINITE, getMonitor());

			// non-removable resources still in source tree
			assertTrue("4.6", folder.exists());
			assertTrue("4.7", file1.exists());
			assertTrue("4.8", !file2.exists());
		}
	}

	public void testProjectBugOnWindows() throws Exception {
		if (!OS.isWindows()) {
			return;
		}

		IWorkspace workspace = getWorkspace();
		IProject sourceProject = workspace.getRoot().getProject(getUniqueString() + ".source");
		IProject destinationProject = workspace.getRoot().getProject(getUniqueString() + ".dest");
		// this file will be made un-removable
		IFile file1 = sourceProject.getFile("file1.txt");
		// but not this one
		IFile file2 = sourceProject.getFile("file2.txt");

		ensureExistsInWorkspace(new IResource[] {file1, file2}, true);
		deleteOnTearDown(sourceProject.getLocation()); // Ensure project location is moved after test

		// add a marker to a file to ensure the move operation is not losing anything
		String attributeKey = getRandomString();
		String attributeValue = getRandomString();
		long markerId = -1;
		IMarker bookmark = file1.createMarker(IMarker.BOOKMARK);
		bookmark.setAttribute(attributeKey, attributeValue);
		markerId = bookmark.getId();

		// opens a file so it (and its parent) cannot be removed on Windows
		try (InputStream input = file1.getContents()) {

			assertThrows(CoreException.class,
					() -> sourceProject.move(destinationProject.getFullPath(), IResource.FORCE, getMonitor()));

			// the source does not exist
			assertTrue("3.0", !sourceProject.exists());
			assertTrue("3.1", sourceProject.isSynchronized(IResource.DEPTH_INFINITE));
			// the target exists and is in sync
			assertTrue("3.2", destinationProject.exists());
			assertTrue("3.3", destinationProject.isSynchronized(IResource.DEPTH_INFINITE));

			// resources have been copied to destination
			assertTrue("3.4", destinationProject.getFile(file1.getProjectRelativePath()).exists());
			assertTrue("3.5", destinationProject.getFile(file2.getProjectRelativePath()).exists());

			// ensure marker info has not been lost
			IMarker marker = destinationProject.getFile(file1.getProjectRelativePath()).findMarker(markerId);
			assertNotNull("3.6", marker);
			assertEquals("3.7", attributeValue, marker.getAttribute(attributeKey));
			assertTrue("5.0", workspace.getRoot().isSynchronized(IResource.DEPTH_INFINITE));
		}
	}

	/**
	 * TODO: This test is currently failing and needs further investigation (bug 203078)
	 */
	public void _testFileBugOnLinux() throws CoreException {
		if (!(OS.isLinux() && isReadOnlySupported())) {
			return;
		}

		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject(getUniqueString());
		IFolder sourceParent = project.getFolder("source_parent");
		IFolder roFolder = sourceParent.getFolder("sub-folder");
		IFolder destinationParent = project.getFolder("destination_parent");
		// this file will be made un-removable
		IFile sourceFile = roFolder.getFile("file.txt");
		IFile destinationFile = destinationParent.getFile("file.txt");

		ensureExistsInWorkspace(new IResource[] { sourceFile, destinationParent }, true);
		deleteOnTearDown(project.getLocation());

		IFileStore roFolderStore = ((Resource) roFolder).getStore();

		// add a marker to a file to ensure the move operation is not losing anything
		String attributeKey = getRandomString();
		String attributeValue = getRandomString();
		long markerId = -1;
		IMarker bookmark = sourceFile.createMarker(IMarker.BOOKMARK);
		bookmark.setAttribute(attributeKey, attributeValue);
		markerId = bookmark.getId();

		try {
			// mark sub-folder as read-only so its immediate children cannot be removed on Linux
			setReadOnly(roFolder, true);
			assertThrows(CoreException.class,
					() -> sourceFile.move(destinationFile.getFullPath(), IResource.FORCE, getMonitor()));

			// the source parent is out-of-sync
			assertTrue("3.0", !sourceParent.isSynchronized(IResource.DEPTH_INFINITE));
			// the target parent is in-sync
			assertTrue("3.1", destinationParent.isSynchronized(IResource.DEPTH_INFINITE));

			// file has been copied to destination
			assertTrue("3.4", destinationFile.exists());

			// ensure marker info has not been lost
			IMarker marker = destinationFile.findMarker(markerId);
			assertNotNull("3.6", marker);
			assertEquals("3.7", attributeValue, marker.getAttribute(attributeKey));

			// non-removable file has been moved (but not in file system - they are out-of-sync)
			assertTrue("4.1", !sourceFile.exists());

			// refresh the source parent
			sourceParent.refreshLocal(IResource.DEPTH_INFINITE, getMonitor());

			// non-removable file now reappear in the resource tree
			assertTrue("4.7", sourceFile.exists());
		} finally {
			setReadOnly(roFolderStore, false);
		}
	}

	/**
	 * TODO: This test is currently failing and needs further investigation (bug 203078)
	 */
	public void _testFolderBugOnLinux() throws CoreException {
		if (!(OS.isLinux() && isReadOnlySupported())) {
			return;
		}

		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject(getUniqueString());
		IFolder sourceParent = project.getFolder("source_parent");
		IFolder roFolder = sourceParent.getFolder("sub-folder");
		IFolder folder = roFolder.getFolder("folder");
		IFile file1 = roFolder.getFile("file1.txt");
		IFile file2 = folder.getFile("file2.txt");
		IFolder destinationParent = project.getFolder("destination_parent");
		IFolder destinationROFolder = destinationParent.getFolder(roFolder.getName());

		ensureExistsInWorkspace(new IResource[] { file1, file2, destinationParent }, true);
		deleteOnTearDown(project.getLocation());

		IFileStore roFolderLocation = ((Resource) roFolder).getStore();
		IFileStore destinationROFolderLocation = ((Resource) destinationROFolder).getStore();

		// add a marker to a file to ensure the move operation is not losing anything
		String attributeKey = getRandomString();
		String attributeValue = getRandomString();
		long markerId = -1;
		IMarker bookmark = file1.createMarker(IMarker.BOOKMARK);
		bookmark.setAttribute(attributeKey, attributeValue);
		markerId = bookmark.getId();

		try {
			// mark sub-folder as read-only so its immediate children cannot be removed on Linux
			setReadOnly(roFolder, true);

			assertThrows(CoreException.class, () -> roFolder
					.move(destinationParent.getFullPath().append(roFolder.getName()), IResource.FORCE, getMonitor()));

			// the source parent is out-of-sync
			assertTrue("3.0", !sourceParent.isSynchronized(IResource.DEPTH_INFINITE));
			// the target parent is in-sync
			assertTrue("3.1", destinationParent.isSynchronized(IResource.DEPTH_INFINITE));

			// resources have been copied to destination
			IFolder destinationFolder = destinationROFolder.getFolder(folder.getName());
			IFile destinationFile1 = destinationROFolder.getFile(file1.getName());
			IFile destinationFile2 = destinationFolder.getFile(file2.getName());
			assertTrue("3.2", destinationROFolder.exists());
			assertTrue("3.4", destinationFolder.exists());
			assertTrue("3.5", destinationFile1.exists());
			assertTrue("3.6", destinationFile2.exists());

			// ensure marker info has not been lost
			IMarker marker = destinationROFolder.getFile(file1.getName()).findMarker(markerId);
			assertNotNull("3.7", marker);
			assertEquals("3.8", attributeValue, marker.getAttribute(attributeKey));

			// non-removable resources have been moved (but not in file system - they are out-of-sync)
			assertTrue("4.0", !roFolder.exists());
			assertTrue("4.1", !folder.exists());
			assertTrue("4.2", !file1.exists());
			assertTrue("4.3", !file2.exists());

			// refresh the source parent
			sourceParent.refreshLocal(IResource.DEPTH_INFINITE, getMonitor());

			// non-removed resources now reappear in the resource tree
			assertTrue("4.5", roFolder.exists());
			assertTrue("4.6", folder.exists());
			assertTrue("4.7", file1.exists());
			assertTrue("4.8", !file2.exists());
		} finally {
			setReadOnly(roFolderLocation, false);
			setReadOnly(destinationROFolderLocation, false);
		}
	}

	/**
	 * TODO: This test is currently failing and needs further investigation (bug 203078)
	 */
	public void _testProjectBugOnLinux() throws CoreException {
		if (!(OS.isLinux() && isReadOnlySupported())) {
			return;
		}

		IWorkspace workspace = getWorkspace();
		IProject sourceProject = workspace.getRoot().getProject(getUniqueString() + ".source");
		IFileStore projectParentStore = getTempStore();
		IFileStore projectStore = projectParentStore.getChild(sourceProject.getName());
		IProjectDescription sourceDescription = workspace.newProjectDescription(sourceProject.getName());
		sourceDescription.setLocationURI(projectStore.toURI());

		IProject destinationProject = workspace.getRoot().getProject(getUniqueString() + ".dest");
		IProjectDescription destinationDescription = workspace.newProjectDescription(destinationProject.getName());

		// create and open the source project at a non-default location
		sourceProject.create(sourceDescription, getMonitor());
		sourceProject.open(getMonitor());
		deleteOnTearDown(sourceProject.getLocation());

		IFile file1 = sourceProject.getFile("file1.txt");

		ensureExistsInWorkspace(new IResource[] { file1 }, true);

		// add a marker to a file to ensure the move operation is not losing anything
		String attributeKey = getRandomString();
		String attributeValue = getRandomString();
		long markerId = -1;
		IMarker bookmark = file1.createMarker(IMarker.BOOKMARK);
		bookmark.setAttribute(attributeKey, attributeValue);
		markerId = bookmark.getId();

		try {
			// mark sub-folder as read-only so its immediate children cannot be removed on Linux
			setReadOnly(projectParentStore, true);

			assertThrows(CoreException.class,
					() -> sourceProject.move(destinationDescription, IResource.FORCE, getMonitor()));
			deleteOnTearDown(destinationProject.getLocation());

			// the source does not exist
			assertTrue("3.0", !sourceProject.exists());
			// the target exists and is in sync
			assertTrue("3.1", destinationProject.exists());
			assertTrue("3.2", destinationProject.isSynchronized(IResource.DEPTH_INFINITE));

			// resources have been copied to destination
			assertTrue("3.4", destinationProject.getFile(file1.getProjectRelativePath()).exists());

			// ensure marker info has not been lost
			IMarker marker = destinationProject.getFile(file1.getProjectRelativePath()).findMarker(markerId);
			assertNotNull("3.6", marker);
			assertEquals("3.7", attributeValue, marker.getAttribute(attributeKey));
			// project's content area still exists in file system
			assertTrue("4.0", projectStore.fetchInfo().exists());

			assertTrue("5.0", workspace.getRoot().isSynchronized(IResource.DEPTH_INFINITE));
		} finally {
			setReadOnly(projectParentStore, false);
		}
	}
}
