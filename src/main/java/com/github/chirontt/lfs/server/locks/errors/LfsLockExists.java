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

import org.eclipse.jgit.lfs.errors.LfsException;

import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Lock;

public class LfsLockExists extends LfsException {
    private static final long serialVersionUID = 1L;

    private Lock lock;

	/**
	 * Constructor for LfsLockExists exception.
	 *
	 * @param message
	 *            error message, which may be shown to an end-user.
	 * @param lock
	 *            the existing lock.
	 */
    public LfsLockExists(String message, Lock lock) {
        super(message);
        this.lock = lock;
    }

    public Lock getLock() {
        return lock;
    }
}
