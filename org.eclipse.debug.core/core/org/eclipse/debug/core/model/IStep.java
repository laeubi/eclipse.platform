package org.eclipse.debug.core.model;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.debug.core.DebugException;

/**
 * Provides the ability to step into, over, and return
 * from the current execution location.
 * <p>
 * Clients may implement this interface.
 * </p>
 * <p>
 * <b>Note:</b> This class/interface is part of an interim API that is still under development and expected to 
 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback 
 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
 * (repeatedly) as the API evolves.
 * </p>
 */
public interface IStep {
	/**
	 * Returns whether this element can currently perform a step into.
	 *
	 * @return whether this element can currently perform a step into
	 */
	public boolean canStepInto();
	/**
	 * Returns whether this element can currently perform a step over.
	 *
	 * @return whether this element can currently perform a step over
	 */
	public boolean canStepOver();
	/**
	 * Returns whether this element can currently perform a step return.
	 *
	 * @return whether this element can currently perform a step return
	 */
	public boolean canStepReturn();
	/**
	 * Returns whether this element is currently stepping.
	 * <p>
	 * For example, a thread is considered to be stepping
	 * after the <code>stepOver</code> call until the step over is completed,
	 * a breakpoint is reached, an exception is thrown, or the thread or is
	 * terminated.
	 * </p>
	 *
	 * @return whether this element is currently stepping
	 */
	public boolean isStepping();
	/**
	 * Steps into the current statement, generating <code>RESUME</code>
	 * and <code>SUSPEND</code> events for the associated thread. Can only be called
	 * when the associated thread is suspended. Implementations may choose to implement
	 * stepping as blocking or non-blocking.
	 *
	 * @exception DebugException on failure. Reasons include:<ul>
	 * <li>TARGET_REQUEST_FAILED - The request failed in the target</li>
	 * <li>NOT_SUPPORTED - The capability is not supported by the target</li>
	 * </ul>
	 */
	public void stepInto() throws DebugException;
	/**
	 * Steps over the current statement, generating <code>RESUME</code>
	 * and <code>SUSPEND</code> events for the associated thread. Can only be called
	 * when the associated thread is suspended. Implementations may choose to implement
	 * stepping as blocking or non-blocking.
	 *
	 * @exception DebugException on failure. Reasons include:<ul>
	 * <li>TARGET_REQUEST_FAILED - The request failed in the target</li>
	 * <li>NOT_SUPPORTED - The capability is not supported by the target</li>
	 * </ul>
	 */
	public void stepOver() throws DebugException;
	/**
	 * Steps to the next return statement in the current scope,
	 * generating <code>RESUME</code> and <code>SUSPEND</code> events for
	 * the associated thread. Can only be called when the associated thread is suspended.
	 * Implementations may choose to implement stepping as blocking or non-blocking.
	 *
	 * @exception DebugException on failure. Reasons include:<ul>
	 * <li>TARGET_REQUEST_FAILED - The request failed in the target</li>
	 * <li>NOT_SUPPORTED - The capability is not supported by the target</li>
	 * </ul>
	 */
	public void stepReturn() throws DebugException;
}
