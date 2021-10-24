/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.lfs.server.locks.lm;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lfs.server.internal.LfsGson;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.github.chirontt.lfs.server.LfsRef;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.CreatedOrDeletedLock;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Lock;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Locks;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.LocksToVerify;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Owner;
import com.github.chirontt.lfs.server.locks.internal.LfsFileLockingText;
import com.github.chirontt.lfs.server.locks.LockManager;
import com.github.chirontt.lfs.server.locks.errors.LfsLockExists;
import com.github.chirontt.lfs.server.locks.errors.LfsUnauthorized;

/**
 * An implementation of {@link LockManager} which persists the LFS locks
 * to the file system.
 */
public class FileLfsLockManager implements LockManager {

    private Path locksPath;
    private FileRepositoryBuilder repositoryBuilder;

    public FileLfsLockManager(Path lfsPath, Path repoPath) {
        this.locksPath = Paths.get(lfsPath.toString(), "locks");
        this.repositoryBuilder =
                new FileRepositoryBuilder().setGitDir(repoPath.toFile())
                                           .setMustExist(true);
    }

    /** {@inheritDoc} */
    @Override
    public CreatedOrDeletedLock createLock(String path, String refName, String username)
            throws LfsException {
        //sanity check for path
        if (path == null || path.isEmpty()) {
            throw new LfsException("Invalid path: " + path);
        }
        //check that the path actually exists for the ref in the repository
        //before any lock creation
        try {
            if (!isPathPresentForRef(refName, path)) {
                throw new LfsException("Path '" + path + "' doesn't exist for ref '" + refName + "' in the repository.");
            }
        } catch (IOException e) {
            throw new LfsException(e.getMessage());
        }

    	//create the lock ID from its path property
        String id = Base64.getUrlEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8));

        Lock lock = null;
        try {
            lock = readLock(id);
        } catch (IOException e) {
            e.printStackTrace();
            throw new LfsException("Failed to create lock. Reason: " + e.getMessage());
        }
        if (lock != null) {
            String error = MessageFormat
                    .format(LfsFileLockingText.get().lockExistsForPath, path);
            throw new LfsLockExists(error, lock);
        }

        lock = new Lock();
        lock.setId(id);
        lock.setPath(path);
        if (username != null && !username.isEmpty()) {
            Owner owner = new Owner(username);
            lock.setOwner(owner);
        }
        //set the lock timestamp
        ZonedDateTime zdt = ZonedDateTime.now(ZoneOffset.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        lock.setLockedAt(zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        //persist the lock
        PersistentLock persistentLock = new PersistentLock(lock);
        if (refName != null && !refName.isEmpty()) {
            LfsRef ref = new LfsRef(refName);
            persistentLock.setRef(ref);
        }
        try {
            writeLock(persistentLock);
        } catch (IOException e) {
            e.printStackTrace();
            throw new LfsException("Failed to create lock. Reason: " + e.getMessage());
        }

        return new CreatedOrDeletedLock(lock);
    }

    /** {@inheritDoc} */
    @Override
    public Locks listLocks(String path, String id, String cursor, int limit, String refspec)
            throws LfsException {
        try {
            List<Lock> lockList = getLocks(path, id, cursor, limit, refspec);
            return new Locks(lockList, null);
        } catch (IOException e) {
            throw new LfsException("Failed to retrieve the lock list. Reason: " + e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public CreatedOrDeletedLock deleteLock(String id, String refName, String username, boolean force)
            throws LfsException {
        Lock lock = null;
        try {
            PersistentLock pLock = readPersistentLock(id);
            if (pLock != null && 
                (lockMatched(pLock, null, refName) || force)) {
                lock = pLock.copyToLock();
            } else {
                throw new LfsException("Lock doesn't exist for deletion");
            }
        } catch (IOException e) {
            throw new LfsException("Failed to delete lock. Reason: " + e.getMessage());
        }
        if (!force) {
            Owner owner = lock.getOwner();
            String ownerName = owner == null ? null : owner.getName();
            if ((ownerName != null && !ownerName.equals(username)) ||
                (username != null && !username.equals(ownerName))) {
                throw new LfsUnauthorized("delete lock", lock.getPath());
            }
        }

        //remove the lock from persistence storage
        try {
            deleteLock(lock);
        } catch (IOException e) {
            throw new LfsException("Failed to delete lock. Reason: " + e.getMessage());
        }

        return new CreatedOrDeletedLock(lock);
    }

    /** {@inheritDoc} */
    @Override
    public LocksToVerify listLocksToVerify(String refName, String username, String cursor, int limit)
            throws LfsException {
        List<Lock> matchingLocks = new ArrayList<>();
        if (Files.exists(locksPath)) {
            try (Stream<Path> stream = Files.list(locksPath)) {
                stream.forEach( lockPath -> {
                    try {
                        PersistentLock lock = readPersistentLock(lockPath.getFileName().toString());
                        if (lockMatched(lock, null, refName)) {
                            matchingLocks.add(lock.copyToLock());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                throw new LfsException("Failed to retrieve the locks for verification. Reason: " + e.getMessage());
            }
        }

        //split the matching lock list to ours and theirs
        List<Lock> ours = new ArrayList<>();
        List<Lock> theirs = new ArrayList<>();
        for (Lock lock : matchingLocks) {
            Owner owner = lock.getOwner();
            if (username == null) {
                if (owner == null) {
                    ours.add(lock);
                } else {
                    theirs.add(lock);
                }
            } else if (owner == null) {
                theirs.add(lock);
            } else if (username.equals(owner.getName())) {
                ours.add(lock);
            } else {
                theirs.add(lock);
            }
        }

        LocksToVerify locks = new LocksToVerify();
        locks.setOurs(ours);
        locks.setTheirs(theirs);
        return locks;
    }

    /**
     * Every user is a lock administrator, in this implementation!
     */
    @Override
    public boolean isLockAdministrator(String username) throws LfsException {
        return true;
    }

    private List<Lock> getLocks(String path, String id, String cursor, int limit, String refspec)
            throws IOException {
        List<Lock> locks = new ArrayList<>();
        if (id != null && !id.isEmpty()) {
            PersistentLock lock = readPersistentLock(id);
            if (lock != null && lockMatched(lock, path, refspec)) {
                locks.add(lock.copyToLock());
            }
        } else {
            if (Files.exists(locksPath)) {
                try (Stream<Path> stream = Files.list(locksPath)) {
                    stream.forEach( lockPath -> {
                        try {
                    	    PersistentLock lock = readPersistentLock(lockPath.getFileName().toString());
                    	    if (lockMatched(lock, path, refspec)) {
                                locks.add(lock.copyToLock());
                    	    }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        }
        return locks;
    }
    
    private boolean lockMatched(PersistentLock lock, String path, String refspec) {
        //does any of the criteria fail to match?
        if (path != null && !path.isEmpty() && !path.equals(lock.getPath())) {
            return false;
        }
        if (refspec != null && !refspec.isEmpty()) {
            LfsRef ref = lock.getRef();
            if (ref == null || !refspec.equals(ref.getName())) {
                return false;
            }
        }
    	
        return true;
    }

    private Lock readLock(String id) throws IOException {
        String json = readPersistentLockToJson(id);
        if (json == null) return null;

        StringReader reader = new StringReader(json);
        Lock lock = LfsGson.fromJson(reader, Lock.class);
        return lock;
    }

    private PersistentLock readPersistentLock(String id) throws IOException {
        String json = readPersistentLockToJson(id);
        if (json == null) return null;

        StringReader reader = new StringReader(json);
        PersistentLock lock = LfsGson.fromJson(reader, PersistentLock.class);
        return lock;
    }

    private String readPersistentLockToJson(String id) throws IOException {
        Path lockPath = Paths.get(locksPath.toString(), id);
        if (!lockPath.toFile().exists()) return null;

        String json = new String(Files.readAllBytes(lockPath), StandardCharsets.UTF_8);
        return json;
    }

    private void writeLock(PersistentLock lock) throws IOException {
        StringWriter w = new StringWriter();
        LfsGson.toJson(lock, w);
        String json = w.toString();

        if (!Files.exists(locksPath)) Files.createDirectories(locksPath);

        File lockFile = Paths.get(locksPath.toString(), lock.getId()).toFile();
        if (!lockFile.createNewFile()) {
            throw new IOException("Lock file already exists!");
        }
	 
        //write the lock contents to file while locking it
        RandomAccessFile stream = new RandomAccessFile(lockFile.getCanonicalPath(), "rw");
        FileChannel channel = stream.getChannel();
        FileLock fileLock = null;
        try {
            fileLock = channel.tryLock();
            stream.writeBytes(json);
        } finally {
            if (fileLock != null) fileLock.release();
            stream.close();
        }
    }

    private void deleteLock(Lock lock) throws IOException {
        Path lockPath = Paths.get(locksPath.toString(), lock.getId());
        if (!lockPath.toFile().exists()) return;
        Files.delete(lockPath);
    }

    /**
     * Check if the given path for the refSpec exists in the repository.
     * 
     * @param refSpec The ref the path belongs to
     * @param path The path to check for existence
     * 
     * @return true - if the path exists,
     *         false - otherwise
     * 
     * @throws IOException
     */
    private boolean isPathPresentForRef(String refSpec, String path)
            throws IOException {
        try (Repository repo = repositoryBuilder.build()) {
            return LockUtils.isPathPresentForRef(repo, refSpec, path);
        }
    }

}
