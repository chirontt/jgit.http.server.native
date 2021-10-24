/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.lfs.server.locks.impl;

import com.github.chirontt.lfs.server.LfsRef;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Lock;

/**
 * LFS lock POJO to be persisted.
 *
 */
class PersistentLock extends Lock {

    LfsRef ref;
    
    public PersistentLock() {
        this(null);
    }

    public PersistentLock(Lock lock) {
        if (lock != null) {
            setId(lock.getId());
            setPath(lock.getPath());
            setLockedAt(lock.getLockedAt());
            setOwner(lock.getOwner());
            setRef(null);
        }
    }

    public LfsRef getRef() {
        return ref;
    }
    
    public void setRef(LfsRef ref) {
        this.ref = ref;
    }
    
    public Lock copyToLock() {
        Lock lock = new Lock();
        lock.setId(getId());
        lock.setLockedAt(getLockedAt());
        lock.setOwner(getOwner());
        lock.setPath(getPath());
        return lock;
    }
}
