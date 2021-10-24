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

import java.util.List;

/**
 * LFS File Locking Response POJOs for Gson serialization/de-serialization.
 *
 * See the <a href="https://github.com/git-lfs/git-lfs/blob/main/docs/api/locking.md">LFS File Locking
 * API specification</a>
 *
 */
public interface LfsFileLockingResponse {

	/** Describes the owner of a LFS lock */
    class Owner {
        String name; //from the user credentials posted when creating the lock

        public Owner() {
            this(null);
        }

        public Owner(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /** Describes the structure of a LFS lock */
    class Lock {
        String id;  //e.g. "some-uuid"
        String path; //e.g. "path/to/file"
        //RFC 3339-formatted string with second precision,
        //e.g. "2016-05-17T15:49:06+00:00"
        String lockedAt;
        //the owner of the lock (optional)
        Owner owner;

        public String getId() {
            return id;
        }

        public String getPath() {
            return path;
        }

        public String getLockedAt() {
            return lockedAt;
        }

        public Owner getOwner() {
            return owner;
        }

		public void setId(String id) {
			this.id = id;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public void setLockedAt(String lockedAt) {
			this.lockedAt = lockedAt;
		}

		public void setOwner(Owner owner) {
			this.owner = owner;
		}
    }

    /** Describes the LFS lock which was created or deleted */
    class CreatedOrDeletedLock {
        Lock lock;

        public CreatedOrDeletedLock() {
            this(null);
        }

        public CreatedOrDeletedLock(Lock lock) {
            this.lock = lock;
        }

        public Lock getLock() {
            return lock;
        }
    }

    /** Describes current listing of active locks for a repository */
    class Locks {
        List<Lock> locks;
        String nextCursor; //optional next ID

        public Locks() {
            this(null, null);
        }

        public Locks(List<Lock> locks, String nextCursor) {
            this.locks = locks;
            this.nextCursor = nextCursor;
        }

        public List<Lock> getLocks() {
            return locks;
        }

        public String getNextCursor() {
            return nextCursor;
        }
    }

    /**
     * Describes the current active locks which are owned by the current user ("ours")
     * and which are owned by other users ("theirs"), for verification purpose.
     */
    class LocksToVerify {
        List<Lock> ours;
        List<Lock> theirs;
        String nextCursor; //optional next ID

        public List<Lock> getOurs() {
            return ours;
        }

        public List<Lock> getTheirs() {
            return theirs;
        }

        public String getNextCursor() {
            return nextCursor;
        }

		public void setOurs(List<Lock> ours) {
			this.ours = ours;
		}

		public void setTheirs(List<Lock> theirs) {
			this.theirs = theirs;
		}

		public void setNextCursor(String nextCursor) {
			this.nextCursor = nextCursor;
		}
    }

    /** Describes an error to be returned by the LFS file locking API */
    class Error {
        //the error message
        String message;
        //optional string to give the user a place to report errors
        String documentationUrl;
        //optional unique identifier for the request; useful for debugging
        String requestId;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public String getDocumentationUrl() {
            return documentationUrl;
        }

        public String getRequestId() {
            return requestId;
        }
    }

    /**
     * Describes the error to be returned by the LFS file locking API,
     * when trying to create a new LFS lock but the lock already exists
     */
    class LockExistsError extends Error {
        Lock lock;

        public LockExistsError(String message, Lock lock) {
            super(message);
            this.lock = lock;
        }

        public Lock getLock() {
            return lock;
        }
    }

}
