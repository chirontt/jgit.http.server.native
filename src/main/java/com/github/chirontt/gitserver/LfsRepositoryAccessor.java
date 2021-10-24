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

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lfs.errors.LfsRepositoryNotFound;
import org.eclipse.jgit.lfs.errors.LfsRepositoryReadOnly;
import org.eclipse.jgit.lfs.errors.LfsUnavailable;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.github.chirontt.lfs.server.RepositoryAccessor;

/**
 * An implementation of {@link RepositoryAccessor} with the following
 * repository access rules:
 * 
 * - Reading is permitted by default for all repositories,
 * unless 'http.uploadpack=false' is set for a specific repository.
 *
 * - Writing is permitted if any of the following is true:
 *   * the servlet container has authenticated the user, and has set
 *     HttpServletRequest.remoteUser field to the authenticated name
 *   * the repository's configuration has 'http.receivepack=true' setting;
 * otherwise repository updating is explicitly rejected.
 *
 */
public class LfsRepositoryAccessor implements RepositoryAccessor {

	private Path repoPath;
    private FileRepositoryBuilder repositoryBuilder;

	public LfsRepositoryAccessor(Path repoPath) {
        this.repoPath = repoPath;
        this.repositoryBuilder =
                new FileRepositoryBuilder().setGitDir(repoPath.toFile())
                                           .setMustExist(true);
    }

    /** {@inheritDoc} */
	@Override
    public void checkReadAccess(String refName, String username)
            throws LfsException {
        //check that the repository is readable
    	//i.e. not having http.uploadpack=false setting
        try {
            if (!getRepoConfigBooleanValue("http", "uploadpack", true)) {
                throw new LfsUnavailable(repoPath.getFileName().toString());
            }
        } catch (IOException e) {
            throw new LfsRepositoryNotFound(repoPath.getFileName().toString());
        }
	}

    /** {@inheritDoc} */
	@Override
    public void checkWriteAccess(String refName, String username)
            throws LfsException {
        //first, check that the repository is writable
        //for unauthenticated user
        if (username == null) {
            //the user is not authenticated;
            //check if the repository allows upload
        	//i.e. having http.receivepack=true setting
            try {
                if (!getRepoConfigBooleanValue("http", "receivepack", false)) {
                    throw new LfsRepositoryReadOnly(repoPath.getFileName().toString());
                }
            } catch (IOException e) {
                throw new LfsRepositoryNotFound(repoPath.getFileName().toString());
            }
        }
        //second, if the ref property is present in the LFS request,
        //check that the ref is writable by the user
        if (refName != null) {
            //does the user have write access to the ref?
            if (!hasWriteAccessToRef(username, refName)) {
                throw new LfsRepositoryReadOnly(
                        repoPath.getFileName().toString() + ", for ref " + refName + ",");
            }
        }
    }

    /**
     * Is writable by any user for any ref, in this implementation!
     */
    @Override
    public boolean hasWriteAccessToRef(String username, String refName)
            throws LfsException {
        return true;
    }

    private boolean getRepoConfigBooleanValue(String section, String name, boolean defaultValue)
            throws IOException {
        try (Repository repository = repositoryBuilder.build()) {
            repository.getConfig();
            return repository.getConfig().getBoolean(section, name, defaultValue);
        }
    }

}
