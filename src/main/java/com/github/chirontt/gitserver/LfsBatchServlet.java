package com.github.chirontt.gitserver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lfs.errors.LfsRepositoryNotFound;
import org.eclipse.jgit.lfs.errors.LfsRepositoryReadOnly;
import org.eclipse.jgit.lfs.errors.LfsUnauthorized;
import org.eclipse.jgit.lfs.errors.LfsUnavailable;
import org.eclipse.jgit.lfs.errors.LfsValidationError;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.fs.FileLfsRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.github.chirontt.lfs.server.LfsProtocolServletV2;
import com.github.chirontt.lfs.server.LfsRef;

/**
 * LFS Batch API servlet, to allow separate storage of large objects
 * in the local file system on the server. 
 */
public class LfsBatchServlet extends LfsProtocolServletV2 {

    private static final long serialVersionUID = 1L;
    private FileLfsRepository lfsRepo;
    private Path repoPath;
    private FileRepositoryBuilder repositoryBuilder;

    public LfsBatchServlet(FileLfsRepository lfsRepo, Path repoPath) {
        this.lfsRepo = lfsRepo;
        this.repoPath = repoPath;
        this.repositoryBuilder =
                new FileRepositoryBuilder().setGitDir(repoPath.toFile())
                                           .setMustExist(true);
    }

    @Override
    protected LargeFileRepository getLargeFileRepository(LfsRequestV2 lfsRequest, String path, String auth, boolean authenticated)
            throws LfsException {
        checkAccessToMainRepository(lfsRequest, auth, authenticated);
        return lfsRepo;
    }

    protected void checkAccessToMainRepository(LfsRequestV2 lfsRequest, String auth, boolean authenticated)
            throws LfsException {
        //check if the transfer property contains "basic"
        //as this LFS server only supports "basic" transfer
        List<String> transfers = lfsRequest.getTransfers();
        if (transfers != null && !transfers.contains("basic")) {
            throw new LfsValidationError("Missing 'basic' in transfer property: " + transfers);
        }

        if (lfsRequest.isDownload()) {
            //check that the repository is readable
        	//i.e. not having http.uploadpack=false setting
            try {
                if (!getRepoConfigBooleanValue("http", "uploadpack", true)) {
                    throw new LfsUnavailable(repoPath.getFileName().toString());
                }
            } catch (IOException e) {
                throw new LfsRepositoryNotFound(repoPath.getFileName().toString());
            }
        } else if (lfsRequest.isUpload()) {
            //first, check that the repository is writable
            //for unauthenticated user
            if (!authenticated) {
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
            //check that the branch is writable by the user
            LfsRef ref = lfsRequest.getRef();
            if (ref != null) {
                if (auth == null || auth.isEmpty()) {
                    throw new LfsUnauthorized("upload", repoPath.getFileName().toString());
                }
                //does the user have write access to the branch?
                if (!hasWriteAccess(retrieveUserName(auth), ref.getName())) {
                    throw new LfsRepositoryReadOnly(
                            repoPath.getFileName().toString() + ", for ref " + ref.getName() + ",");
                }
            }
        }
    }

    private boolean getRepoConfigBooleanValue(String section, String name, boolean defaultValue)
            throws IOException {
        try (Repository repository = repositoryBuilder.build()) {
            repository.getConfig();
            return repository.getConfig().getBoolean(section, name, defaultValue);
        }
    }
    
    private boolean hasWriteAccess(String userName, String refName) {
        //writable by any user for any ref, for now
        return true;
    }

    private String retrieveUserName(String auth) throws LfsException {
    	String[] authScheme = auth.trim().split(" ");
    	if (!"Basic".equalsIgnoreCase(authScheme[0])) {
    	    throw new LfsException("Only 'Basic' authentication is allowed, not " + authScheme[0]);
    	}
    	try {
    	    byte[] decodedBytes = Base64.getDecoder().decode(authScheme[1]);
    	    String decodedString = new String(decodedBytes);
    	    return decodedString.split(":")[0];
    	} catch (Exception e) {
    	    throw new LfsException("Not in valid Base64 scheme: " + authScheme[1]);
    	}
    }
}
