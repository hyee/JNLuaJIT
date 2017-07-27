# Paths
JDK_DIR=/Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home
LUA_LIB_DIR=/Users/tylert/luajit/src
LUA_INC_DIR=/Users/tylert/luajit/src
VERSION=5.1

# Tools
CC=gcc
LD=gcc

# Default arguments
CFLAGS=-c -fno-strict-aliasing -m64 -fPIC -O2 -Wall -DNDEBUG -D_REENTRANT -DLUA_USE_POSIX
LDFLAGS=-m64 -bundle -undefined dynamic_lookup -lm -static-libgcc

# Description blocks
all: libjnlua$(VERSION).so javavm.so

libjnlua$(VERSION).so: jnlua.o
	$(LD) $(LDFLAGS) -o libjnlua$(VERSION).so jnlua.o 

jnlua.o: ../jnlua.c
	$(CC) $(CFLAGS) -I$(JDK_DIR)/Headers -I$(LUA_INC_DIR) ../jnlua.c

javavm.so: javavm.o
	$(LD) $(LDFLAGS) -o javavm.so javavm.o -L$(JDK_DIR)/jre/lib/jli -ljli

javavm.o: ../javavm.c ../javavm.h
	$(CC) $(CFLAGS) -I$(JDK_DIR)/include -I$(JDK_DIR)/include/darwin -I$(LUA_INC_DIR) ../javavm.c

clean:
	-rm *.o .so
