cmake_minimum_required(VERSION 3.10)
project(JNLua C)

# Set cross-compilation for ARM64
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)

# Set cross-compiler
set(CMAKE_C_COMPILER aarch64-linux-gnu-gcc)
set(CMAKE_FIND_ROOT_PATH /usr/aarch64-linux-gnu)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)

# Variable definitions
set(VERSION "5.1")
set(ARCH "aarch64")

# JDK and Lua paths
set(D_DIR   "/mnt/d"  CACHE PATH "D directory")
set(JDK_DIR "${D_DIR}/jdk-24-linux-arm64" CACHE PATH "JDK directory")
set(LUA_DIR "${D_DIR}/LuaJIT-2.1/src" CACHE PATH "LuaJIT directory")

# Compilation options
set(CMAKE_C_STANDARD 99)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -g -Wall -DNDEBUG -D_REENTRANT -DLUA_USE_LINUX -DLUA_USE_POSIX")

# Linking options
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libgcc")

# Include directories
include_directories(
    ${JDK_DIR}/include
    ${JDK_DIR}/include/linux
    ${LUA_DIR}
)

# Link directories
link_directories(
    ${D_DIR}/dbcli/lib/linux-arm
    ${JDK_DIR}/lib/server
)

# Set global parameters, remove lib prefix
set(CMAKE_SHARED_LIBRARY_PREFIX "")

# Create jnlua shared library
add_library(jnlua${VERSION} SHARED jnlua.c)
set_target_properties(jnlua${VERSION} PROPERTIES
    OUTPUT_NAME "jnlua${VERSION}"
    SUFFIX ".so"
)
target_link_libraries(jnlua${VERSION}
    luajit${VERSION}
    pthread
)

# Create javavm shared library
add_library(javavm SHARED javavm.c)
set_target_properties(javavm PROPERTIES
    OUTPUT_NAME "javavm"
    SUFFIX ".so"
)
target_link_libraries(javavm
    luajit${VERSION}
    jvm
    pthread
)
