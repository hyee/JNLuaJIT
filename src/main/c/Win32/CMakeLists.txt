cmake_minimum_required(VERSION 3.10)
project(JNLua)

# Set cross-compilation for 32-bit Windows
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86)
set(CMAKE_C_COMPILER i686-w64-mingw32-gcc)
set(CMAKE_RC_COMPILER i686-w64-mingw32-windres)

# Set compilation options
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -g -DLUA_WIN -DLUA_BUILD_AS_DLL")
set(CMAKE_SHARED_LIBRARY_PREFIX "")  # Remove lib prefix

# Set linking flags
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--add-stdcall-alias -static-libgcc")

# JDK and Lua paths (Adjust according to actual Ubuntu paths)
set(JDK_DIR "/mnt/d/jdkx86" CACHE PATH "JDK directory")
set(LUA_DIR "/mnt/d/LuaJIT-2.1/src" CACHE PATH "LuaJIT directory")

# Include directories
include_directories(
    ${LUA_DIR}
    ${JDK_DIR}/include
    ${JDK_DIR}/include/win32
)

# Link directories
link_directories(
    ${JDK_DIR}/lib
    ${LUA_DIR}
)

# Set linking flags
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libgcc")

# Create jnlua DLL
add_library(jnlua5.1 SHARED jnlua.c)
set_target_properties(jnlua5.1 PROPERTIES
    OUTPUT_NAME "jnlua5.1"
    PREFIX ""
    SUFFIX ".dll"
)
target_link_libraries(jnlua5.1
    ${LUA_DIR}/lua5.1.dll
)

# Create javavm DLL
add_library(javavm SHARED javavm.c)
set_target_properties(javavm PROPERTIES
    OUTPUT_NAME "javavm"
    PREFIX ""
    SUFFIX ".dll"
)
target_link_libraries(javavm
    jvm
    ${LUA_DIR}/lua5.1.dll
)
