package com.github.chirontt.lfs.server.locks.lm;

import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

public class LockUtils {

    private LockUtils() {
    }

    /**
     * Check if the given path for the refSpec exists in the repository.
     * 
     * @param repo The repository to search
     * @param refSpec The ref the path belongs to
     * @param path The path to check for existence
     * 
     * @return true - if the path exists,
     *         false - otherwise
     * 
     * @throws IOException
     */
    public static boolean isPathPresentForRef(Repository repo, String refSpec, String path)
            throws IOException {
        // Resolve the revision specification
        final ObjectId id;
        if (refSpec == null || refSpec.isEmpty()) {
            id = repo.resolve(Constants.HEAD);
        } else {
            id = repo.resolve(refSpec);
        }
        try (ObjectReader reader = repo.newObjectReader();
             RevWalk walk = new RevWalk(reader)) {
            // Get the commit object for that revision
            RevCommit commit = walk.parseCommit(id);
            // Get the revision's file tree
            // and narrow it down to the single file's path
            TreeWalk treewalk = TreeWalk.forPath(reader, path, commit.getTree());
            if (treewalk != null) {
                treewalk.close();
                return true;
            } else {
                return false;
            }
        }
    }

}
