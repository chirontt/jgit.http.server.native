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
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.fs.FileLfsRepository;

import com.github.chirontt.lfs.server.LfsProtocolServletV2;
import com.github.chirontt.lfs.server.RepositoryAccessor;

/**
 * LFS Batch API servlet, to allow separate storage of large objects
 * in the local file system on the server. 
 */
public class LfsBatchServlet extends LfsProtocolServletV2 {

    private static final long serialVersionUID = 1L;

    private FileLfsRepository lfsRepo;
    private RepositoryAccessor repoAccessor;

    public LfsBatchServlet(FileLfsRepository lfsRepo, Path repoPath) {
        this.lfsRepo = lfsRepo;
        this.repoAccessor = new LfsRepositoryAccessor(repoPath);
    }

    @Override
    protected LargeFileRepository getLargeFileRepository(LfsRequestV2 lfsRequest, String path, String auth)
            throws LfsException {
        return lfsRepo;
    }

    @Override
    protected RepositoryAccessor getRepositoryAccessor(String path)
            throws LfsException {
        return repoAccessor;
    }

}
