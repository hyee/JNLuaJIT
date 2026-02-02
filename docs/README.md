# JNLua Documentation

Welcome to the JNLua (Java Native Lua) documentation!

JNLua is a high-performance library for integrating the Lua scripting language into Java. The integration is based on the original implementation of Lua which is written in ANSI C. The Lua C code is integrated into Java using the Java Native Interface (JNI).

## Quick Navigation

- [Home](Home.md) - Project Overview
- [Getting Started](GettingStarted.md) - Quick Start
- [Java API Reference](JavaApiReference.md) - Detailed Java-side API documentation
- [Lua API Reference](LuaApiReference.md) - Detailed Lua-side API documentation
- [JSR 223 Support](JSR223Provider.md) - JSR 223 specification integration
- [Building the Native Library](BuildingTheNativeLibrary.md) - How to build the C code portion

## Documentation Contents

See [TableOfContents.md](TableOfContents.md) for the complete documentation contents.

## Features

- **Two-way Integration**: Supports accessing Lua from Java and Java from Lua
- **Type Safety**: Maintains type safety within the Java Virtual Machine
- **High Performance**: Avoids unnecessary value copying, uses proxy objects
- **Standard Compliant**: Supports JSR 223 scripting specification
- **Rich Features**: Provides Java module, console and other functionalities

## License

JNLua is licensed under the MIT license which is the same as Lua's license.