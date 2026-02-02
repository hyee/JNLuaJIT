# Building the Native Library

JNLua uses a native library which integrates Lua with Java by means of the Java Native API (JNI). The native library is written in ANSI C and invokes the actual Lua implementation provided by a shared Lua library.

The optional Java VM module is built alongside the native library. It is a Lua module written in C that allows a Lua process to create a Java Virtual Machine and run Java code in that machine. In essence, the Java VM module supports bootstrapping JNLua from the Lua side.

The sources of the native library and the Java VM module are part of the `src` assembly that is downloadable from the JNLua web site. As an alternative, the sources can be checked out from the project repository. The assembly or checkout contains the following directories that are relevant for building the native library and Java VM module:

| **Directory** | **Description** |
|:--------------|:----------------|
| `jnlua-{version}/src/main/c` | C sources of the native library and the Java VM module|
| `jnlua-{version}/src/main/c/{platform}` | Makefiles for various platforms |

## Build Requirements

Building the JNLua native library and Java VM module requires a shared Lua library. For many platforms, you can download a shared Lua library from the [LuaBinaries project](http://luabinaries.sourceforge.net/) website. Many Linux distributions provide the shared Lua library as a package.

- JNLua 0.9 requires Lua 5.1
- JNLua 1.0 requires Lua 5.2
- JNLua 1.0.4 requires LuaJIT

## Platform Support

Currently, JNLua provides Makefiles for the Win32, Linux and MacOSX platforms. The build process is deliberately kept simple. It should therefore be relatively easy to adapt a Makefile for a platform that is not supported out-of-the-box from one of the existing Makefiles.

## Build Steps

### Linux
```bash
cd src/main/c/Linux
make
```

### Windows
```cmd
cd src/main/c\Win32
nmake
```
(in the Visual Studio Command Prompt)

### macOS
```bash
cd src/main/c/MacOSX
make
```

### Linux-aarch64
```bash
cd src/main/c/Linux-aarch64
./build.sh
```

### Win32
```bash
cd src/main/c\Win32
.\build.sh
```

## Custom Builds

To build the native library and Java VM module for a platform, change into the appropriate directory and edit the Makefile to customize the paths for your environment. The paths typically define the location of the shared Lua library and headers on your system as well as the location of the Java Development Kit (JDK).

When the paths are set correctly, run **make** from the platform directory. On the Windows platform, open the _Visual Studio Command Prompt_ from the Start Menu and then run **nmake** from the platform directory.

## CMake Builds (Linux-aarch64)

For some platforms, JNLua also provides CMakeLists.txt files:

```bash
cd src/main/c/Linux-aarch64
cmake .
make
```

## Build Configuration

When building the native library, you may need to adjust the following settings in the Makefile:

- `LUA_DIR`: Path to the Lua installation directory
- `JAVA_HOME`: Path to the Java Development Kit (JDK)
- `LUA_LIB`: Name of the Lua library (e.g., `lua52`, `lua51`, `luajit`)
- Compiler flags specific to your platform

The native library build process creates both the core JNLua library and the optional Java VM module. Both components are essential for the complete JNLua functionality.