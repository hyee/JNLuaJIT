#!/bin/bash
cd $(dirname "$0")
make clean
rm -rf CMakeCache.txt cmake_install.cmake CMakeFiles Makefile *.so
cp -f ../jnlua.c ../javavm.c ../javavm.h .
cp CMakeLists.txt ../

# 检查交叉编译器
if ! command -v aarch64-linux-gnu-gcc &> /dev/null; then
    echo "Error: aarch64-linux-gnu-gcc not found"
    echo "Please install the cross compiler: sudo apt-get install gcc-aarch64-linux-gnu"
    exit 1
fi

# 配置和编译
cmake .. \
    -DCMAKE_C_FLAGS="-fPIC -O2 -g -Wall" \
    -DCMAKE_SHARED_LINKER_FLAGS="-static-libgcc" \
    -DCMAKE_SYSTEM_NAME=Linux \
    -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
    -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
    -DCMAKE_FIND_ROOT_PATH=/usr/aarch64-linux-gnu

make

# 清理
rm jnlua.c javavm.c javavm.h
mv jnlua5.1.so libjnlua5.1.so