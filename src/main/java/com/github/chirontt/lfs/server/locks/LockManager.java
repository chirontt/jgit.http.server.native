/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.lfs.server.locks;

import org.eclipse.jgit.lfs.errors.LfsException;

import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.CreatedOrDeletedLock;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Locks;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.LocksToVerify;

/**
 * Abstraction of a manager for the creation/deletion/listing of LFS locks.
 *
 */
public interface LockManager {

    /**
     * Create lock.
     * Servers should ensure that users have push access to the repository, before the call,
     * and that files are locked exclusively to one user.
     * 
     * @param path The path name of the file that is locked. This should be relative
     *             to the root of the repository working directory.
     * @param refName (Optional) Fully-qualified server refspec that the lock belongs to.
     * @param username The user name; can be <code>null</code> for anonymous user.
     * 
     * @return The created lock
     * 
     * @throws LfsException if any error occurs
     */
    CreatedOrDeletedLock createLock(String path, String refName, String username)
    throws LfsException;

    /**
     * Retrieve the current active locks for a repository.
     * Servers should ensure that users have at least pull access to the repository,
     * before the call.
     * 
     * @param path Optional string path to match against locks on the server.
     * @param id Optional string ID to match against a lock on the server.
     * @param cursor Optional string value to continue listing locks.
     *               This value should be the next_cursor from a previous request.
     * @param limit Limit of the number of locks to return. The server should have
     *              its own upper and lower bounds on the supported limits.
     * @param refspec Optional fully qualified server refspec from which to search for locks.
     * 
     * @return Matching Lock objects
     * 
     * @throws LfsException if any error occurs
     */
    Locks listLocks(String path, String id, String cursor, int limit, String refspec)
    throws LfsException;

    /**
     * Delete a lock, given its ID.
     * Servers should ensure that callers have push access to the repository, before the call.
     * They should also prevent a user from deleting another user's lock,
     * unless the force property is given.
     * If the force parameter is omitted, or false, the user should only be allowed
     * to delete locks that they created.
     * Servers may decide that the force parameter can only be true if the user is
     * a lock administrator, which can be determined by a call to the
     * {@link #isLockAdministrator(String)} method.
     *  
     * 
     * @param id The lock's ID string
     * @param refName Optional string describing the server ref that the lock belongs to.
     *                (Added in LFS API v2.4)
     * @param username The user name; can be <code>null</code> for anonymous user.
     * @param force Optional boolean specifying that the user is deleting another user's lock.
     * 
     * @return The deleted lock
     * 
     * @throws LfsException if any error occurs
     */
    CreatedOrDeletedLock deleteLock(String id, String refName, String username, boolean force)
    throws LfsException;

    /**
     * List locks for verification, to check for active locks that can affect a Git push.
     * Servers should ensure that users have push access to the repository, before the call.
     * If a Git push updates any files matching any of "our" locks, Git LFS will list them
     * in the push output, in case the user will want to unlock them after the push.
     * However, any updated files matching one of "their" locks will halt the push.
     * At this point, it is up to the user to resolve the lock conflict with their team.
     * 
     * @param refName Optional string describing the server ref that the locks belong to.
     *                (Added in LFS API v2.4)
     * @param username The user name; can be <code>null</code> for anonymous user.
     * @param cursor Optional cursor to allow pagination. Servers can determine how cursors
     *               are formatted based on how they are stored internally.
     * @param limit Optional limit to how many locks to return.
     * 
     * @return The response includes locks partitioned into ours and theirs properties.
     *         If the server has no locks, it must return an empty array in the ours or theirs properties.
     * 
     * @throws LfsException if any error occurs
     */
    LocksToVerify listLocksToVerify(String refName, String username, String cursor, int limit)
    throws LfsException;

    /**
     * Indicates if the user is a lock administrator, which has special power such as
     * ability to delete another user's lock.
     *  
     * @param username The user name; can be <code>null</code> for anonymous user.
     * 
     * @return true if the user has admin privilege,
     *         false otherwise.
     * 
     * @throws LfsException if any error occurs
     */
    boolean isLockAdministrator(String username) throws LfsException;
}
