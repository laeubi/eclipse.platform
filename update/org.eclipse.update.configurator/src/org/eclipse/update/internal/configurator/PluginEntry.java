/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.internal.configurator;


/**
 */
public class PluginEntry {

	private String pluginId;
	private String pluginVersion;
	private boolean isFragment = false;
	private VersionedIdentifier versionId;
	
	public PluginEntry() {
		super();
	}


	/**
	 * Returns the plug-in identifier for this entry.
	 * 
	 * @return the plug-in identifier, or <code>null</code>
	 */
	public String getPluginIdentifier() {
		return pluginId;
	}

	/**
	 * Returns the plug-in version for this entry.
	 * 
	 * @return the plug-in version, or <code>null</code>
	 */
	public String getPluginVersion() {
		return pluginVersion;
	}

	/**
	 * Indicates whether the entry describes a full plug-in, or 
	 * a plug-in fragment.
	 * 
	 * @return <code>true</code> if the entry is a plug-in fragment, 
	 * <code>false</code> if the entry is a plug-in
	 */
	public boolean isFragment() {
		return isFragment;
	}

	/**
	 * Sets the entry plug-in identifier.
	 * Throws a runtime exception if this object is marked read-only.
	 *
	 * @param pluginId the entry identifier.
	 */
	void setPluginIdentifier(String pluginId) {
		this.pluginId = pluginId;
	}

	/**
	 * Sets the entry plug-in version.
	 * Throws a runtime exception if this object is marked read-only.
	 *
	 * @param pluginVersion the entry version.
	 */
	void setPluginVersion(String pluginVersion) {
		this.pluginVersion = pluginVersion;
	}

	/**
	 * Indicates whether this entry represents a fragment or plug-in.
	 * Throws a runtime exception if this object is marked read-only.
	 *
	 * @param isFragment fragment setting
	 */
	public void isFragment(boolean isFragment) {
		this.isFragment = isFragment;
	}

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		String msg = (getPluginIdentifier()!=null)?getPluginIdentifier().toString():"";
		msg += getPluginVersion()!=null?" "+getPluginVersion().toString():"";
		msg += isFragment()?" fragment":" plugin";
		return msg;
	}


	/**
	 * Returns the identifier of this plugin entry
	 */
	public VersionedIdentifier getVersionedIdentifier() {
		if (versionId != null)
			return versionId;

		String id = getPluginIdentifier();
		String ver = getPluginVersion();
		if (id != null && ver != null) {
			try {
				versionId = new VersionedIdentifier(id, ver);
				return versionId;
			} catch (Exception e) {
				System.out.println("Unable to create versioned identifier:" + id + ":" + ver);
			}
		}

		versionId = new VersionedIdentifier("",null);
		return versionId;
	}

	/**
	 * Sets the identifier of this plugin entry. 
	 * 
	 */
	void setVersionedIdentifier(VersionedIdentifier identifier) {
		setPluginIdentifier(identifier.getIdentifier());
		setPluginVersion(identifier.getVersion().toString());
	}	

	/**
	 * Compares two plugin entries for equality
	 * 
	 * @param object plugin entry object to compare with
	 * @return <code>true</code> if the two entries are equal, 
	 * <code>false</code> otherwise
	 */
	public boolean equals(Object object) {
		if (!(object instanceof PluginEntry))
			return false;
		PluginEntry e = (PluginEntry) object;
		return getVersionedIdentifier().equals(e.getVersionedIdentifier());
	}
}