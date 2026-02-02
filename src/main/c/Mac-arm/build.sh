#!/bin/bash
cd "$(dirname "$0")"
make clean
rm -rf CMakeCache.txt cmake_install.cmake CMakeFiles Makefile
cp -f ../jnlua.c ../javavm.c ../javavm.h .

# 检查 OSXCross 环境
if [ -z "$OSXCROSS_PATH" ]; then
    echo "Error: OSXCROSS_PATH not set"
    echo "Please set up OSXCross first:"
    echo "1. git clone https://github.com/tpoechtrager/osxcross"
    echo "2. cd osxcross"
    echo "3. Download MacOS SDK (e.g., MacOSX11.3.sdk.tar.xz) to ./tarballs/"
    echo "4. UNATTENDED=1 ./build.sh"
    echo "5. export OSXCROSS_PATH=/path/to/osxcross"
    exit 1
fi

# 检查必要的工具链
if ! [ -x "$OSXCROSS_PATH/target/bin/aarch64-apple-darwin20.4-clang" ]; then
    echo "Error: OSXCross toolchain not found"
    exit 1
fi

# 配置和编译
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=$OSXCROSS_PATH/toolchain.cmake \
    -DCMAKE_C_FLAGS="-fPIC -O2 -g -Wall" \
    -DCMAKE_SHARED_LINKER_FLAGS="-undefined dynamic_lookup"

make

# 清理
rm jnlua.c javavm.c javavm.h
