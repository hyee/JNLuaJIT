cmake_minimum_required(VERSION 3.10)
project(JNLua C)

# 设置交叉编译为 ARM64
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)

# 设置交叉编译器
set(CMAKE_C_COMPILER aarch64-linux-gnu-gcc)
set(CMAKE_FIND_ROOT_PATH /usr/aarch64-linux-gnu)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)

# 变量定义
set(VERSION "5.1")
set(ARCH "aarch64")

# JDK和Lua路径
set(JDK_DIR "/mnt/d/jdk-24-linux-arm64" CACHE PATH "JDK directory")
set(LUA_DIR "/mnt/d/LuaJIT-2.1/src" CACHE PATH "LuaJIT directory")

# 编译选项
set(CMAKE_C_STANDARD 99)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -g -Wall -DNDEBUG -D_REENTRANT -DLUA_USE_LINUX -DLUA_USE_POSIX")

# 链接选项
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libgcc")

# 包含目录
include_directories(
    ${JDK_DIR}/include
    ${JDK_DIR}/include/linux
    ${LUA_DIR}
)

# 链接目录
link_directories(
    /mnt/d/dbcli/lib/linux-arm
    ${JDK_DIR}/lib/server
)

# 设置全局参数，去除 lib 前缀
set(CMAKE_SHARED_LIBRARY_PREFIX "")

# 创建 jnlua 共享库
add_library(jnlua${VERSION} SHARED jnlua.c)
set_target_properties(jnlua${VERSION} PROPERTIES
    OUTPUT_NAME "jnlua${VERSION}"
    SUFFIX ".so"
)
target_link_libraries(jnlua${VERSION}
    luajit${VERSION}
    pthread
)

# 创建 javavm 共享库
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
