/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.console;

import org.eclipse.jface.text.Position;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IHyperlink;

/**
 * Describes the postition of a hyperlink within the Console's document.
 * 
 * @since 3.1
 */
public class IOConsoleHyperlinkPosition extends Position {
    
	public static final String HYPER_LINK_CATEGORY = ConsolePlugin.getUniqueIdentifier() + ".IO_CONSOLE_HYPERLINK_POSITION"; //$NON-NLS-1$
	
	private IHyperlink fLink = null;

	public IOConsoleHyperlinkPosition(IHyperlink link, int offset, int length) {
		super(offset, length);
		fLink = link;
	}
	
	public IHyperlink getHyperLink() {
		return fLink;
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object arg) {
		return arg instanceof IOConsoleHyperlinkPosition && super.equals(arg) && getHyperLink().equals(((IOConsoleHyperlinkPosition)arg).getHyperLink());
	}

	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return super.hashCode() + getHyperLink().hashCode();
	}

}