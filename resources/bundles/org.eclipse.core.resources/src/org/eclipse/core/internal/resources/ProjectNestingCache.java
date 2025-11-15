/*******************************************************************************
 * Copyright (c) 2024 Vector Informatik GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Vector Informatik GmbH - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;

/**
 * Cache for project nesting relationships. A project A is nested within a project B
 * if B.getLocation().isPrefixOf(A.getLocation()).
 * 
 * This cache is invalidated when projects are created, deleted, moved, or opened/closed.
 * 
 * @since 3.20
 */
public class ProjectNestingCache {
	/**
	 * Cache mapping from a project to its list of ancestor projects (ordered from immediate parent to root).
	 * The cache is cleared when projects change.
	 */
	private final Map<IProject, List<IProject>> nestingCache = new ConcurrentHashMap<>();

	/**
	 * Returns the list of ancestor projects for the given project, ordered from immediate parent to root.
	 * A project B is an ancestor of project A if B.getLocation().isPrefixOf(A.getLocation()).
	 * 
	 * @param project the project to find ancestors for
	 * @param workspace the workspace containing all projects
	 * @return list of ancestor projects, or empty list if no ancestors exist
	 */
	public List<IProject> getAncestorProjects(IProject project, Workspace workspace) {
		if (project == null || !project.isAccessible()) {
			return Collections.emptyList();
		}

		// Check cache first
		List<IProject> cached = nestingCache.get(project);
		if (cached != null) {
			return cached;
		}

		// Compute ancestor projects
		List<IProject> ancestors = computeAncestorProjects(project, workspace);
		nestingCache.put(project, ancestors);
		return ancestors;
	}

	/**
	 * Computes the ancestor projects for the given project.
	 */
	private List<IProject> computeAncestorProjects(IProject project, Workspace workspace) {
		IPath projectLocation = project.getLocation();
		if (projectLocation == null) {
			return Collections.emptyList();
		}

		List<IProject> ancestors = new ArrayList<>();
		IProject[] allProjects = workspace.getRoot().getProjects();

		for (IProject potentialAncestor : allProjects) {
			if (potentialAncestor.equals(project) || !potentialAncestor.isAccessible()) {
				continue;
			}

			IPath ancestorLocation = potentialAncestor.getLocation();
			// Skip projects with null locations - they cannot be ancestors
			if (ancestorLocation == null) {
				continue;
			}
			
			if (ancestorLocation.isPrefixOf(projectLocation)) {
				ancestors.add(potentialAncestor);
			}
		}

		// Sort by path length (longer paths first = closer ancestors first)
		ancestors.sort((p1, p2) -> {
			IPath loc1 = p1.getLocation();
			IPath loc2 = p2.getLocation();
			// At this point, locations should not be null (filtered above), but be defensive
			if (loc1 == null && loc2 == null) {
				return 0;
			}
			if (loc1 == null) {
				return 1; // null locations go to end
			}
			if (loc2 == null) {
				return -1; // null locations go to end
			}
			// Reverse order: longer paths (closer ancestors) come first
			return Integer.compare(loc2.segmentCount(), loc1.segmentCount());
		});

		return Collections.unmodifiableList(ancestors);
	}

	/**
	 * Clears the cache for the given project.
	 * 
	 * @param project the project whose cache entry should be cleared
	 */
	public void clearCache(IProject project) {
		nestingCache.remove(project);
	}

	/**
	 * Clears the entire cache. Should be called when projects are created, deleted, moved, or closed.
	 */
	public void clearCache() {
		nestingCache.clear();
	}
}
