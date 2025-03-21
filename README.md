# JGit HTTP Server + GraalVM native image

[![Github Actions Build Status](https://github.com/chirontt/jgit.http.server.native/actions/workflows/gradle-build.yml/badge.svg)](https://github.com/chirontt/jgit.http.server.native/actions/workflows/gradle-build.yml)
[![Github Actions Build Status](https://github.com/chirontt/jgit.http.server.native/actions/workflows/maven-build.yml/badge.svg)](https://github.com/chirontt/jgit.http.server.native/actions/workflows/maven-build.yml)

Sample project to compile JGit HTTP server, with LFS server support, to native executable
using GraalVM native-image utility.

[JGit](https://projects.eclipse.org/projects/technology.jgit) is a pure Java implementation of the Git version control system,
and is available as a library to be integrated into many projects.
The JGit [project](https://github.com/eclipse-jgit/jgit) also implements a
[git server](https://github.com/eclipse-jgit/jgit/tree/master/org.eclipse.jgit.http.server)
for the
[Git HTTP protocol](https://git-scm.com/docs/http-protocol), in the form of a
[servlet](https://github.com/eclipse-jgit/jgit/blob/master/org.eclipse.jgit.http.server/src/org/eclipse/jgit/http/server/GitServlet.java).

For [LFS](https://github.com/git-lfs/git-lfs/tree/main/docs/api) server support, JGit provides the
[LFS server project](https://github.com/eclipse-jgit/jgit/tree/master/org.eclipse.jgit.lfs.server)
which implements 2 servlets: the
[Batch API servlet](https://github.com/eclipse-jgit/jgit/blob/master/org.eclipse.jgit.lfs.server/src/org/eclipse/jgit/lfs/server/LfsProtocolServlet.java),
and the
[LFS servlet](https://github.com/eclipse-jgit/jgit/blob/master/org.eclipse.jgit.lfs.server/src/org/eclipse/jgit/lfs/server/fs/FileLfsServlet.java)
which supports upload/download of large objects to a separate storage in the local file system.
In addition, this project implements a
[servlet](src/main/java/com/github/chirontt/lfs/server/locks/LfsFileLockingProtocolServlet.java)
to support the [LFS File Locking API](https://github.com/git-lfs/git-lfs/blob/main/docs/api/locking.md).

This project aims to produce stand-alone, platform-specific, native executable `JGitHttpServer` of the
JGit HTTP servlet with LFS support, using embedded [Jetty](https://github.com/eclipse/jetty.project)
servlet container, and compiled by the
[GraalVM native-image](https://www.graalvm.org/reference-manual/native-image) utility.

Gradle and Maven build scripts are provided for building the project.

## Build pre-requisites

The [GraalVM native-image](https://www.graalvm.org/reference-manual/native-image) page
shows how to set up GraalVM and its native-image utility for common platforms.
[Gluon](https://gluonhq.com/) also provides some setup [details](https://docs.gluonhq.com/#_platforms)
for GraalVM native-image creation.

The GraalVM native-image utility will use the configuration files in
`src/main/resources/META-INF/native-image` folder to assist in the native-image generation.

## Gradle build tasks

To build and run the Git server in standard JVM with Gradle, execute the `run` task with
port number, path to the local git repos, and path to the local LFS storage as parameters:

	gradlew run
	gradlew run --args="8080 /path/to/repos /path/to/lfs/storage"

To generate native executable, run the `nativeCompile` task:

	gradlew nativeCompile

The `nativeCompile` task would take a while to compile the source code and link into an executable file.
The resulting `JGitHttpServer` file is in:

	build/native/nativeCompile/JGitHttpServer

(or if building on a Windows machine:

	build\native\nativeCompile\JGitHttpServer.exe

)

which can then be run directly (with parameters):

	./build/native/nativeCompile/JGitHttpServer 8080 /path/to/repos /path/to/lfs/storage

(or if building on a Windows machine:

	build\native\nativeCompile\JGitHttpServer.exe 8080 \path\to\repos \path\to\lfs\storage

)

## Maven build tasks

To build and run the Git server in standard JVM with Maven, execute the `compile` and `exec:exec`
tasks with port number, path to the local git repos, and path to the local LFS storage as parameters:

	mvnw compile
	mvnw exec:exec
	mvnw exec:exec -Dexec.port=8080 -Dexec.base-path=/path/to/repos -Dexec.lfs-path=/path/to/lfs/storage

To generate native executable, run the `package` task:

	mvnw package

The `package` task would take a while to compile the source code and link into an executable file.
The resulting `JGitHttpServer` file is in:

	target/JGitHttpServer

(or if building on a Windows machine:

	target\JGitHttpServer.exe

)

which can then be run directly (with parameters):

	./target/JGitHttpServer 8080 /path/to/repos /path/to/lfs/storage

(or if building on a Windows machine:

	target\JGitHttpServer.exe 8080 \path\to\repos \path\to\lfs\storage

)

