cmake_minimum_required(VERSION 3.10)
project(JNLua C)

# Set cross-compilation for macOS ARM64
set(CMAKE_SYSTEM_NAME Darwin)
set(CMAKE_SYSTEM_PROCESSOR arm64)
set(CMAKE_OSX_ARCHITECTURES "arm64")

# Set OSXCross toolchain
set(CMAKE_C_COMPILER "$ENV{OSXCROSS_PATH}/target/bin/aarch64-apple-darwin20.4-clang")
set(CMAKE_LINKER "$ENV{OSXCROSS_PATH}/target/bin/aarch64-apple-darwin20.4-ld")
set(CMAKE_AR "$ENV{OSXCROSS_PATH}/target/bin/aarch64-apple-darwin20.4-ar")
set(CMAKE_RANLIB "$ENV{OSXCROSS_PATH}/target/bin/aarch64-apple-darwin20.4-ranlib")

# Variable definitions
set(VERSION "5.1")
set(ARCH "arm64")

# JDK and Lua paths
set(JDK_DIR "$ENV{OSXCROSS_PATH}/SDK/MacOSX11.3.sdk/System/Library/Frameworks/JavaVM.framework" CACHE PATH "JDK framework directory")
set(LUA_DIR "/mnt/d/LuaJIT-2.1/src" CACHE PATH "LuaJIT directory")

# Compilation options
set(CMAKE_C_STANDARD 99)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -g -Wall -DNDEBUG -D_REENTRANT -DLUA_USE_POSIX")

# macOS specific options
set(CMAKE_MACOSX_RPATH ON)
set(CMAKE_BUILD_WITH_INSTALL_RPATH ON)
set(CMAKE_INSTALL_RPATH "@loader_path")

# Include directories
include_directories(
    ${JDK_DIR}/Headers
    ${LUA_DIR}
)

# Link directories
link_directories(
    /mnt/d/dbcli/lib/mac-arm
)

# Set output file properties
set(CMAKE_SHARED_LIBRARY_PREFIX "lib")
set(CMAKE_SHARED_LIBRARY_SUFFIX ".so")

# Create jnlua shared library
add_library(jnlua${VERSION} MODULE jnlua.c)
set_target_properties(jnlua${VERSION} PROPERTIES
    OUTPUT_NAME "jnlua${VERSION}"
    PREFIX "lib"
    SUFFIX ".so"
)
target_link_libraries(jnlua${VERSION}
    luajit-5.1
)

# Create javavm shared library
add_library(javavm MODULE javavm.c)
set_target_properties(javavm PROPERTIES
    OUTPUT_NAME "javavm"
    PREFIX "lib"
    SUFFIX ".so"
)
target_link_libraries(javavm
    luajit-5.1
    "-framework JavaVM"
)
