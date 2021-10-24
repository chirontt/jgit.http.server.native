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

import com.github.chirontt.lfs.server.LfsRef;

/**
 * LFS File Locking Request POJOs for Gson serialization/de-serialization.
 *
 * See the <a href="https://github.com/git-lfs/git-lfs/blob/main/docs/api/locking.md">LFS File Locking
 * API specification</a>
 *
 */
public interface LfsFileLockingRequest {

    enum LockAction {
        CREATE_LOCK, LIST_LOCKS, DELETE_LOCK, LIST_LOCKS_TO_VERIFY;
    }

    /** Describes the LFS lock to be created */
    class CreateLock {
        //path name of the file that is locked;
        //should be relative to the root of the repository working directory
        String path;
	    //optional object describing the server ref that the locks belong to
        LfsRef ref;

        public String getPath() {
            return path;
		}

        public LfsRef getRef() {
            return ref;
		}
    }

    /** Describes the LFS lock to be deleted */
    class DeleteLock {
        //optional boolean specifying that the user is deleting another user's lock
        boolean force = false; //default to no force
        //optional object describing the server ref that the locks belong to
        LfsRef ref;
        
        public boolean isForce() {
            return force;
        }

        public LfsRef getRef() {
            return ref;
        }
    }

    /** Describes the LFS lock listing to be verified */
    class ListLocksToVerify {
        //optional cursor to allow pagination
        String cursor;
        //optional limit to how many locks to return
        int limit = 0; //default to no limit
        //optional object describing the server ref that the locks belong to
        LfsRef ref;

        public String getCursor() {
            return cursor;
		}

        public int getLimit() {
            return limit;
		}

        public LfsRef getRef() {
            return ref;
		}
    }

}
