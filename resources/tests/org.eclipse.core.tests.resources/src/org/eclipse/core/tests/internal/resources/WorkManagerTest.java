/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     GitHub Copilot - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.internal.resources;

import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createInWorkspace;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.core.internal.resources.WorkManager;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.tests.resources.WorkspaceTestRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for WorkManager to verify proper handling of concurrent operations
 * and rule lifecycle management, specifically for the race condition fixed
 * in the checkInFailed() method.
 */
public class WorkManagerTest {

	@Rule
	public WorkspaceTestRule workspaceRule = new WorkspaceTestRule();

	/**
	 * Test for the race condition in WorkManager.checkInFailed().
	 * 
	 * This test reproduces the exact scenario where:
	 * 1. Thread 1 calls checkIn() which successfully calls beginRule()
	 * 2. Thread 1's checkIn() then fails (e.g., at lock.acquire())
	 * 3. Thread 1 sets checkInFailed flag
	 * 4. The workspace tree becomes locked (e.g., during resource change notification)
	 * 5. Thread 1 calls checkInFailed() for cleanup
	 * 
	 * Without the fix (using workspace.isTreeLocked()):
	 * - checkInFailed() sees tree is locked
	 * - Skips calling endRule() even though beginRule() was called
	 * - This orphans the rule
	 * - Next endRule() call fails with "endRule without matching beginRule"
	 * 
	 * With the fix (using beginRuleCalled ThreadLocal):
	 * - checkInFailed() checks if beginRule() was actually called
	 * - Calls endRule() correctly regardless of tree lock status
	 * - No orphaned rule
	 */
	@Test
	public void testCheckInFailedWithTreeLockDoesNotOrphanRule() throws Exception {
		IProject project = getWorkspace().getRoot().getProject("TestProject");
		createInWorkspace(project);
		
		final Workspace workspace = (Workspace) getWorkspace();
		final ISchedulingRule rule = project;
		
		// Latches to synchronize test execution
		final CountDownLatch beginRuleCalled = new CountDownLatch(1);
		final CountDownLatch lockTreeNow = new CountDownLatch(1);
		final CountDownLatch treeIsLocked = new CountDownLatch(1);
		final CountDownLatch cleanupComplete = new CountDownLatch(1);
		final AtomicReference<Throwable> error = new AtomicReference<>();
		
		// Listener that will lock the tree when signaled
		IResourceChangeListener treeLockingListener = new IResourceChangeListener() {
			@Override
			public void resourceChanged(IResourceChangeEvent event) {
				try {
					// Wait for signal to lock the tree
					if (lockTreeNow.await(5, TimeUnit.SECONDS)) {
						// Tree is now locked during this notification
						treeIsLocked.countDown();
						// Keep it locked until cleanup is done
						cleanupComplete.await(5, TimeUnit.SECONDS);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		};
		
		workspace.addResourceChangeListener(treeLockingListener, IResourceChangeEvent.POST_CHANGE);
		
		try {
			// Thread that simulates the race condition
			Thread testThread = new Thread(() -> {
				try {
					WorkManager workManager = workspace.getWorkManager();
					
					// Step 1: Acquire the rule (this calls beginRule internally)
					Job.getJobManager().beginRule(rule, null);
					beginRuleCalled.countDown();
					
					// Step 2: Simulate that checkIn partially succeeded but then failed
					// We do this by directly manipulating the ThreadLocal flags using reflection
					setCheckInFailed(workManager, true);
					setBeginRuleCalled(workManager, true);
					
					// Step 3: Trigger a notification to lock the tree
					// We do this by making a small change
					IFile triggerFile = project.getFile("trigger.txt");
					if (!triggerFile.exists()) {
						triggerFile.create("trigger".getBytes(), true, null);
					}
					
					// Signal the listener to lock the tree
					lockTreeNow.countDown();
					
					// Wait for tree to be locked
					if (!treeIsLocked.await(5, TimeUnit.SECONDS)) {
						error.set(new AssertionError("Tree did not lock in time"));
						return;
					}
					
					// Step 4: Now call checkInFailed - this is where the bug would manifest
					// With bug: sees tree is locked, skips endRule(), orphans the rule
					// With fix: checks beginRuleCalled flag, calls endRule() correctly
					boolean failed = workManager.checkInFailed(rule);
					
					if (!failed) {
						error.set(new AssertionError("checkInFailed should have returned true"));
						return;
					}
					
					cleanupComplete.countDown();
					
					// Step 5: Try to call endRule again - this should work
					// If the rule was orphaned (bug), this will throw IllegalArgumentException
					// If the fix works, the first endRule was called, so this one will fail
					// with the expected error (which we catch and verify)
					try {
						Job.getJobManager().endRule(rule);
						// If we get here, it means the first endRule() was NOT called (bug!)
						error.set(new AssertionError(
								"Second endRule() succeeded, which means first endRule() was not called. "
								+ "This indicates the bug is present: checkInFailed() did not call endRule() "
								+ "even though beginRule() was called."));
					} catch (IllegalArgumentException e) {
						// This is expected! It means the first endRule() WAS called (fix working)
						if (!e.getMessage().contains("endRule without matching beginRule")) {
							error.set(e);
						}
						// Otherwise, this is the expected error - the fix is working!
					}
					
				} catch (Throwable t) {
					error.set(t);
				}
			}, "WorkManager-Race-Test");
			
			testThread.start();
			testThread.join(15000);
			
			if (!testThread.isAlive() && error.get() != null) {
				fail("Test failed: " + error.get().getMessage());
			}
			
			if (testThread.isAlive()) {
				testThread.interrupt();
				fail("Test timed out");
			}
			
		} finally {
			workspace.removeResourceChangeListener(treeLockingListener);
		}
	}
	
	/**
	 * Helper method to set the checkInFailed ThreadLocal using reflection
	 */
	private void setCheckInFailed(WorkManager workManager, boolean value) throws Exception {
		Field field = WorkManager.class.getDeclaredField("checkInFailed");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		ThreadLocal<Boolean> threadLocal = (ThreadLocal<Boolean>) field.get(workManager);
		if (value) {
			threadLocal.set(Boolean.TRUE);
		} else {
			threadLocal.remove();
		}
	}
	
	/**
	 * Helper method to set the beginRuleCalled ThreadLocal using reflection
	 */
	private void setBeginRuleCalled(WorkManager workManager, boolean value) throws Exception {
		Field field = WorkManager.class.getDeclaredField("beginRuleCalled");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		ThreadLocal<Boolean> threadLocal = (ThreadLocal<Boolean>) field.get(workManager);
		if (value) {
			threadLocal.set(Boolean.TRUE);
		} else {
			threadLocal.remove();
		}
	}
}
