/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.team.core;

/**
 * An ignore info specifies both the pattern and the enabled state
 * of a globally ignored pattern.
 * 
 * @since 2.0
 */
public interface IIgnoreInfo {
	/**
	 * Return the string specifying the pattern of this ignore. The string
	 * may include the wildcard characters '*' and '?'. If you wish to
	 * include either of these characters verbatim (i.e. you do not wish
	 * them to expand to wildcards), you must escape them with a backslash '\'.
	 * <p>
	 * If you are using string literals in Java to represent the patterns, don't 
	 * forget escape characters are represented by "\\".
	 * 
	 * @return the pattern represented by this ignore info
	 */
	public String getPattern();
	/**
	 * Return whether or not this ignore info is enabled. A disabled ignore
	 * info remains in the global ignore list, but no attempt is made to match
	 * with it to determine resource ignore state.
	 * 
	 * @return whether the ignore info is enabled
	 */
	public boolean getEnabled();
}
