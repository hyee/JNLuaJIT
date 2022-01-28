# Paths
JDK_DIR=D:\jdkx86
LUA_DIR=D:\LuaJIT-2.1\src
INCR=d:\dbcli\lib\x86\lua5.1.dll -I$(LUA_DIR) -I$(JDK_DIR)\include\win32 -I$(JDK_DIR)\include
LUA_VERSION=5.1
VERSION=5.1

# Tools
CC=gcc
LD=link \subsystem:native
MT=mt

# Default arguments
CFLAGS=-Wl,--add-stdcall-alias -lm -static-libgcc -DLUA_WIN -DLUA_BUILD_AS_DLL -shared -O2 -static -s
LDFLAGS=-shared
MTFLAGS=-nologo

# Description blocks
all: jnlua$(VERSION).dll javavm.dll

jnlua$(VERSION).dll: 
	$(CC) $(CFLAGS) $(LDFLAGS) -o jnlua$(VERSION).dll ..\jnlua.c $(INCR)

javavm.dll: 
	$(CC) $(CFLAGS) $(LDFLAGS) -o javavm.dll ..\javavm.c $(INCR) -L$(JDK_DIR)\lib -ljvm

clean:
	-rm jnlua$(VERSION).dll javavm.dll
