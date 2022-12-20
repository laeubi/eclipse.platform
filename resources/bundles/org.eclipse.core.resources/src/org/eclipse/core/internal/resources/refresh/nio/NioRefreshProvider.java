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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.refresh.*;
import org.eclipse.core.runtime.IProgressMonitor;

public class NioRefreshProvider extends RefreshProvider {

	private Map<IRefreshResult, NioRefreshMonitor> monitors = new ConcurrentHashMap<>();

	@Override
	public IRefreshMonitor installMonitor(IResource resource, IRefreshResult result, IProgressMonitor progressMonitor) {
		NioRefreshMonitor monitor = monitors.computeIfAbsent(result, NioRefreshMonitor::new);
		if (monitor.monitor(resource, progressMonitor)) {
			return monitor;
		}
		return null;
	}

}
