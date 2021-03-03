# JGit HTTP Server + GraalVM native image

Sample project to compile JGit HTTP server to native executable using GraalVM native-image utility.

[JGit](http://www.eclipse.org/jgit/) is a pure Java implementation of the Git version control system,
and is available as a library to be integrated into many projects.
The JGit [project](https://github.com/eclipse/jgit) also implements a
[server](https://github.com/eclipse/jgit/tree/master/org.eclipse.jgit.http.server)
for the
[Git HTTP protocol](https://github.com/git/git/blob/master/Documentation/technical/http-protocol.txt),
in the form of a
[servlet](https://github.com/eclipse/jgit/blob/master/org.eclipse.jgit.http.server/src/org/eclipse/jgit/http/server/GitServlet.java).

This project aims to produce stand-alone, platform-specific, native executable `JGitHttpServer` of the
JGit HTTP servlet with embedded [Jetty](https://github.com/eclipse/jetty.project) servlet container, using the
[GraalVM native-image](https://www.graalvm.org/reference-manual/native-image) utility.

Gradle and Maven build scripts are provided for building the project.

## Build pre-requisites

The [GraalVM native-image](https://www.graalvm.org/reference-manual/native-image) page
shows how to set up GraalVM and its native-image utility for common platforms.
[Gluon](https://gluonhq.com/) also provides some setup [details](https://docs.gluonhq.com/#_platforms)
for GraalVM native-image creation.

This project's Gradle build script uses the [client-gradle-plugin](https://github.com/gluonhq/client-gradle-plugin)
from Gluon to build the native executable from Gradle with GraalVM.

The GraalVM native-image utility will use the configuration files in
`src/main/resources/META-INF/native-image` folder to assist in the native-image generation.

Gluon also provides the [client-maven-plugin](https://github.com/gluonhq/client-maven-plugin)
which is used in this project's Maven build script. This Maven plugin, which works similarly to the above
client-gradle-plugin, also uses the `src/main/resources/META-INF/substrate/config/initbuildtime` file
as the list of packages/classes to be initialized at image build time by GraalVM during the
native-image generation.

## Gradle build tasks

To build and run the Git server in standard JVM with Gradle, execute the `run` task with
port number and the path to the local git repos as parameters:

	gradlew run --args="8080 /path/to/repos"

To generate native executable, run the `nativeBuild` task:

	gradlew nativeBuild

The `nativeBuild` task would take a while to compile the source code and link into an executable file.
The resulting `JGitHttpServer` file is in:

	build/client/x86_64-linux/JGitHttpServer

(or if building on a Windows machine:

	build\client\x86_64-windows\JGitHttpServer.exe

)

which can then be run directly (with parameters):

	./build/client/x86_64-linux/JGitHttpServer 8080 /path/to/repos

(or if building on a Windows machine:

	build\client\x86_64-windows\JGitHttpServer.exe 8080 \path\to\repos

)

## Maven build tasks

To build and run the Git server in standard JVM with Maven, execute the `compile` and `exec:java`
tasks with port number and the path to the local git repos as parameters:

	mvn compile exec:java -Dexec.args="8080 /path/to/repos"

To generate native executable, run the `client:build` task:

	mvn client:build

The `client:build` task would take a while to compile the source code and link into an executable file.
The resulting `JGitHttpServer` file is in:

	target/client/x86_64-linux/JGitHttpServer

(or if building on a Windows machine:

	target\client\x86_64-windows\JGitHttpServer.exe

)

which can then be run directly (with parameters):

	./target/client/x86_64-linux/JGitHttpServer 8080 /path/to/repos

(or if building on a Windows machine:

	target\client\x86_64-windows\JGitHttpServer.exe 8080 \path\to\repos

)

## Compressed native executable

The resulting `JGitHttpServer` executable file, whether produced by Gradle or Maven build scripts,
can be further reduced in size via compression using the [UPX](https://upx.github.io) utility,
as described [here](https://medium.com/graalvm/compressed-graalvm-native-images-4d233766a214).

As an example, the resulting `JGitHttpServer.exe` native application file produced in Windows
is normally 50MB in size, but is compressed to 13MB with the UPX command: `upx --best JGitHttpServer.exe`

