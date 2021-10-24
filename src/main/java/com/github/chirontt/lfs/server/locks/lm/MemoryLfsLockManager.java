package com.github.chirontt.lfs.server.locks.lm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.CreatedOrDeletedLock;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Lock;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Locks;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.LocksToVerify;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Owner;
import com.github.chirontt.lfs.server.locks.errors.LfsLockExists;
import com.github.chirontt.lfs.server.locks.errors.LfsUnauthorized;
import com.github.chirontt.lfs.server.locks.internal.LfsFileLockingText;
import com.github.chirontt.lfs.server.LfsRef;
import com.github.chirontt.lfs.server.locks.LockManager;

/**
 * An implementation of {@link LockManager} which persists the LFS locks
 * to memory.
 */
public class MemoryLfsLockManager implements LockManager {

    private ConcurrentMap<String, PersistentLock> lockCache = new ConcurrentHashMap<>();
    private FileRepositoryBuilder repositoryBuilder;

    public MemoryLfsLockManager(Path repoPath) {
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

        Lock lock = lockCache.get(id);
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
        lockCache.put(id, persistentLock);

        return new CreatedOrDeletedLock(lock);
    }

    /** {@inheritDoc} */
    @Override
    public Locks listLocks(String path, String id, String cursor, int limit, String refspec)
            throws LfsException {
        List<Lock> lockList = new ArrayList<>();
        lockCache.values().stream().filter( lock -> lockMatched(lock, path, refspec) )
                                   .forEach( lock -> lockList.add(lock.copyToLock()) );
        return new Locks(lockList, null);
    }

    /** {@inheritDoc} */
    @Override
    public CreatedOrDeletedLock deleteLock(String id, String refName, String username, boolean force)
            throws LfsException {
        Lock lock = null;
        PersistentLock pLock = lockCache.get(id);
        if (pLock != null && 
            (lockMatched(pLock, null, refName) || force)) {
            lock = pLock.copyToLock();
        } else {
            throw new LfsException("Lock doesn't exist for deletion");
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
        lockCache.remove(id);

        return new CreatedOrDeletedLock(lock);
    }

    /** {@inheritDoc} */
    @Override
    public LocksToVerify listLocksToVerify(String refName, String username, String cursor, int limit)
            throws LfsException {
        List<Lock> matchingLocks = new ArrayList<>();
        lockCache.values().stream().filter( lock -> lockMatched(lock, null, refName) )
                                   .forEach( lock -> matchingLocks.add(lock.copyToLock()) );

        //split the matching lock list to ours and theirs
        List<Lock> ours = new ArrayList<>();
        List<Lock> theirs = new ArrayList<>();
        matchingLocks.forEach( lock -> {
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
        });

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

    private boolean isPathPresentForRef(String refSpec, String path)
            throws IOException {
        try (Repository repo = repositoryBuilder.build()) {
            return LockUtils.isPathPresentForRef(repo, refSpec, path);
        }
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

}
