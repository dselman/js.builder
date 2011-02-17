/*******************************************************************************
 * Copyright (c) 2011 Daniel Selman}.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Daniel Selman - initial API and implementation and/or initial documentation
 *******************************************************************************/ 

package org.selman.js.builder;

import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * 
 */
public class BuilderStatus extends Status implements IResourceStatus {
	IPath path;

	public BuilderStatus(int type, int code, IPath path, String message, Throwable exception) {
		super(type, Activator.PLUGIN_ID, code, message, exception);
		this.path = path;
	}

	public BuilderStatus(int code, String message) {
		this(getSeverity(code), code, null, message, null);
	}

	public BuilderStatus(int code, IPath path, String message) {
		this(getSeverity(code), code, path, message, null);
	}

	public BuilderStatus(int code, IPath path, String message, Throwable exception) {
		this(getSeverity(code), code, path, message, exception);
	}

	/**
	 * @see IResourceStatus#getPath()
	 */
	public IPath getPath() {
		return path;
	}

	protected static int getSeverity(int code) {
		return code == 0 ? 0 : 1 << (code % 100 / 33);
	}

	// for debug only
	private String getTypeName() {
		switch (getSeverity()) {
			case IStatus.OK :
				return "OK"; //$NON-NLS-1$
			case IStatus.ERROR :
				return "ERROR"; //$NON-NLS-1$
			case IStatus.INFO :
				return "INFO"; //$NON-NLS-1$
			case IStatus.WARNING :
				return "WARNING"; //$NON-NLS-1$
			default :
				return String.valueOf(getSeverity());
		}
	}

	// for debug only
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("[type: "); //$NON-NLS-1$
		sb.append(getTypeName());
		sb.append("], [path: "); //$NON-NLS-1$
		sb.append(getPath());
		sb.append("], [message: "); //$NON-NLS-1$
		sb.append(getMessage());
		sb.append("], [plugin: "); //$NON-NLS-1$
		sb.append(getPlugin());
		sb.append("], [exception: "); //$NON-NLS-1$
		sb.append(getException());
		sb.append("]\n"); //$NON-NLS-1$
		return sb.toString();
	}
}
