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
package org.eclipse.core.tests.internal.resources;

import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createInWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createTestMonitor;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createUniqueString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.tests.resources.WorkspaceTestRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * Tests for hierarchical project preferences feature.
 * 
 * @since 3.20
 */
public class HierarchicalProjectPreferencesTest {

	@Rule
	public WorkspaceTestRule workspaceRule = new WorkspaceTestRule();

	private boolean originalHierarchicalPrefsEnabled;

	@Before
	public void setUp() throws BackingStoreException {
		// Save original setting
		Preferences instancePrefs = Platform.getPreferencesService().getRootNode()
				.node(InstanceScope.SCOPE)
				.node(ResourcesPlugin.PI_RESOURCES);
		originalHierarchicalPrefsEnabled = instancePrefs.getBoolean(
				ResourcesPlugin.PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES,
				ResourcesPlugin.DEFAULT_PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES);
		
		// Enable hierarchical preferences for tests
		instancePrefs.putBoolean(ResourcesPlugin.PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES, true);
		instancePrefs.flush();
	}

	@After
	public void tearDown() throws BackingStoreException {
		// Restore original setting
		Preferences instancePrefs = Platform.getPreferencesService().getRootNode()
				.node(InstanceScope.SCOPE)
				.node(ResourcesPlugin.PI_RESOURCES);
		instancePrefs.putBoolean(ResourcesPlugin.PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES,
				originalHierarchicalPrefsEnabled);
		instancePrefs.flush();
	}

	/**
	 * Test simple nesting: project A nested in project B
	 * <pre>
	 * /workspace
	 *   /projectB (root)
	 *     /projectA (nested in B)
	 * </pre>
	 */
	@Test
	public void testSimpleNesting() throws Exception {
		String qualifier = "test.qualifier." + createUniqueString();
		String key = "testKey";
		String valueB = "valueFromB";
		String valueA = "valueFromA";

		// Create projects with nesting relationship
		IProject projectB = getProject("projectB_" + createUniqueString());
		IProject projectA = getProject("projectA_" + createUniqueString());

		createInWorkspace(projectB);
		createProjectAtLocation(projectA, projectB.getLocation().append(projectA.getName()));

		// Set preference in project B
		IScopeContext contextB = new ProjectScope(projectB);
		Preferences nodeB = contextB.getNode(qualifier);
		nodeB.put(key, valueB);
		nodeB.flush();

		// Project A should inherit from B
		IScopeContext contextA = new ProjectScope(projectA);
		Preferences nodeA = contextA.getNode(qualifier);
		assertEquals("Project A should inherit value from B", valueB, nodeA.get(key, null));

		// Set preference in project A to override B
		nodeA.put(key, valueA);
		nodeA.flush();

		// Project A should now have its own value
		nodeA = new ProjectScope(projectA).getNode(qualifier);
		assertEquals("Project A should have its own value", valueA, nodeA.get(key, null));

		// Project B should still have its original value
		nodeB = new ProjectScope(projectB).getNode(qualifier);
		assertEquals("Project B should keep its value", valueB, nodeB.get(key, null));
	}

	/**
	 * Test three-level nesting: C nested in B nested in A
	 * <pre>
	 * /workspace
	 *   /projectA (root)
	 *     /projectB (nested in A)
	 *       /projectC (nested in B, also nested in A)
	 * </pre>
	 */
	@Test
	public void testThreeLevelNesting() throws Exception {
		String qualifier = "test.qualifier." + createUniqueString();
		String key1 = "key1";
		String key2 = "key2";
		String key3 = "key3";
		String valueA1 = "valueFromA_key1";
		String valueA2 = "valueFromA_key2";
		String valueB2 = "valueFromB_key2";
		String valueB3 = "valueFromB_key3";
		String valueC3 = "valueFromC_key3";

		// Create projects with three-level nesting
		IProject projectA = getProject("projectA_" + createUniqueString());
		IProject projectB = getProject("projectB_" + createUniqueString());
		IProject projectC = getProject("projectC_" + createUniqueString());

		createInWorkspace(projectA);
		createProjectAtLocation(projectB, projectA.getLocation().append(projectB.getName()));
		createProjectAtLocation(projectC, projectB.getLocation().append(projectC.getName()));

		// Set preferences in project A
		Preferences nodeA = new ProjectScope(projectA).getNode(qualifier);
		nodeA.put(key1, valueA1);
		nodeA.put(key2, valueA2);
		nodeA.flush();

		// Set preferences in project B (overrides key2, adds key3)
		Preferences nodeB = new ProjectScope(projectB).getNode(qualifier);
		nodeB.put(key2, valueB2);
		nodeB.put(key3, valueB3);
		nodeB.flush();

		// Set preferences in project C (overrides key3)
		Preferences nodeC = new ProjectScope(projectC).getNode(qualifier);
		nodeC.put(key3, valueC3);
		nodeC.flush();

		// Project C should have:
		// - key1 from A
		// - key2 from B (overriding A)
		// - key3 from C (overriding B)
		nodeC = new ProjectScope(projectC).getNode(qualifier);
		assertEquals("C should inherit key1 from A", valueA1, nodeC.get(key1, null));
		assertEquals("C should inherit key2 from B", valueB2, nodeC.get(key2, null));
		assertEquals("C should have its own key3", valueC3, nodeC.get(key3, null));
	}

	/**
	 * Test nested project without preference file inherits from ancestor
	 * <pre>
	 * /workspace
	 *   /projectParent (has prefs)
	 *     /projectChild (no prefs file, should inherit)
	 * </pre>
	 */
	@Test
	public void testNestedProjectWithoutPreferenceFile() throws Exception {
		String qualifier = "test.qualifier." + createUniqueString();
		String key = "testKey";
		String valueParent = "valueFromParent";

		// Create projects
		IProject projectParent = getProject("projectParent_" + createUniqueString());
		IProject projectChild = getProject("projectChild_" + createUniqueString());

		createInWorkspace(projectParent);
		createProjectAtLocation(projectChild, projectParent.getLocation().append(projectChild.getName()));

		// Set preference in parent
		Preferences nodeParent = new ProjectScope(projectParent).getNode(qualifier);
		nodeParent.put(key, valueParent);
		nodeParent.flush();

		// Child should inherit even without its own preference file
		Preferences nodeChild = new ProjectScope(projectChild).getNode(qualifier);
		assertEquals("Child should inherit from parent", valueParent, nodeChild.get(key, null));
	}

	/**
	 * Test that middle level without preference file still allows inheritance
	 * <pre>
	 * /workspace
	 *   /projectA (has prefs)
	 *     /projectB (no prefs file)
	 *       /projectC (should inherit from A through B)
	 * </pre>
	 */
	@Test
	public void testMiddleLevelWithoutPreferenceFile() throws Exception {
		String qualifier = "test.qualifier." + createUniqueString();
		String key = "testKey";
		String valueA = "valueFromA";

		// Create projects
		IProject projectA = getProject("projectA_" + createUniqueString());
		IProject projectB = getProject("projectB_" + createUniqueString());
		IProject projectC = getProject("projectC_" + createUniqueString());

		createInWorkspace(projectA);
		createProjectAtLocation(projectB, projectA.getLocation().append(projectB.getName()));
		createProjectAtLocation(projectC, projectB.getLocation().append(projectC.getName()));

		// Set preference in project A
		Preferences nodeA = new ProjectScope(projectA).getNode(qualifier);
		nodeA.put(key, valueA);
		nodeA.flush();

		// Project B has no preference file but is in the chain
		Preferences nodeB = new ProjectScope(projectB).getNode(qualifier);
		assertEquals("B should inherit from A", valueA, nodeB.get(key, null));

		// Project C should also inherit from A (through B)
		Preferences nodeC = new ProjectScope(projectC).getNode(qualifier);
		assertEquals("C should inherit from A through B", valueA, nodeC.get(key, null));
	}

	/**
	 * Test that preference files are not modified when inheriting values
	 * <pre>
	 * /workspace
	 *   /projectParent (has prefs)
	 *     /projectChild (reads but doesn't write inherited prefs)
	 * </pre>
	 */
	@Test
	public void testInheritanceDoesNotModifyFiles() throws Exception {
		String qualifier = "test.qualifier." + createUniqueString();
		String key = "testKey";
		String valueParent = "valueFromParent";

		// Create projects
		IProject projectParent = getProject("projectParent_" + createUniqueString());
		IProject projectChild = getProject("projectChild_" + createUniqueString());

		createInWorkspace(projectParent);
		createProjectAtLocation(projectChild, projectParent.getLocation().append(projectChild.getName()));

		// Set preference in parent
		Preferences nodeParent = new ProjectScope(projectParent).getNode(qualifier);
		nodeParent.put(key, valueParent);
		nodeParent.flush();

		// Child reads the inherited value
		Preferences nodeChild = new ProjectScope(projectChild).getNode(qualifier);
		String inheritedValue = nodeChild.get(key, null);
		assertEquals("Child should inherit from parent", valueParent, inheritedValue);

		// Verify child preference file does not exist (not created by inheritance)
		assertEquals("Child should not have a preference file",
				false,
				projectChild.getFile(".settings/" + qualifier + ".prefs").exists());
	}

	/**
	 * Test hierarchical preferences can be disabled
	 * <pre>
	 * /workspace
	 *   /projectParent (has prefs)
	 *     /projectChild (should not inherit when disabled)
	 * </pre>
	 */
	@Test
	public void testHierarchicalPreferencesCanBeDisabled() throws Exception {
		String qualifier = "test.qualifier." + createUniqueString();
		String key = "testKey";
		String valueParent = "valueFromParent";

		// Disable hierarchical preferences
		Preferences instancePrefs = Platform.getPreferencesService().getRootNode()
				.node(InstanceScope.SCOPE)
				.node(ResourcesPlugin.PI_RESOURCES);
		instancePrefs.putBoolean(ResourcesPlugin.PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES, false);
		instancePrefs.flush();

		try {
			// Create projects
			IProject projectParent = getProject("projectParent_" + createUniqueString());
			IProject projectChild = getProject("projectChild_" + createUniqueString());

			createInWorkspace(projectParent);
			createProjectAtLocation(projectChild, projectParent.getLocation().append(projectChild.getName()));

			// Set preference in parent
			Preferences nodeParent = new ProjectScope(projectParent).getNode(qualifier);
			nodeParent.put(key, valueParent);
			nodeParent.flush();

			// Child should NOT inherit when hierarchical prefs are disabled
			Preferences nodeChild = new ProjectScope(projectChild).getNode(qualifier);
			assertNull("Child should not inherit when hierarchical prefs disabled", nodeChild.get(key, null));
		} finally {
			// Re-enable for other tests
			instancePrefs.putBoolean(ResourcesPlugin.PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES, true);
			instancePrefs.flush();
		}
	}

	/**
	 * Test complex nesting scenario with multiple preferences
	 * <pre>
	 * /workspace
	 *   /projectRoot (has key1, key2)
	 *     /projectMid (has key2, key3)
	 *       /projectLeaf (has key3, key4)
	 * </pre>
	 */
	@Test
	public void testComplexNestingWithMultiplePreferences() throws Exception {
		String qualifier = "test.qualifier." + createUniqueString();

		// Create projects
		IProject projectRoot = getProject("projectRoot_" + createUniqueString());
		IProject projectMid = getProject("projectMid_" + createUniqueString());
		IProject projectLeaf = getProject("projectLeaf_" + createUniqueString());

		createInWorkspace(projectRoot);
		createProjectAtLocation(projectMid, projectRoot.getLocation().append(projectMid.getName()));
		createProjectAtLocation(projectLeaf, projectMid.getLocation().append(projectLeaf.getName()));

		// Set preferences in root
		Preferences nodeRoot = new ProjectScope(projectRoot).getNode(qualifier);
		nodeRoot.put("key1", "rootValue1");
		nodeRoot.put("key2", "rootValue2");
		nodeRoot.flush();

		// Set preferences in mid (override key2, add key3)
		Preferences nodeMid = new ProjectScope(projectMid).getNode(qualifier);
		nodeMid.put("key2", "midValue2");
		nodeMid.put("key3", "midValue3");
		nodeMid.flush();

		// Set preferences in leaf (override key3, add key4)
		Preferences nodeLeaf = new ProjectScope(projectLeaf).getNode(qualifier);
		nodeLeaf.put("key3", "leafValue3");
		nodeLeaf.put("key4", "leafValue4");
		nodeLeaf.flush();

		// Verify leaf has correct values
		nodeLeaf = new ProjectScope(projectLeaf).getNode(qualifier);
		assertEquals("Leaf should inherit key1 from root", "rootValue1", nodeLeaf.get("key1", null));
		assertEquals("Leaf should inherit key2 from mid", "midValue2", nodeLeaf.get("key2", null));
		assertEquals("Leaf should have its own key3", "leafValue3", nodeLeaf.get("key3", null));
		assertEquals("Leaf should have its own key4", "leafValue4", nodeLeaf.get("key4", null));

		// Verify mid has correct values
		nodeMid = new ProjectScope(projectMid).getNode(qualifier);
		assertEquals("Mid should inherit key1 from root", "rootValue1", nodeMid.get("key1", null));
		assertEquals("Mid should have its own key2", "midValue2", nodeMid.get("key2", null));
		assertEquals("Mid should have its own key3", "midValue3", nodeMid.get("key3", null));
		assertNull("Mid should not have key4", nodeMid.get("key4", null));
	}

	private static IProject getProject(String name) {
		return getWorkspace().getRoot().getProject(name);
	}

	private void createProjectAtLocation(IProject project, org.eclipse.core.runtime.IPath location)
			throws CoreException {
		org.eclipse.core.resources.IProjectDescription description = project.getWorkspace()
				.newProjectDescription(project.getName());
		description.setLocation(location);
		project.create(description, createTestMonitor());
		project.open(createTestMonitor());
	}
}
