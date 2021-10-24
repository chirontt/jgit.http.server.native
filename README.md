# JGit HTTP Server + GraalVM native image

Sample project to compile JGit HTTP server, with LFS server support, to native executable
using GraalVM native-image utility.

[JGit](http://www.eclipse.org/jgit/) is a pure Java implementation of the Git version control system,
and is available as a library to be integrated into many projects.
The JGit [project](https://git.eclipse.org/c/jgit/jgit.git/tree/) also implements a
[git server](https://git.eclipse.org/c/jgit/jgit.git/tree/org.eclipse.jgit.http.server)
for the
[Git HTTP protocol](https://github.com/git/git/blob/master/Documentation/technical/http-protocol.txt),
in the form of a
[servlet](https://git.eclipse.org/c/jgit/jgit.git/tree/org.eclipse.jgit.http.server/src/org/eclipse/jgit/http/server/GitServlet.java).

For [LFS](https://github.com/git-lfs/git-lfs/tree/main/docs/api) server support, JGit provides the
[LFS server project](https://git.eclipse.org/c/jgit/jgit.git/tree/org.eclipse.jgit.lfs.server)
which implements 2 servlets: the
[Batch API servlet](https://git.eclipse.org/c/jgit/jgit.git/tree/org.eclipse.jgit.lfs.server/src/org/eclipse/jgit/lfs/server/LfsProtocolServlet.java),
and the
[LFS servlet](https://git.eclipse.org/c/jgit/jgit.git/tree/org.eclipse.jgit.lfs.server/src/org/eclipse/jgit/lfs/server/fs/FileLfsServlet.java)
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

	build/native-image/JGitHttpServer

(or if building on a Windows machine:

	build\native-image\JGitHttpServer.exe

)

which can then be run directly (with parameters):

	./build/native-image/JGitHttpServer 8080 /path/to/repos /path/to/lfs/storage

(or if building on a Windows machine:

	build\native-image\JGitHttpServer.exe 8080 \path\to\repos \path\to\lfs\storage

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

	target/native-image/JGitHttpServer

(or if building on a Windows machine:

	target\native-image\JGitHttpServer.exe

)

which can then be run directly (with parameters):

	./target/native-image/JGitHttpServer 8080 /path/to/repos /path/to/lfs/storage

(or if building on a Windows machine:

	target\native-image\JGitHttpServer.exe 8080 \path\to\repos \path\to\lfs\storage

)

## Compressed native executable

The resulting `JGitHttpServer` executable file, whether produced by Gradle or Maven build scripts,
can be further reduced in size via compression using the [UPX](https://upx.github.io) utility,
as described [here](https://medium.com/graalvm/compressed-graalvm-native-images-4d233766a214).

As an example, the resulting `JGitHttpServer.exe` native application file produced in Windows
is normally 35MB in size, but is compressed to 10MB with the UPX command: `upx --best JGitHttpServer.exe`

