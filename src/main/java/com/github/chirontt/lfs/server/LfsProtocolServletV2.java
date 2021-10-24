/*
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com> and others
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.lfs.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INSUFFICIENT_STORAGE;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lfs.errors.LfsBandwidthLimitExceeded;
import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lfs.errors.LfsInsufficientStorage;
import org.eclipse.jgit.lfs.errors.LfsRateLimitExceeded;
import org.eclipse.jgit.lfs.errors.LfsRepositoryNotFound;
import org.eclipse.jgit.lfs.errors.LfsRepositoryReadOnly;
import org.eclipse.jgit.lfs.errors.LfsUnauthorized;
import org.eclipse.jgit.lfs.errors.LfsUnavailable;
import org.eclipse.jgit.lfs.errors.LfsValidationError;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.LfsProtocolServlet;
import org.eclipse.jgit.lfs.server.internal.LfsGson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LFS protocol handler implementing the LFS batch API v2.4 [1]
 *
 * [1] https://github.com/git-lfs/git-lfs/blob/main/docs/api/batch.md
 *
 */
public abstract class LfsProtocolServletV2 extends LfsProtocolServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected LargeFileRepository getLargeFileRepository(LfsRequest request, String path, String auth)
            throws LfsException {
        throw new LfsException("not supported");
	}
    
    protected abstract LargeFileRepository getLargeFileRepository(LfsRequestV2 lfsRequest, String path, String auth)
            throws LfsException;

    protected abstract RepositoryAccessor getRepositoryAccessor(String path)
            throws LfsException;

    /**
     * LFS request (LFS Batch API v2.4)
     */
    protected static class LfsRequestV2 extends LfsRequest {
        //optional object describing the server ref that the objects belong to
        //(to support Git server authentication schemes that take the refspec into account.)
        LfsRef ref;
        //optional identifiers for transfer adapters that the client has configured.
        //If omitted, the basic transfer adapter MUST be assumed by the server.
        List<String> transfers;

        public LfsRef getRef() {
            return ref;
        }

        public List<String> getTransfers() {
            return transfers;
        }
    }

    protected void checkAccessToMainRepository(LfsRequestV2 lfsRequest, String path, String username)
            throws LfsException {
        //check if the transfer property contains "basic"
        //as this LFS server only supports "basic" transfer
        List<String> transfers = lfsRequest.getTransfers();
        if (transfers != null && !transfers.contains("basic")) {
            throw new LfsValidationError("Missing 'basic' in transfer property: " + transfers);
        }

        RepositoryAccessor repoAccessor = getRepositoryAccessor(path);
        if (repoAccessor == null) {
            return;
        }

        LfsRef ref = lfsRequest.getRef();
        String refName = ref == null ? null : ref.getName();
        if (lfsRequest.isDownload()) {
            repoAccessor.checkReadAccess(refName, username);
        } else if (lfsRequest.isUpload()) {
            repoAccessor.checkWriteAccess(refName, username);
        }
    }

	private static final Logger LOG = LoggerFactory
			.getLogger(LfsProtocolServletV2.class);

	public static final String CONTENTTYPE_VND_GIT_LFS_JSON =
			"application/vnd.git-lfs+json; charset=utf-8"; //$NON-NLS-1$

	private static final int SC_RATE_LIMIT_EXCEEDED = 429;

	private static final int SC_BANDWIDTH_LIMIT_EXCEEDED = 509;

	/** {@inheritDoc} */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		Writer w = new BufferedWriter(
				new OutputStreamWriter(res.getOutputStream(), UTF_8));

		Reader r = new BufferedReader(
				new InputStreamReader(req.getInputStream(), UTF_8));
		LfsRequestV2 request = LfsGson.fromJson(r, LfsRequestV2.class);
		String path = req.getPathInfo();
		LOG.debug("pathInfo=" + path);
		String auth = req.getHeader(HDR_AUTHORIZATION);

		res.setContentType(CONTENTTYPE_VND_GIT_LFS_JSON);
		LargeFileRepository repo = null;
		try {
		    checkAccessToMainRepository(request, path, getUsername(req));
			repo = getLargeFileRepository(request, path, auth);
			if (repo == null) {
				String error = MessageFormat
						.format(LfsText.get().lfsFailedToGetRepository, path);
				LOG.error(error);
				throw new LfsException(error);
			}
			res.setStatus(SC_OK);
			TransferHandler handler = TransferHandler
					.forOperation(request.getOperation(), repo, request.getObjects());
			LfsGson.toJson(handler.process(), w);
		} catch (LfsValidationError e) {
			sendError(res, w, SC_UNPROCESSABLE_ENTITY, e.getMessage());
		} catch (LfsRepositoryNotFound e) {
			sendError(res, w, SC_NOT_FOUND, e.getMessage());
		} catch (LfsRepositoryReadOnly e) {
			sendError(res, w, SC_FORBIDDEN, e.getMessage());
		} catch (LfsRateLimitExceeded e) {
			sendError(res, w, SC_RATE_LIMIT_EXCEEDED, e.getMessage());
		} catch (LfsBandwidthLimitExceeded e) {
			sendError(res, w, SC_BANDWIDTH_LIMIT_EXCEEDED, e.getMessage());
		} catch (LfsInsufficientStorage e) {
			sendError(res, w, SC_INSUFFICIENT_STORAGE, e.getMessage());
		} catch (LfsUnavailable e) {
			sendError(res, w, SC_SERVICE_UNAVAILABLE, e.getMessage());
		} catch (LfsUnauthorized e) {
			sendError(res, w, SC_UNAUTHORIZED, e.getMessage());
		} catch (LfsException e) {
			sendError(res, w, SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} finally {
			w.flush();
		}
	}

	private void sendError(HttpServletResponse rsp, Writer writer, int status,
			String message) {
		rsp.setStatus(status);
		LfsGson.toJson(message, writer);
	}

    private String getUsername(HttpServletRequest req) throws LfsException {
        String username = req.getRemoteUser();
        if (username == null) {
            username = retrieveUserName(req.getHeader(HDR_AUTHORIZATION));
        }
        return username;
    }

    private String retrieveUserName(String auth) throws LfsException {
        if (auth == null || auth.isEmpty()) return null;

        String[] authScheme = auth.trim().split(" ");
        if (!"Basic".equalsIgnoreCase(authScheme[0])) {
            throw new LfsException("Only 'Basic' authentication is allowed, not " + authScheme[0]);
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(authScheme[1].getBytes(StandardCharsets.UTF_8));
            String decodedString = new String(decodedBytes);
            return decodedString.split(":")[0];
        } catch (Exception e) {
            throw new LfsException("Not in valid Base64 scheme: " + authScheme[1]);
        }
    }

}
