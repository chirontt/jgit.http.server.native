/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.gitserver;

import java.nio.file.Path;

import org.eclipse.jgit.lfs.errors.LfsException;

import com.github.chirontt.lfs.server.RepositoryAccessor;
import com.github.chirontt.lfs.server.locks.LfsFileLockingProtocolServlet;
import com.github.chirontt.lfs.server.locks.LockManager;

/**
 * Simple LFS File Locking servlet where LFS locks are persisted
 * to the file system.
 *
 */
public class LfsFileLockingServlet extends LfsFileLockingProtocolServlet {

    private static final long serialVersionUID = 1L;

    private LockManager lockManager;
    private RepositoryAccessor repoAccessor;

    public LfsFileLockingServlet(LockManager lockManager, Path repoPath) {
        this.lockManager = lockManager;
        this.repoAccessor = new LfsRepositoryAccessor(repoPath);
    }

    @Override
    protected LockManager getLockManager() throws LfsException {
        return lockManager;
    }

    @Override
    protected RepositoryAccessor getRepositoryAccessor(String path)
            throws LfsException {
        return repoAccessor;
    }

}
