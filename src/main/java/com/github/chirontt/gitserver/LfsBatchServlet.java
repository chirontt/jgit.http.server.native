package com.github.chirontt.gitserver;

import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.LfsProtocolServlet;
import org.eclipse.jgit.lfs.server.fs.FileLfsRepository;

/**
 * LFS Batch API servlet, to allow separate storage of large objects
 * in the local file system on the server. 
 */
public class LfsBatchServlet extends LfsProtocolServlet {

    private static final long serialVersionUID = 1L;
    private FileLfsRepository fsRepo;

    public LfsBatchServlet(FileLfsRepository fsRepo) {
        this.fsRepo = fsRepo;
    }

    @Override
    protected LargeFileRepository getLargeFileRepository(LfsRequest request, String path, String auth)
            throws LfsException {
        return fsRepo;
    }

}
