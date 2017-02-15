This page provides instructions for installing JNLua and launching the JNLua console in order to get started with using JNLua.



# Installing JNLua #

JNLua can be [downloaded](https://github.com/airminer/jnlua/releases) from its project web site. The download directory contains three types of assemblies:

  * `bin`: these contain the JNLua Java library as a JAR file
  * `native`: these contain pre-built versions of the JNLua native library and the JNLua Java module for the Win32 platform (64-bit and 32-bit)
  * `src`: these contain the JNLua source code

As an alternative, you clone JNLua from its repository. Tags are provided for each release.

## Installing the JNLua Java Library ##

The JNLua Java library is provided by a file named `jnlua-{version}.jar`. This file can be obtained by downloading a `bin` assembly.

[Apache Maven](http://maven.apache.org/) is required to build the JNLua Java library manually. Once the JNLua native library and the Lua library are installed on your system (see below), type the following commands:

```
mvn clean
mvn javadoc:jar
mvn package
```

## Installing the JNLua Native Library ##

The JNLua proejct provides pre-built versions of the JNLua native library for the Win32 platform (64-bit and 32-bit). These files can be obtained by downloading a `native` assembly.

Please see [Building the Native Library](BuildingTheNativeLibrary.md) for information on how to build the JNLua native library on other platforms, or do a different build for Win32.

### Installation Directory ###

The JNLua native library must be placed in a location where is is found by the Java Virtual machine. Typical locations include:

  * **Win32**: `C:\Windows\System32`
  * **Linux**: `/usr/lib`
  * **MacOSX**: `/Library/Java/Extensions`

See your JVM documentation for a list of these locations on your platform. As as alternative, you can ensure that the location where you have installed the JNLua native library is on your `java.library.path`.

## Installing the Lua Library ##

The JNLua native library requires Lua as a shared library. Depending on your environment, you can install the shared Lua library via a package manager, download it from the [LuaBinaries](http://luabinaries.sourceforge.net/) project, or compile the [Lua source code](http://www.lua.org/download.html) into a shared library yourself.

### Lua Version ###

JNLua 0.9 requires Lua 5.1.

JNLua 1.0 requires Lua 5.2.

### Installation Directory ###

The Lua library must be placed in a location where is is found when the Java Virtual Machine loads the JNLua native library. Typical locations inlcude:

  * **Win32**: `C:\Windows\System32`
  * **Linux**: `/usr/lib`
  * **MacOSX**: `/usr/lib`

# Launching the JNLua Console #

To launch the JNLua console, type the following in the directory where `jnlua-{version}.jar` is installed:

```
java -classpath jnlua-{version}.jar com.naef.jnlua.console.LuaConsole 
```

To exit the console, terminate the input. On Windows, this is accomplished by typing **F6** followed by **Enter**. On Linux, type **Ctrl+D**.