# Paths
#JDK_DIR=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
#for arm, use jdk-24, for x86, use jdk-21
JDK_BASE=/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents
JDK_DIR=$(JDK_BASE)/Home
LUA_LIB_DIR=/Users/will/dbcli/lib/macos/
LUA_INC_DIR=/Users/will/LuaJIT-2.1/src
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
	$(LD) $(LDFLAGS) -o javavm.so javavm.o -L$(JDK_BASE)/MacOS -ljli -L$(JDK_DIR)/lib/server -ljvm
javavm.o: ../javavm.c ../javavm.h
	$(CC) $(CFLAGS) -I$(JDK_DIR)/include -I$(JDK_DIR)/include/darwin -I$(LUA_INC_DIR) ../javavm.c

clean:
	-rm *.o .so
