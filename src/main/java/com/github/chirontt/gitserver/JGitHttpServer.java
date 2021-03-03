package com.github.chirontt.gitserver;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.http.server.GitServlet;

/**
 * Server to handle access to git repositories over HTTP.
 * This server uses Jetty as the embedded servlet container
 * running the servlet GitServlet from the JGit project.
 */
public class JGitHttpServer {

    //default parameter values
    static int serverPort = 8080;
    static String basePath = "/git";

    /**
     * Server for accessing git repositories over HTTP
     *
     * @param args args[0] - server port number
     *             args[1] - path to the git repositories in the local filesystem
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

        if (args.length == 0) {
            System.out.println("Usage: JGitHttpServer [port [base-path]]\n");
        }
        printServerInfo();

        //set up the JGit's GitServlet
        ServletHandler servletHandler = new ServletHandler();
        ServletHolder servletHolder = servletHandler.addServletWithMapping(GitServlet.class, "/*");
        servletHolder.setInitParameter("base-path", basePath); //path to the git repositories
        servletHolder.setInitParameter("export-all", "true"); //yes, true, 1, on: export all repositories
                                                              //no, false, 0, off: export no repositories

        //start up the http server
        Server server = new Server(serverPort);
        server.setHandler(servletHandler);
        server.start();
        server.join();
    }

    private static void printServerInfo() {
        System.out.println("Running Git http server on port=" + serverPort +
                           ", base-path=" + basePath);
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

}
