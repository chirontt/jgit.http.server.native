/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.github.chirontt.gitserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lfs.server.fs.FileLfsRepository;
import org.eclipse.jgit.lfs.server.fs.FileLfsServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.chirontt.lfs.server.locks.impl.FileLfsLockManager;

/**
 * Server to handle access to git repositories over HTTP, with LFS support.
 * This server uses Jetty as the embedded servlet container
 * running the servlet GitServlet from the JGit project.
 */
public class JGitHttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(JGitHttpServer.class);

    private static final String LFS_PATH = "/info/lfs/";
    private static final String OBJECTS = "objects/";
    private static final String FILE_LOCKING_API_PATH = LFS_PATH + "locks/*";
    private static final String STORE_PATH = LFS_PATH + OBJECTS + "*";
    private static final String BATCH_API_PATH = LFS_PATH + OBJECTS + "batch";

    //default parameter values
    static int serverPort = 8080;
    static String basePath = "/git";
    static String lfsPath = "/git-lfs-storage";

    /**
     * Server for accessing git repositories over HTTP, with LFS support.
     *
     * @param args args[0] - server port number
     *             args[1] - path to the git repositories in the local filesystem
     *             args[2] - path to the LFS storage in the local filesystem
     *            
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            serverPort = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            basePath = args[1];
        }
        if (args.length > 2) {
            lfsPath = args[2];
        }

        if (args.length == 0) {
            System.out.println("Usage: JGitHttpServer [port [base-path [lfs-path]]]\n");
        }
        printServerInfo();

        Server server = new Server();

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setOutputBufferSize(32768);

        ServerConnector gitConnector = new ServerConnector(server,
                new HttpConnectionFactory(httpConfig));
        gitConnector.setName("git-connector");
        gitConnector.setPort(serverPort);
        server.addConnector(gitConnector);

        ServletContextHandler gitContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        gitContext.setVirtualHosts(new String[]{"@git-connector"});

        //set up LFS servlets for each valid git repo under base-path
        List<Path> validRepos = getValidGitRepos(basePath);
        LOG.info("Git repos with LFS support: " + validRepos);
        setUpLfsServlets(validRepos, gitContext);

        //set up the GitServlet
        ServletHolder gitServletHolder = new ServletHolder(GitServlet.class);
        gitServletHolder.setInitParameter("base-path", basePath); //path to the git repositories
        gitServletHolder.setInitParameter("export-all", "true"); //yes, true, 1, on: export all repositories
                                                                 //no, false, 0, off: export no repositories
        gitContext.addServlet(gitServletHolder, "/*");

        //start up the http server
        server.setHandler(gitContext);
        server.start();
        server.join();
    }

    private static void printServerInfo() {
        System.out.println("Running Git http server on port=" + serverPort +
                           ", base-path=" + basePath + ", lfs-path=" + lfsPath);
        System.out.println("Available services:");
        System.out.println(" - Reading is permitted by default for all repositories,");
        System.out.println("   unless 'http.uploadpack=false' is set for a specific repository.");
        System.out.println(" - Writing is permitted if any of the following is true:");
        System.out.println("    * the servlet container has authenticated the user, and has set");
        System.out.println("      HttpServletRequest.remoteUser field to the authenticated name");
        System.out.println("    * the repository's configuration has 'http.receivepack=true' setting;");
        System.out.println("   otherwise repository updating is explicitly rejected.");
        System.out.println("FYI: This simple server has no user login facility.\n");
    }

    private static URI getBaseURI() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                Runtime.getRuntime().exec("hostname").getInputStream()))) {
            String hostname = br.readLine();
            return new URI("http://" + hostname + ":" + serverPort);
        } catch (IOException e) {
            throw new RuntimeException("Cannot find hostname", e);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unexpected URI error", e);
        }
    }

    private static List<Path> getValidGitRepos(String basePath) {
        try (Stream<Path> walk = Files.walk(Paths.get(basePath), 1)) {
            List<Path> repos = walk.filter(Files::isDirectory)
                                   .filter(path -> isGitDirectory(path))
                                   .collect(Collectors.toList());
            return repos;    	
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid base-path: " + basePath, e);
        }
    }

    private static void setUpLfsServlets(List<Path> repos, ServletContextHandler context) {
        URI baseURI = getBaseURI();
        repos.forEach( repoPath -> {
            String repoName = repoPath.getFileName().toString();
            if (!repoName.endsWith(".git")) {
                repoName = repoName + ".git";
            }
            try {
                //set up the LFS file locking servlet for this repo
                FileLfsLockManager lockManager = new FileLfsLockManager(Paths.get(lfsPath, repoName), repoPath);
                context.addServlet(new ServletHolder(new LfsFileLockingServlet(lockManager, repoPath)),
                                   "/" + repoName + FILE_LOCKING_API_PATH);
                //set up the LFS batch servlet for this repo
                FileLfsRepository fsRepo = new FileLfsRepository(
                        baseURI + "/" + repoName + LFS_PATH + OBJECTS, Paths.get(lfsPath, repoName));
                context.addServlet(new ServletHolder(new LfsBatchServlet(fsRepo, repoPath)),
                                   "/" + repoName + BATCH_API_PATH);
                //set up the LFS content servlet for this repo
                //with timeout of 60 minutes for object upload/download
                FileLfsServlet lfsContentServlet = new FileLfsServlet(fsRepo, 3600000);
                context.addServlet(new ServletHolder(lfsContentServlet),
                                   "/" + repoName + STORE_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static boolean isGitDirectory(Path path) {
        FileRepositoryBuilder repositoryBuilder =
                new FileRepositoryBuilder().setGitDir(path.toFile())
                                           .setMustExist(true);
        try (Repository repository = repositoryBuilder.build()) {
            return repository.exactRef("HEAD") != null;
        } catch (IOException e) {
            return false;
        }
    }
}
