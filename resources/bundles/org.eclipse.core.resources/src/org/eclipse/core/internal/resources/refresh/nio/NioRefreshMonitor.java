/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.resources.refresh.nio;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.refresh.IRefreshMonitor;
import org.eclipse.core.resources.refresh.IRefreshResult;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

class NioRefreshMonitor implements IRefreshMonitor {

	private IRefreshResult refreshResult;

	private Map<IResource, NioWatchList> watchLists = new ConcurrentHashMap<>();

	private WatchService watchService;

	public NioRefreshMonitor(IRefreshResult refreshResult) {
		this.refreshResult = refreshResult;
	}

	@Override
	public void unmonitor(IResource resource) {
		synchronized (this) {
			if (resource == null) {
				watchLists.clear();
			} else {
				NioWatchList list = watchLists.remove(resource);
				if (list != null) {
					list.dispose();
				}
			}
			if (watchLists.isEmpty() && watchService != null) {
				try {
					watchService.close();
				} catch (IOException e) {
					// there is really nothing we can do here...
				}
			}
		}
	}

	public boolean monitor(IResource resource, IProgressMonitor progressMonitor) {
		if (resource instanceof IContainer) {
			IPath location = resource.getLocation();
			if (location == null || !resource.exists()) {
				return false;
			}
			File file = location.toFile();
			if (file == null) {
				// for the momment we can/want monitor local files only ... probably one can try
				// to use the URI of the path to lookup a suitable FileSystem to support others
				// as well?
				return false;
			}
			Path path = file.toPath();
			if (Files.isDirectory(path)) {
				NioWatchList watchList;
				synchronized (this) {
					try {
						WatchService ws = getWatchService();
						if (watchLists.containsKey(resource)) {
							// already monitored?
							return true;
						}
						watchList = new NioWatchList(ws, refreshResult, this);
						watchLists.put(resource, watchList);
					} catch (IOException e) {
						return false;
					}
				}
				return watchList.register((IContainer) resource, path);
			}
		}
		return false;
	}

	private WatchService getWatchService() throws IOException {
		if (watchService == null) {
			WatchService ws = FileSystems.getDefault().newWatchService();
			Thread thread = new Thread(() -> {
				while (true) {
					try {
						WatchKey take = ws.take();
						for (NioWatchList wl : watchLists.values()) {
							if (wl.handle(take)) {
								break;
							}
						}
					} catch (InterruptedException e) {
						return;
					} catch (ClosedWatchServiceException e) {
						return;
					}
				}

			});
			thread.setDaemon(true);
			thread.setName("NIO-Event-Monitor"); //$NON-NLS-1$
			thread.start();
			watchService = ws;
		}
		return watchService;
	}

}
