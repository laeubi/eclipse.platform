/*******************************************************************************
 * Copyright (c) 2025 Eclipse contributors and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eclipse contributors - initial API and implementation
 *******************************************************************************/

/**
 * Provides support for integrating Open Test Reporting (OTA4J) format with the
 * Eclipse Unit Test view.
 * <p>
 * This package contains classes that read OTA4J XML event streams and translate
 * them into calls to the {@link org.eclipse.unittest.model.ITestRunSession}
 * API. The main entry points are:
 * </p>
 * <ul>
 * <li>{@link org.eclipse.unittest.ui.ota4j.OpenTestReportingReader} - StAX-based
 * reader for OTA4J event streams</li>
 * <li>{@link org.eclipse.unittest.ui.ota4j.OpenTestReportingClient} - Test
 * runner client implementation for OTA4J</li>
 * </ul>
 * <p>
 * The Open Test Reporting format is a vendor-neutral format for test execution
 * results, defined by the <a href="https://github.com/ota4j-team/open-test-reporting">
 * OTA4J project</a>. This integration allows test frameworks that output OTA4J
 * format to display their results in the Eclipse Unit Test view without requiring
 * framework-specific adapters.
 * </p>
 * <h2>Example Usage</h2>
 * <pre>
 * // Create a test run session
 * ITestRunSession session = ...;
 * 
 * // Create a reader for the OTA4J event stream
 * Reader eventReader = new FileReader("test-results.xml");
 * OpenTestReportingClient client = new OpenTestReportingClient(session, eventReader);
 * 
 * // Start monitoring the event stream
 * client.startMonitoring();
 * </pre>
 *
 * @since 1.0
 */
package org.eclipse.unittest.ui.ota4j;
