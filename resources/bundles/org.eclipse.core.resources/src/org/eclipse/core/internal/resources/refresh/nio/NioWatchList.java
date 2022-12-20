/*******************************************************************************
 * Copyright (c) 2022 Christoph Läurich and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läurich - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.resources.refresh.nio;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.refresh.IRefreshMonitor;
import org.eclipse.core.resources.refresh.IRefreshResult;

/**
 * A {@link NioWatchList} encapsualtes a {@link IContainer} and all its
 * subfolders to watch.
 */
class NioWatchList {

	private final Map<WatchKey, Mapping> keyMap = new HashMap<>();
	private WatchService watchService;
	private IRefreshResult refreshResult;
	private IRefreshMonitor refreshMonitor;

	public NioWatchList(WatchService watchService, IRefreshResult refreshResult, IRefreshMonitor refreshMonitor) {
		this.watchService = watchService;
		this.refreshResult = refreshResult;
		this.refreshMonitor = refreshMonitor;
	}

	public void dispose() {
		synchronized (keyMap) {
			keyMap.keySet().forEach(WatchKey::cancel);
			keyMap.clear();
		}
	}

	public boolean register(IContainer container, Path path) {
		Mapping root = new Mapping(path, container);
		try {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					Path realtive = path.relativize(dir);
					IContainer folder = root.resolveFolder(realtive);
					register(new Mapping(dir, folder));
					return FileVisitResult.CONTINUE;
				}
			});
			return true;
		} catch (IOException e) {
			refreshResult.monitorFailed(refreshMonitor, container);
			return false;
		}
	}

	private boolean register(Mapping mapping) {
		try {
			WatchKey watchKey = mapping.path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
			synchronized (keyMap) {
				keyMap.put(watchKey, mapping);
			}
			return true;
		} catch (IOException e) {
			refreshResult.monitorFailed(refreshMonitor, mapping.container);
			return false;
		}
	}

	public boolean handle(WatchKey watchKey) {
		Mapping mapping;
		synchronized (keyMap) {
			mapping = keyMap.get(watchKey);
		}
		if (mapping == null) {
			return false;
		}
		boolean requireFullRefresh = false;
		for (WatchEvent<?> event : watchKey.pollEvents()) {
			Kind<?> kind = event.kind();
			if (kind == OVERFLOW) {
				// on overflow we might have missed things
				requireFullRefresh = true;
				break;
			}
			Path relative = (Path) event.context();
			Path path = mapping.resolvePath(relative);
			IResource resourceToRefresh;
			if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
				IContainer folder = mapping.resolveFolder(relative);
				if (kind == ENTRY_CREATE) {
					register(folder, path);
				}
				resourceToRefresh = folder;
			} else {
				resourceToRefresh = mapping.resolveFile(relative);
			}
			refreshResult.refresh(resourceToRefresh);
		}
		if (!watchKey.reset()) {
			// this has become invalid so remove any maping and force a full refresh...
			synchronized (keyMap) {
				keyMap.remove(watchKey);
			}
			requireFullRefresh = true;
		}
		if (requireFullRefresh) {
			refreshResult.refresh(mapping.container);
		}
		return true;
	}

	private static final class Mapping {

		private Path path;
		private IContainer container;

		public Mapping(Path path, IContainer container) {
			this.path = path;
			this.container = container;
		}

		public IResource resolveFile(Path relative) {
			return container.getFile(new org.eclipse.core.runtime.Path(relative.toString()));
		}

		public IContainer resolveFolder(Path relative) {
			String relPath = relative.toString();
			if (relPath.isBlank()) {
				return container;
			}
			return container.getFolder(new org.eclipse.core.runtime.Path(relPath));
		}

		public Path resolvePath(Path relative) {
			return path.resolve(relative);
		}

	}

}
