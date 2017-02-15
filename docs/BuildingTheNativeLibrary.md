JNLua uses a native library which integrates Lua with Java by means of the Java Native API (JNI). The native library is written in ANSI C and invokes the actual Lua implementation provided by a shared Lua library.

The optional Java VM module is built alongside the native library. It is a Lua module written in C that allows a Lua process to create a Java Virtual Machine and run Java code in that machine. In essence, the Java VM module supports bootstrapping JNLua from the Lua side.

The sources of the native library and the Java VM module are part of the `src` assembly that is downloadable from the JNLua web site. As an alternative, the sources can be checked out from the project respository. The assembly or checkout contains the following directories that are relevant for building the native library and Java VM module:

| **Directory** | **Description** |
|:--------------|:----------------|
| `jnlua-{version}/src/main/c` | C sources of the native library and the Java VM module|
| `jnlua-{version}/src/main/c/{platform}` | Makefiles for various platforms |

Currently, JNLua provides Makefiles for the Win32, Linux and MacOSX platforms. The build process is deliberately kept simple. It should therefore be relatively easy to adapt a Makefile for a platform that is not supported out-of-the-box from one of the existing Makefiles.

Building the JNLua native library and Java VM module requires a shared Lua library. For many platforms, you can download a shared Lua library from the [LuaBinaries project](http://luabinaries.sourceforge.net/) web site. Many Linux distributions provide the shared Lua library as a package.

JNLua 0.9 requires Lua 5.1.

JNLua 1.0 requires Lua 5.2.

To build the native library and Java VM module for a platform, change into the appropriate directory and edit the Makefile to customize the paths for your environment. The paths typically define the location of the shared Lua library and headers on your system as well as the location of the Java Development Kit (JDK). When the paths are set correctly, run **make** from the platform directory. On the Windows platform, open the _Visual Studio Command Prompt_ from the Start Menu and then run **nmake** from the platform directory.