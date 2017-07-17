# Paths
JDK_DIR=/media/sf_D_DRIVE/jdk_linux
LUA_LIB_DIR=/media/sf_D_DRIVE/LuaJIT-2.1/src
LUA_INC_DIR=/media/sf_D_DRIVE/LuaJIT-2.1/src
VERSION=5.1
ARCH=amd64

# Tools
CC=gcc
LD=gcc

# Default arguments
CFLAGS=-c -Wl,--add-stdcall-alias -static -fno-strict-aliasing -fPIC -O2 -Wall -DNDEBUG -D_REENTRANT -DLUA_USE_LINUX -DLUA_USE_POSIX 
LDFLAGS=-shared -lm -s

# Description blocks
all: libjnlua$(VERSION).so javavm.so

libjnlua$(VERSION).so: jnlua.o
	$(LD) $(LDFLAGS)  -olibjnlua$(VERSION).so -L$(LUA_LIB_DIR) jnlua.o -l luajit$(VERSION) -pthread

jnlua.o: ../jnlua.c
	$(CC) $(CFLAGS) -I$(JDK_DIR)/include -I$(JDK_DIR)/include/linux -I$(LUA_INC_DIR) ../jnlua.c

javavm.so: javavm.o
	$(LD) $(LDFLAGS) -Wl,-soname=javavm.so -ojavavm.so -L$(LUA_LIB_DIR) -L$(JDK_DIR)/jre/lib/$(ARCH)/server javavm.o -l luajit$(VERSION) -ljvm -pthread -lpthread

javavm.o: ../javavm.c ../javavm.h
	$(CC) $(CFLAGS) -I$(JDK_DIR)/include -I$(JDK_DIR)/include/linux -I$(LUA_INC_DIR) ../javavm.c

clean:
	-rm libjnlua$(VERSION).so jnlua.o
	-rm javavm.so javavm.o

