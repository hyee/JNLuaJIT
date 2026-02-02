# JNLua (Java Native Lua)

[![License](http://img.shields.io/badge/Licence-MIT-brightgreen.svg)](https://en.wikipedia.org/wiki/MIT_License)

JNLua (Java Native Lua) integrates the [Lua Scripting Language](http://www.lua.org/) into Java. The integration is based on the original implementation of Lua which is written in ANSI C. The Lua C code is integrated into Java using the Java Native API (JNI).

## Features

JNLua provides the following features:

* **Full Lua support with full Java type-safety.** JNLua provides the full functionality of Lua C API including large parts of the Lua Auxiliary Library. All Lua Standard Libraries are supported, including the coroutine functions. At the same time, JNLua maintains the type-safety of the Java VM by performing rigorous checks in its native library.
  
* **Two-way integration.** With JNLua, you can access Java from Lua and Lua from Java. From Lua, JNLua provides full Java object access with intuitive syntax and the ability to implement Java interfaces in Lua. From Java, JNLua provides full Lua access including the ability to implement Lua functions in Java. The integration works transparently in both directions and on each end conforms to the common principles of the respective platform.

* **Dual bootstrapping.** JNLua can be started from both the Java and the Lua side. If started from the Java side, a Lua state is attached to the calling Java virtual machine; if started from the Lua side, a Java virtual machine is attached to the calling Lua process.

* **Extensive language bindings.** The bindings between Lua and Java are abstracted into the domains of _Java reflection_ and _conversion_. The _default Java reflector_ supports field, method and property access on Java classes and objects. For overloaded methods, it provides a dispatch logic that mimics the behavior described in Java Language Specification. The _default converter_ handles the bidirectional conversion of primitive types, such as numbers and strings. For complex types, it supports the bidirectional mapping of Lua tables to Java maps, lists and arrays. These mappings are generally implemented with proxy objects, that is, they work _by reference_. Both the Java reflector and converter can be specialized to fit custom needs.

* **Java module.** The JNLua Java module provides a small but comprehensive set of Lua functions providing Java language support for Lua.

* **Java VM module.** The Java VM module is a Lua module written in C that allows a Lua process to create a Java Virtual Machine and run Java code in that machine.

* **Transparent error handling.** Java does error handling by exceptions; Lua uses mechanics such as `error()` and `pcall()`. JNLua ensures a seamless translation of error conditions between the two domains. Lua errors are reported as exceptions to Java. Java exceptions generate errors on the Lua side.

* **JSR 223: Scripting for the Java Platform provider.** JNLua includes a provider that conforms to the [JSR 223: Scripting for the Java Platform](http://www.jcp.org/en/jsr/detail?id=223) specification. This allows the use of Lua as a scripting language for Java in a standardized way. The JSR 223 provider also supports the optional Compilable and Invocable interfaces.

* **JNLua Console.** A simple console implemented in Java for experimenting with JNLua.

## Design Goals

The following goals drive the design of JNLua:
* **Stability.** Both Java and Lua are mature and stable platforms. However, the underlying Java VM is a type-safe in a way that ANSI C is not. When loading C code into the Java VM, one must be careful not to corrupt the type-safety of the VM. JNLua achieves this goal by rigorous argument checking, stack checking and a sufficient amount of internal synchronization.
* **Performance.** Lua has a reputation of being a very fast scripting language. Basically, JNLua simply tries not to get in the way of that performance ;) In support of good performance, JNLua avoids copying values between Java and Lua. Instead, JNLua uses proxy objects where possible.
* **Simplicity.** JNLua aims at being simple and easy to learn by adhering to well-known patterns in both the Java and the Lua world. Lua provides a large spectrum of flexibility with relatively few, but well-designed features. JNLua _tries_ to adhere to that spirit.

## Architecture

The figure below depicts the architecture of JNLua (see Architecture.png in docs folder for visual representation):

When JNLua is bootstrapped from Java, the Java code calls the JNLua Java library. The JNLua Java library invokes the JNLua native library via JNI, and the JNLua native library calls the Lua C API. Lua code can use the JNLua Java module to access Java functionality.

When JNLua is bootstrapped from Lua, the Lua code uses the JNLua Java VM module to create a Java virtual machine with the JNLua Java library, and then calls the JNLua Java module to access Java functionality.

## Installation

JNLua can be installed in the following ways:

### Maven Dependency
```xml
<dependency>
    <groupId>com.naef</groupId>
    <artifactId>jnlua</artifactId>
    <version>1.0.4</version>
</dependency>
```

### Manual Installation
1. Download the `jnlua-{version}.jar` file and add it to your classpath
2. Place the native library file in a location where the Java Virtual Machine can find it (e.g., `C:\Windows\System32` on Windows or `/usr/lib` on Linux)

## Getting Started

### Using Lua from Java

```java
import com.naef.jnlua.LuaState;

public class Simple { 
    public static void main(String[] args) { 
        // Create a Lua state 
        LuaState luaState = new LuaState(); 
        try { 
            // Define a function 
            luaState.load("function add(a, b) return a + b end", "=simple"); 

            // Evaluate the chunk, thus defining the function 
            luaState.call(0, 0); // No arguments, no returns 

            // Prepare a function call 
            luaState.getGlobal("add"); // Push the function on the stack 
            luaState.pushInteger(1); // Push argument #1 
            luaState.pushInteger(1); // Push argument #2 

            // Call 
            luaState.call(2, 1); // 2 arguments, 1 return 

            // Get and print result 
            int result = luaState.toInteger(1); 
            luaState.pop(1); // Pop result 
            System.out.println("According to Lua, 1 + 1 = " + result); 
        } finally { 
            luaState.close(); 
        } 
    } 
}
```

### Using Java from Lua

```lua
-- Import Java class
System = java.require("java.lang.System")

-- Call static method
print(System:currentTimeMillis())

-- Read static field
out = System.out
out:println("Hello, world!")

-- Create object and call methods
StringBuilder = java.require("java.lang.StringBuilder")
sb = StringBuilder:new()
sb:append("Hello ")
sb:append("World")
out:println(sb:toString())
```

## Java Module API

JNLua provides a Java module that enables Lua to access Java functionality:

* `java.require(typeName [, import])` - Loads a Java type
* `java.new(type|typeName, {dimension})` - Creates a Java object or array
* `java.instanceof(object, type|typeName)` - Checks if an object is an instance of a specific type
* `java.cast(value, type|typeName)` - Converts a value to a specific type
* `java.proxy(table, interface|interfaceName...)` - Creates a proxy object implementing Java interfaces
* `java.pairs(map)` - Provides an iterator for Java maps
* `java.ipairs(array|list)` - Provides an iterator for Java arrays or lists
* `java.totable(list|map)` - Wraps Java collections as Lua tables
* `java.tolua(list|map|array)` - Directly converts Java collections to native Lua tables
* `java.elements(iterable)` - Provides an iterator for Java iterable objects
* `java.fields(class|object)` - Iterates Java class or object fields
* `java.methods(class|object)` - Iterates Java class or object methods
* `java.properties(object)` - Iterates Java object properties

## JSR-223 Support

JNLua fully supports the JSR 223: Scripting for the Java Platform specification.

```java
import javax.script.*;

ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("Lua");

// Execute script in Lua
engine.eval("print('Hello from Lua!')");

// Pass Java object to Lua
engine.put("name", "JNLua");
Object result = engine.eval("return 'Hello, ' .. name");
```

## Java VM Module

The Java VM module is a Lua module written in C that allows a Lua process to create a Java Virtual Machine and run Java code in that machine. For better structure, the documentation of the [Java VM module](JavaVMModule.md) is on a separate page.

## Requirements

JNLua 2.0 requires LuaJIT

Max Raskin has done a port of JNLua for the [Android platform](https://github.com/airminer/jnlua-android).

## Building from Source

### Building Java Components
```bash
mvn clean package
```

### Building Native Library

JNLua uses a native library which integrates Lua with Java by means of the Java Native API (JNI). The native library is written in ANSI C and invokes the actual Lua implementation provided by a shared Lua library. To build from source, you'll need to compile both the Java code and the native C library. See the [Building the Native Library](docs/BuildingTheNativeLibrary.md) documentation for detailed instructions on building for different platforms (Linux, Windows, macOS, etc.).

## Error Handling

JNLua uses the following exceptions:
* `LuaRuntimeException` - If a Lua runtime error occurs
* `LuaSyntaxException` - If the syntax of a Lua chunk is incorrect
* `LuaMemoryAllocationException` - If the Lua memory allocator runs out of memory or if a JNI allocation fails
* `LuaGcMetamethodException` - If an error occurs running a `__gc` metamethod during garbage collection
* `LuaMessageHandlerException` - If an error occurs running the message handler of a protected call

## License

JNLua is licensed under the MIT license which at the time of this writing is the same license as the one of Lua.