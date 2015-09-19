# Paths
JDK_DIR=D:\java
LUA_DIR=D:\LuaRock\lua
INCR=$(LUA_DIR)\lua5.1.dll -I$(LUA_DIR) -I$(JDK_DIR)\include\win32 -I$(JDK_DIR)\include -L$(JDK_DIR)\lib
LUA_VERSION=5.1
VERSION=5.1

# Tools
CC=gcc
LD=link \subsystem:native
MT=mt

# Default arguments
CFLAGS=-m32 -Wl,--add-stdcall-alias -DLUA_WIN -DLUA_BUILD_AS_DLL -shared -O2 -static -s
LDFLAGS=-shared
MTFLAGS=-nologo

# Description blocks
all: jnlua$(VERSION).dll javavm.dll

jnlua$(VERSION).dll: 
	$(CC) $(CFLAGS) $(LDFLAGS) -o jnlua$(VERSION).dll ..\jnlua.c $(INCR)

javavm.dll: 
	$(CC) $(CFLAGS) $(LDFLAGS) -o javavm.dll ..\javavm.c $(INCR) -ljvm

clean:
	-del jnlua$(VERSION).dll jnlua$(VERSION).dll.manifest jnlua$(VERSION).exp jnlua$(VERSION).lib jnlua.obj
	-del javavm.dll javavm.dll.manifest javavm.exp javavm.lib javavm.obj
