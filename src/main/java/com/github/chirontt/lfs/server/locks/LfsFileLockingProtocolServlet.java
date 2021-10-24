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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lfs.errors.LfsUnavailable;
import org.eclipse.jgit.lfs.server.internal.LfsGson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.chirontt.lfs.server.LfsProtocolServletV2;
import com.github.chirontt.lfs.server.LfsRef;
import com.github.chirontt.lfs.server.RepositoryAccessor;
import com.github.chirontt.lfs.server.locks.LfsFileLockingRequest.CreateLock;
import com.github.chirontt.lfs.server.locks.LfsFileLockingRequest.DeleteLock;
import com.github.chirontt.lfs.server.locks.LfsFileLockingRequest.ListLocksToVerify;
import com.github.chirontt.lfs.server.locks.LfsFileLockingRequest.LockAction;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.CreatedOrDeletedLock;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.Error;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.LockExistsError;
import com.github.chirontt.lfs.server.locks.LfsFileLockingResponse.LocksToVerify;
import com.github.chirontt.lfs.server.locks.errors.LfsLockExists;
import com.github.chirontt.lfs.server.locks.errors.LfsUnauthorized;
import com.github.chirontt.lfs.server.locks.internal.LfsFileLockingText;

/**
 * LFS file locking handler implementing the LFS File Locking API [1]
 *
 * [1] https://github.com/git-lfs/git-lfs/blob/main/docs/api/locking.md
 */
public abstract class LfsFileLockingProtocolServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory
            .getLogger(LfsFileLockingProtocolServlet.class);

    protected abstract LockManager getLockManager() throws LfsException;

    protected abstract RepositoryAccessor getRepositoryAccessor(String path)
            throws LfsException;

    protected void checkAccessToMainRepository(LockAction action,
            String refName, String username)
    throws LfsException {
        RepositoryAccessor repoAccessor = getRepositoryAccessor(null);
        if (repoAccessor == null) {
            return;
        }

        if (action == LockAction.LIST_LOCKS) {
            repoAccessor.checkReadAccess(refName, username);
        } else {
            repoAccessor.checkWriteAccess(refName, username);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
    	LOG.debug("GET headers: " + getHeaders(req));
        String pathInfo = req.getPathInfo();
        Map<String, String[]> params = req.getParameterMap();
        LOG.debug("Locks query string: " + req.getQueryString());

        Writer w = new BufferedWriter(
                new OutputStreamWriter(resp.getOutputStream(), UTF_8));
        String path = getQueryParameterValue(params, "path");
        String id = getQueryParameterValue(params, "id");
        String cursor = getQueryParameterValue(params, "cursor");
        String limitStr = getQueryParameterValue(params, "limit");
        String refspec = getQueryParameterValue(params, "refspec");

        resp.setContentType(LfsProtocolServletV2.CONTENTTYPE_VND_GIT_LFS_JSON);
        try {
            if (pathInfo != null) {
                throw new LfsException("Invalid path info in the GET request: " + pathInfo);
            }
            int limit = 0; //default is no limit
            if (limitStr != null && !limitStr.isEmpty()) {
                try {
                    limit = Integer.parseInt(limitStr);
                } catch (NumberFormatException e) {
                    throw new LfsException("Invalid limit parameter in the GET request: " + limitStr);
                }
            }
            checkAccessToMainRepository(LfsFileLockingRequest.LockAction.LIST_LOCKS,
                    refspec, getUsername(req));
            LockManager lockManager = getLockManager();
            if (lockManager == null) {
                throw new LfsUnavailable(LfsFileLockingText.get().fileLockingServiceUnavailable);
            }
            LOG.debug(String.format("Retrieving locks, with path=%1$s, id=%2$s, cursor=%3$s, limit=%4$d, refspec=%5$s",
                                    path, id, cursor, limit, refspec));
            LfsFileLockingResponse.Locks locks =
                    lockManager.listLocks(path, id, cursor, limit, refspec);
            resp.setStatus(SC_OK);
            LfsGson.toJson(locks, w);
        } catch (LfsUnauthorized e) {
            sendError(resp, w, SC_FORBIDDEN, e.getMessage());
        } catch (LfsUnavailable e) {
            sendError(resp, w, SC_NOT_FOUND, e.getMessage());
        } catch (LfsException e) {
            sendError(resp, w, SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            w.flush();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
    	LOG.debug("POST headers: " + getHeaders(req));
        Writer w = new BufferedWriter(
                new OutputStreamWriter(resp.getOutputStream(), UTF_8));

        Reader r = new BufferedReader(
                new InputStreamReader(req.getInputStream(), UTF_8));
        String pathInfo = req.getPathInfo();
        LOG.debug("Path info: " + pathInfo);

        resp.setContentType(LfsProtocolServletV2.CONTENTTYPE_VND_GIT_LFS_JSON);
        try {
            String username = getUsername(req);
            if (pathInfo == null) {
                //create-lock request;
                CreateLock createLock = LfsGson.fromJson(r, CreateLock.class);
                LfsRef ref = createLock.getRef();
                String refName = ref == null ? null : ref.getName();
                checkAccessToMainRepository(LockAction.CREATE_LOCK, refName, username);
                LockManager lockManager = getLockManager();
                if (lockManager == null) {
                    throw new LfsUnavailable(LfsFileLockingText.get().fileLockingServiceUnavailable);
                }
                LOG.debug(String.format("Creating lock, with path=%1$s, refspec=%2$s, username=%3$s",
                        createLock.getPath(), refName, username));
                CreatedOrDeletedLock lockCreated = 
                        lockManager.createLock(createLock.getPath(), refName, username);
                resp.setStatus(SC_CREATED);
                LfsGson.toJson(lockCreated, w);
            } else if (pathInfo.equals("/verify")) {
                //list-locks-to-verify request;
                ListLocksToVerify listLocksToVerify = LfsGson.fromJson(r, ListLocksToVerify.class);
                LfsRef ref = listLocksToVerify.getRef();
                String refName = ref == null ? null : ref.getName();
                checkAccessToMainRepository(LockAction.LIST_LOCKS_TO_VERIFY, refName, username);
                LockManager lockManager = getLockManager();
                if (lockManager == null) {
                    throw new LfsUnavailable(LfsFileLockingText.get().fileLockingServiceUnavailable);
                }
                LOG.debug(String.format("Retrieving locks for verification, with cursor=%1$s, limit=%2$d, refspec=%3$s, username=%4$s",
                        listLocksToVerify.getCursor(), listLocksToVerify.getLimit(), refName, username));
                LocksToVerify locksToVerify = lockManager.listLocksToVerify(refName, username,
                        listLocksToVerify.getCursor(), listLocksToVerify.getLimit());
                resp.setStatus(SC_OK);
                LfsGson.toJson(locksToVerify, w);
            } else {
                //delete-lock request, with path info in the form of "/:id/unlock"
                String lockId = null;
                String[] tokens = pathInfo.split("/");
                if (tokens.length != 3 || !tokens[0].isEmpty() || !tokens[2].equals("unlock")) {
                    String error = MessageFormat
                            .format(LfsFileLockingText.get().invalidDeleteLockEndpoint, pathInfo);
                    throw new LfsException(error);
                } else {
                    lockId = tokens[1];
                }
                DeleteLock deleteLock = LfsGson.fromJson(r, DeleteLock.class);
                LfsRef ref = deleteLock.getRef();
                String refName = ref == null ? null : ref.getName();
                checkAccessToMainRepository(LockAction.DELETE_LOCK, refName, username);
                LockManager lockManager = getLockManager();
                if (lockManager == null) {
                    throw new LfsUnavailable(LfsFileLockingText.get().fileLockingServiceUnavailable);
                }
                boolean isAdministrator = lockManager.isLockAdministrator(username);
                LOG.debug(String.format("Deleting lock, with id=%1$s, ref=%2$s, username=%3$s, force=%4$b",
                        lockId, refName, username, deleteLock.isForce() && isAdministrator));
                CreatedOrDeletedLock lockDeleted =
                        lockManager.deleteLock(lockId, refName, username, deleteLock.isForce() && isAdministrator);
                resp.setStatus(SC_OK);
                LfsGson.toJson(lockDeleted, w);
            }
        } catch (LfsUnauthorized e) {
            sendError(resp, w, SC_FORBIDDEN, e.getMessage());
        } catch (LfsUnavailable e) {
            sendError(resp, w, SC_NOT_FOUND, e.getMessage());
        } catch (LfsLockExists e) {
            resp.setStatus(SC_CONFLICT);
            Error error = new LockExistsError(e.getMessage(), e.getLock());
            LfsGson.toJson(error, w);
        } catch (LfsException e) {
            sendError(resp, w, SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            w.flush();
        }
    }

    private String getQueryParameterValue(Map<String, String[]> params, String key) {
        String[] values = params.getOrDefault(key, null);
        if (values == null || values.length == 0) {
            return null;
        } else {
            return URLDecoder.decode(values[0], StandardCharsets.UTF_8);
        }
    }

    private void sendError(HttpServletResponse resp, Writer writer, int status,
            String message) {
        resp.setStatus(status);
        Error error = new Error(message);
        LfsGson.toJson(error, writer);
    }

    private String getHeaders(HttpServletRequest req) {
        StringBuilder builder = new StringBuilder();
        Enumeration<String> headers = req.getHeaderNames();
        while (headers.hasMoreElements()) {
            String headerName = headers.nextElement();
            builder.append(headerName+"=");
            Enumeration<String> headerValues = req.getHeaders(headerName);
            List<String> valueList = new ArrayList<>();
            while (headerValues.hasMoreElements()) {
                valueList.add(headerValues.nextElement());
            }
            builder.append(valueList.toString()+", ");
        }
        return builder.toString();
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
