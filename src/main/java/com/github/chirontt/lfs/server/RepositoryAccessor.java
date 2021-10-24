/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.lfs.server;

import org.eclipse.jgit.lfs.errors.LfsException;

/**
 * Abstraction of an accessor for determining read/write access
 * to a git repository.
 *
 */
public interface RepositoryAccessor {

    /**
     * Check for read access to the repository
     * 
     * @param refName
     * @param username
     * 
     * @throws LfsException if access not allowed
     */
    void checkReadAccess(String refName, String username) throws LfsException;

    /**
     * Check for write access to the repository
     * 
     * @param refName
     * @param username
     * 
     * @throws LfsException if access not allowed
     */
    void checkWriteAccess(String refName, String username) throws LfsException;

    /**
     * Check whether the username has write access to the ref in the repository.
     * 
     * @param username
     * @param refName
     * 
     * @return true - if write access is allowed,
     *         false - otherwise
     * 
     * @throws LfsException if error happens in the check
     */
    boolean hasWriteAccessToRef(String username, String refName) throws LfsException;
}
