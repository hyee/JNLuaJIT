cd $(dirname "$0")
make clean
cp -f CMakeLists.txt ../
cmake .. \
    -DCMAKE_SYSTEM_NAME=Windows \
    -DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc
make
strip *.dll