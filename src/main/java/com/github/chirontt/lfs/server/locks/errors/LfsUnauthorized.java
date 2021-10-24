/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.lfs.server.locks.errors;

import java.text.MessageFormat;

import org.eclipse.jgit.lfs.errors.LfsException;

import com.github.chirontt.lfs.server.locks.internal.LfsFileLockingText;

public class LfsUnauthorized extends LfsException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for LfsUnauthorized exception.
	 *
	 * @param operation
	 *            the operation that was attempted.
	 * @param path
	 *            the path to lock.
	 */
	public LfsUnauthorized(String operation, String path) {
		super(MessageFormat.format(LfsFileLockingText.get().lfsUnathorized, operation,
				path));
	}
}
