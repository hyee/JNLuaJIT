# Paths
JDK_DIR=/Library/Java/JavaVirtualMachines/jdk1.8.0_311.jdk/Contents/Home
LUA_LIB_DIR=/Users/tylert/luajit.old/src
LUA_INC_DIR=/Users/tylert/luajit.old/src
VERSION=5.1

# Tools
CC=gcc -isysroot /Library/Developer/CommandLineTools/SDKs/MacOSX.sdk
LD=gcc

# Default arguments
CFLAGS=-c -fno-strict-aliasing -m64 -fPIC -O2 -g -Wall -DNDEBUG -D_REENTRANT -DLUA_USE_POSIX -std=c99
LDFLAGS=-m64 -bundle -undefined dynamic_lookup -lm -s -std=c99

# Description blocks
all: libjnlua$(VERSION).so javavm.so

libjnlua$(VERSION).so: jnlua.o
	$(LD) $(LDFLAGS) -o libjnlua$(VERSION).so jnlua.o 

jnlua.o: ../jnlua.c
	$(CC) $(CFLAGS) -I$(JDK_DIR)/include -I$(JDK_DIR)/include/darwin -I$(LUA_INC_DIR) ../jnlua.c

javavm.so: javavm.o
	$(LD) $(LDFLAGS) -o javavm.so javavm.o -L$(JDK_DIR)/jre/lib/jli -ljli

javavm.o: ../javavm.c ../javavm.h
	$(CC) $(CFLAGS) -I$(JDK_DIR)/include -I$(JDK_DIR)/include/darwin -I$(LUA_INC_DIR) ../javavm.c

clean:
	-rm *.o .so
