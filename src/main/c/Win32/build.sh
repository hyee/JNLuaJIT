cd $(dirname "$0")
make clean
rm -f CMakeCache.txt cmake_install.cmake
cp -f CMakeLists.txt ../
cmake .. \
    -DCMAKE_SYSTEM_NAME=Windows \
    -DCMAKE_C_COMPILER=i686-w64-mingw32-gcc

make
strip *.dll