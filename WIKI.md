# JNLua Wiki

Welcome to the JNLua Wiki! This comprehensive guide covers all aspects of JNLua (Java Native Lua), a high-performance library for integrating the Lua scripting language into Java environments.

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [Installation](#installation)
4. [Getting Started](#getting-started)
5. [API Reference](#api-reference)
6. [Advanced Topics](#advanced-topics)
7. [Building from Source](#building-from-source)
8. [Troubleshooting](#troubleshooting)

## Overview

JNLua (Java Native Lua) integrates the [Lua Scripting Language](http://www.lua.org/) into Java. The integration is based on the original implementation of Lua which is written in ANSI C. The Lua C code is integrated into Java using the Java Native API (JNI).

JNLua provides bidirectional integration between Java and Lua, allowing Java code to execute Lua scripts and Lua code to access Java classes and objects seamlessly.

### Architecture

When JNLua is bootstrapped from Java, the Java code calls the JNLua Java library. The JNLua Java library invokes the JNLua native library via JNI, and the JNLua native library calls the Lua C API. Lua code can use the JNLua Java module to access Java functionality.

When JNLua is bootstrapped from Lua, the Lua code uses the JNLua Java VM module to create a Java virtual machine with the JNLua Java library, and then calls the JNLua Java module to access Java functionality.

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

## Installation

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

For a comprehensive introduction to the Lua API, the chapters on the C API in [Programming in Lua, Second Edition](http://www.lua.org/docs.html#books) may be of interest in addition to the Lua Reference Manual. A full description of Lua API is beyond the scope of the JNLua documentation.

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

Java access from Lua works by reflection. Java classes and objects are reflected into Lua. The following Lua code illustrates more advanced usage:

```lua
System = java.require("java.lang.System") -- import class into Lua 
print(System:currentTimeMillis()) -- invoke static method 

out = System.out -- read static field 
out:println("Hello, world!") -- invoke method 

StringBuilder = java.require("java.lang.StringBuilder") 
sb = StringBuilder:new() -- invoke constructor 
sb:append("a") -- invoke method 
sb:append("b") 
out:println(sb:toString()) 

TimeZone = java.require("java.util.TimeZone") 
timeZone = TimeZone:getDefault() 
out:println(timeZone.displayName) -- read property; invokes getDisplayName() 

Calendar = java.require("java.util.Calendar") 
today = Calendar:getInstance() 
print(tostring(today)) -- invokes Object.toString() 
tomorrow = today:clone() 
tomorrow:add(Calendar.DAY_OF_MONTH, 1) 
assert(today < tomorrow) -- invokes Comparable.compareTo() 
```

### Using JSR-223

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

## API Reference

### Java API Reference

#### LuaState Class

`LuaState` is the core class of JNLua, representing a Lua instance. It corresponds to the `lua_State` pointer in the underlying Lua C API, providing access to the entire Lua virtual machine.

The interface of the Lua state class corresponds largely to the underlying Lua C API which is documented in the [Lua Reference Manual](http://www.lua.org/manual/5.1). Method names have been adjusted to Java camel case and the `lua_` and `luaL_` prefixes haven been omitted since this information is implied in the Java package name. Also, the Java API takes advantage of method overloading and uses a single method name for C API functions that primarily differ in their number arguments. For example, the Java API provides `rawGet(int)` and `rawGet(int, int)` whereas the C API provides `lua_rawget()` and `lua_rawgeti()`.

A JNLua Lua state subsumes the Lua main thread and all additional Lua threads created by means of the coroutine API. Therefore, there is no separate Java Lua state instance for each Lua thread. Instead, Lua threads are treated as Lua values and referenced by stack index in the coroutine API methods. The coroutine API methods have been slightly adapted from their C behavior to that end. Functionality-wise, they pretty much correspond to the functions in the Lua coroutine module.

You should free Lua states that are no longer in use by explicitly invoking the `LuaState.close()` method. Otherwise, resources on the native side are not freed until the Lua state (or more precisely, the _finalize guardian_ of the Lua state) is collected by the Java garbage collector.

##### Creation and Destruction
```java
// Create a new Lua state
LuaState luaState = new LuaState();

// Close and release resources
luaState.close();
```

##### Basic Operations
```java
// Load and execute code
luaState.load("function add(a, b) return a + b end", "chunk");
luaState.call(0, 0); // Execute the loaded code

// Call function
luaState.getGlobal("add");      // Get global function
luaState.pushInteger(5);        // Push argument
luaState.pushInteger(3);        // Push argument
luaState.call(2, 1);            // Call function (2 arguments, 1 return value)
int result = luaState.toInteger(-1); // Get result
luaState.pop(1);                // Pop result
```

##### Java Function Implementation
```java
class MyJavaFunction implements NamedJavaFunction {
    @Override
    public int invoke(LuaState luaState) {
        double arg1 = luaState.checkNumber(1);
        double arg2 = luaState.checkNumber(2);
        double result = arg1 / arg2;
        luaState.pushNumber(result);
        return 1; // Number of return values
    }

    @Override
    public String getName() {
        return "divide";
    }
}

// Register function
luaState.register("mylib", new NamedJavaFunction[]{new MyJavaFunction()});
```

Java functions can be pushed on the Lua stack by means of the `LuaState.pushJavaFunction()` method. The Lua state also provides the method `LuaState.register()` which registers the Java function in the global scope. The register method requires that the Java function implements the `NamedJavaFunction` interface. That interface extends base interface by an additional method that returns the name of the function.

In Java, _checked_ exceptions are used to indicate application errors and _unchecked_ exceptions are used to indicate programming errors. In Lua, return values are used to indicate application errors and Lua errors are used to indicate programming errors. In case a Java function requires to signal an application error, it should return an appropriate error code; in case a Java function requires to signal a programming error, it should throw an unchecked exception. The unchecked exception is caught by JNLua and translated to a Lua error.

##### Proxy Object Creation
```java
// Create Java interface implementation from Lua
luaState.load("myRunnable = { run = function() print('Running from Lua') end }");
luaState.call(0, 0);

luaState.getGlobal("myRunnable");
Runnable runnable = luaState.getProxy(-1, Runnable.class);
luaState.pop(1);

// Run in Java thread
new Thread(runnable).start();
```

Java interfaces can be implemented in Lua. The Lua state provides the method `LuaState.getProxy()` to that end. The method takes as arguments a stack index containing a Lua table and a Java interface type. The keys of the table are expected to match the names of the interface methods, the values are expected to be functions providing the corresponding implementations. Another signature of the `LuaState.getProxy()` method allows creating proxies implementing multiple interfaces.

##### Implementing Modules in Java
JNLua supports the creation of Lua modules from Java. To that end, the Lua state provides the LuaState.register method which takes as arguments a module name and an array of named Java functions to populate the module with. When the method returns, the module table is on top of the Lua stack and can be further populated.

```java
public void registerSimple(LuaState luaState) {
	// Register the module 
	luaState.register("simple", new NamedJavaFunction[] { new Divide() });

	// Set a field 'VERSION' with the value 1 
	luaState.pushInteger(1); 
	luaState.setField(-2, "VERSION"); 

	// Pop the module table 
	luaState.pop(1); 
}
```

#### JavaModule Class

The Java module provides a set of Lua functions that enable Lua code to access Java functionality.

##### Provided Functions
- `java.require(typeName [, import])` - Loads a Java type
- `java.new(type|typeName, {dimension})` - Creates a Java object or array
- `java.instanceof(object, type|typeName)` - Checks type instance
- `java.cast(value, type|typeName)` - Type conversion
- `java.proxy(table, interface|interfaceName...)` - Creates interface proxy
- `java.pairs(map)` - Map iterator
- `java.ipairs(array|list)` - Array/List iterator
- `java.totable(list|map)` - Wraps as Lua table
- `java.tolua(list|map|array)` - Converts to native Lua table

#### Threading and Synchronization

Lua states are _conditionally thread-safe_. This means that a Lua state performs enough internal synchronization to protect its integrity when used by multiple threads. However, the outcome of certain operations depends on the order by which the methods are invoked. Therefore, clients should synchronize on the Lua state at a higher level to ensure the consistency of their operations if a Lua state is used by multiple threads.

For example, if a client pushes a value on the Lua stack and performs an operation with that value in the next step, the client should synchronize on the Lua state to ensure that the two operations are not influenced by another thread working with the same Lua state. The following code fragment shows the proper use of a Lua state in environments with multiple threads:

```java
synchronized (luaState) { 
	luaState.getGlobal("add"); 
	luaState.pushInteger(1); 
	luaState.pushInteger(1); 
	luaState.call(2, 1); 
	int result = luaState.toInteger(1); 
	luaState.pop(1); 
} 
```

#### Error Handling

##### JNLua Exception Hierarchy
```
Throwable
 └── Exception
     └── RuntimeException
         └── LuaException
             ├── LuaRuntimeException
             ├── LuaSyntaxException
             ├── LuaMemoryAllocationException
             ├── LuaGcMetamethodException
             └── LuaMessageHandlerException
```

##### Exception Descriptions
- `LuaRuntimeException`: Lua runtime error, such as calling a non-existent function
- `LuaSyntaxException`: Lua code syntax error
- `LuaMemoryAllocationException`: Out of memory error
- `LuaGcMetamethodException`: Metamethod error during garbage collection
- `LuaMessageHandlerException`: Message handler error

All exceptions are derived from `LuaException`.

Note that all Lua exceptions are _unchecked_ exceptions. It is recommended that clients use a try...catch clause to catch `LuaException` and its subclasses.

The Lua runtime exception class provides the method `LuaRuntimeException.getLuaStackTrace()` which returns the Lua stack trace of the error. Additional methods of the class allow printing the Lua stack trace to various outputs.

##### Best Practices
```java
try {
    luaState.load(largeScript, "myscript");
    luaState.call(0, 0);
} catch (LuaSyntaxException e) {
    System.err.println("Syntax error: " + e.getMessage());
} catch (LuaRuntimeException e) {
    System.err.println("Runtime error: " + e.getMessage());
    // Print Lua stack trace
    e.printLuaStackTrace(System.err);
} catch (LuaException e) {
    System.err.println("Lua error: " + e.getMessage());
}
```

When using LuaState in a multithreaded environment, ensure synchronized access:
```java
synchronized(luaState) {
    luaState.getGlobal("add");
    luaState.pushInteger(1);
    luaState.pushInteger(1);
    luaState.call(2, 1);
    int result = luaState.toInteger(1);
    luaState.pop(1);
}
```

### Lua API Reference

#### Accessing Java from Lua

JNLua provides rich APIs that allow accessing Java functionality from Lua scripts.

Java access from Lua works by reflection. Java classes and objects are reflected into Lua. The following Lua code illustrates this.

```lua
System = java.require("java.lang.System") -- import class into Lua 
print(System:currentTimeMillis()) -- invoke static method 

out = System.out -- read static field 
out:println("Hello, world!") -- invoke method 

StringBuilder = java.require("java.lang.StringBuilder") 
sb = StringBuilder:new() -- invoke constructor 
sb:append("a") -- invoke method 
sb:append("b") 
out:println(sb:toString()) 

TimeZone = java.require("java.util.TimeZone") 
timeZone = TimeZone:getDefault() 
out:println(timeZone.displayName) -- read property; invokes getDisplayName() 

Calendar = java.require("java.util.Calendar") 
today = Calendar:getInstance() 
print(tostring(today)) -- invokes Object.toString() 
tomorrow = today:clone() 
tomorrow:add(Calendar.DAY_OF_MONTH, 1) 
assert(today < tomorrow) -- invokes Comparable.compareTo() 
```

#### Java Reflection Mapping

The table below explains the mapping of Java elements to Lua elements as implemented by the default Java reflector:

| **From Java Element** | **To Lua Element** |
|:----------------------|:-------------------|
| `Class` | Table-like value with keys for static fields and methods of the Java type. Constructors are mapped to a fixed key `new`. For interface types, the `new` method accepts a single table argument and returns a proxy implementing the interface with the Lua functions provided in the table. |
| `Object` | Table-like value with keys for fields, methods and properties of the Java object. |
| `toString()` | `__tostring` metamethod. This implies that the Lua `tostring()` function has the semantics of the Java `toString()` method. |
| `equals()` | `__eq` metamethod. This implies that the Lua `==` and `~=` operators have the semantics of the Java `equals()` method. |
| `Comparable` | `__le` and `__lt` metamethods. This implies that the Lua `<`, `<=`, `>=` and `>` operators have the the semantics of the Java `compareTo()` method. |
| `[]` | Lua value behaving similar to a regular Lua table used as an array, except that the size is fixed. Indexing in Lua is 1-based. The Lua value is a proxy for the Java array. Therefore, changes on the Lua side go through to the Java array and vice versa. The `#` operator returns the length of the Java array. As of JNLua 1.0, the `__ipairs` metamethod provides an iterator over the indexes and values of the array. |
| `List` | The `__ipairs` metamethod provides an interator over the indexes and values of the list. |
| `Map` | The `__pairs` metamethod provides an iterator over the keys and values of the map. |

#### External Functions/Attributes for Java Objects

| Function / Field | Description |
|:----------------------|:-------------------|
| `java_class_name` | Returns the java class name |
| `java_fields(<obj>)` | Returns a `pair` function of fields, same to `java.fields(<obj>)` |
| `java_methods(<obj>)` | Returns a `pair` function of methods, same to `java.methods(<obj>)` |
| `java_properties(<obj>)` | Returns a `pair` function of properties, same to `java.properties(<obj>)` |
| `to_table(<object>)` | Accesses Java list/map , same to `java.totable(<obj>)` | 
| `to_lua(<object>)` | Converts Java list/map/array into native Lua table , same to `java.tolua(<obj>)` | 
| `JNI_GC(<obj>)` | Calls `__gc()` function |

#### Type Conversion from Lua to Java

The following table describes the type mapping from Lua to Java, as implemented in the default converter. The type mapping is driven by the type of the Lua value to convert from and the formal Java type to convert to.

Each conversion is assigned a _type distance_. That distance is an indication of the preference of the conversion. A lower distance means higher preference. If multiple conversions are available, the converter selects the conversion with the lowest distance.

| **From Lua Type** | **To Formal Java Type** | **Type Distance** |
|:------------------|:------------------------|:------------------|
| any | `LuaValueProxy` | 0 |
| `nil` | `null` | 1 |
| `boolean` | `Boolean`, `boolean` | 1 |
| `boolean` | `Object` (actual type `Boolean`) | 2 |
| `number` | `Byte`, `byte`, `Short`, `short`, `Integer`, `int`, `Long`, `long`, `Float`, `float`, `Double`, `double`, `BigInteger`, `BigDecimal`, `Character`, `char` | 1 |
| `number` | `Object` (actual type `Double`) | 2 |
| `number` | `String`, `byte[]` | 3 |
| `string` | `String`, `byte[]` | 1 |
| `string` | `Object` (actual type `String`) | 2 |
| `string` | `Byte`, `byte`, `Short`, `short`, `Integer`, `int`, `Long`, `long`, `Float`, `float`, `Double`, `double`, `BigInteger`, `BigDecimal`, `Character`, `char` | 3 |
| `table` | `Map`, `List`, any array type | 1 |
| `table` | `Object` (actual type `Map`) | 2 |
| `function` (Java) | `JavaFunction` | 1 |
| `function` (Java) | `Object` (actual type `JavaFunction`) | 2 |
| `userdata` (Java object) | the class of the Java object or any superclass thereof | 1 |
| `userdata` (Java object) | any interface implemented by the Java object or any superinterface thereof | 1 |
| any | `Object` (actual type `LuaValueProxy`) | Integer.MAX_VALUE - 1 |

Additional notes:

  * If there is no applicable conversion, the _type distance_ is  `Integer.MAX_VALUE`, indicating that the conversion is not supported.
  * The `Map` and `List` objects returned by the converter are proxies for the Lua table. Therefore, changes on the Java side go through to the Lua table and vice versa.
  * For Java objects implementing the `TypedJavaObject` interface, the type and object returned by the corresponding interface methods are used.

Note that for each Lua type, there is a conversion to `Object`. Therefore, requests for this type never fail. In the absence of precise formal Java types, requesting a conversion to `Object` is recommended since it produces the natural Java type for most Lua types.

The interface `LuaValueProxy` is a generic proxy for any type of Lua value. The proxy allows pushing its proxied value on the Lua stack, thus making the value accessible on demand. Proxies are currently implemented by means of the Lua Auxiliary Library _reference system_ and Java _phantom references_. After the Java garbage collector has finalized a Lua value proxy that is no longer in use, the proxy becomes _phantom reachable_. At that point, its reference will be removed in Lua upon the next JNLua API call. This design ensures that there are no asynchronous calls from a Java garbage collector thread into Lua.

As of JNLua 0.9.5 and 1.0.3, Java byte arrays are transparently converted to and from Lua strings. While Java has a strong distinction between binary information (`byte`, `byte[]`) and textual information (`char`, `String`), Lua uses strings to hold both binary and textual information. In case the transparent conversion causes issues with existing code, the following options are available to address such situations:

  * Set the system property `com.naef.jnlua.rawByteArray` to `true`. This disables the transparent conversion in the default converter and restores the behavior of previous versions where Java byte arrays are passed raw. You can still pass a byte array as a string by explicitly invoking the `pushByteArray` and `toByteArray` methods on the Lua state.
  * (If the system property is not set; default.) Use the Lua state methods `pushJavaObjectRaw` and `toJavaObjectRaw` to explicitly pass a Java byte array in raw form, bypassing the converter. Use the `java.cast` function to resolve ambivalence when invoking same-name methods with both a `String` and `byte[]` signature.
  * Adapt your own converter, as explained in the next section.

#### Implementing Custom Java Reflection and Conversion

The Java reflection and conversion can be customized by providing custom implementations of the `JavaReflector` and `Converter` interfaces. The custom implementations are then configured in the Lua state by invoking the `LuaState.setJavaReflector()` and `LuaState.setConverter()` methods.

A custom Java reflector allows overriding how an object reacts to operators and other events for which Lua provides metamethods.

A custom converter allows overriding how values are converted between Lua and Java.

The default implementations provided by `DefaultJavaReflector` and `DefaultConverter` are not intended to be subclassed. However, it is easy to encapsulate them. To that end, you can create your own implementation of the respective interface and forward calls that do not require customized handling to the default implementation.

If you require customized Java reflection for a specific class, you can have that class implement the `JavaReflector` interface directly. This may be simpler than creating and configuring a custom Java reflector. If an object implements the Java reflector interface, its own Java reflector is queried first for a requested metamethod. Only if the requested metamethod is not provided by the object, the metamethod provided by the Java reflector configured in the Lua state is used. This mechanic is used by the wrapper objects created by the `java.totable()` function. These wrapper objects provide custom Java reflection for the `__index` and `__newindex` metamethods in order to customize the behavior of the returned objects with regard to the Lua index operator. The wrapper objects also implement the `TypedJavaObject` interface to ensure that they behave like the wrapped `List` or `Map` with regard to other operations.

#### Java Method Dispatch

Java method dispatching refers to the process of selecting the Java method (or constructor) to invoke with a given argument signature. The process has some inherent complexity due to overloading. Java allows to declare multiple same-name methods that only differ in their formal parameter types. It is the responsibility of the method dispatcher to select the "right" method for each method invocation. In normal Java code, this selection is performed statically by the Java compiler. In JNLua, the selection must be done at runtime. To that end, JNLua provides a method dispatcher that mimics the behavior described in the Java Language Specification.

Informally, the JNLua method dispatcher selects from the set of candidate methods the one that is _closest_ and _most specific_.

More formally, the default Java reflector performs the following steps to dispatch a Java method:

**Input:** A Lua call signature consisting of a method name and the Lua types of the arguments. If an argument is a Java function, that distinction is noted. If an argument is a Java object, its Java type is used instead of the Lua type.

**Step 1:** Start with a set of all same-name methods provided by the target type of the call.

**Step 2:** Eliminate methods with a non-matching static modifier. If the call is targeted at a class, the non-static methods are eliminated; if the call is targeted at an object, the static methods are eliminated.

**Step 3:** Eliminate methods with a non-matching argument count. Methods with fixed arguments are eliminated if their argument count is different from the number of arguments in the Lua call; methods with variable arguments are eliminated if their argument count minus one is greater than the number of arguments in the Lua call.

**Step 4:** Eliminate methods that cannot be called due to type mismatch. In this step, for each method, the dispatcher checks the _type distance_ from the Lua type to the formal Java type of each argument. If the _type distance_ is `Integer.MAX_VALUE` for any argument, the method is eliminated.

**Step 5:** Eliminate methods with variable arguments if there are method with fixed arguments. If fixed and variable argument methods are applicable, the Java Language Specification gives preference to methods with fixed arguments.

**Step 6:** Eliminate methods that are not _closest_. For each method, if there is another method which for each argument has the same or a lower _type distance_ (of which at least one lower type distance), the method is eliminated.

**Step 7:** Eliminate methods that are not _most specific_. For each method, if there is another method which for each argument has the same type or a subtype thereof (of which at least one subtype), the method is eliminated.

**Output:** A set of methods. If the set contains exactly one method, the invocation proceeds. If the set is empty, the invocation fails due to no matching method. If the set contains more than one method, the invocation fails due to ambivalence.

The default Java reflector caches the result of this calculation once a Lua call signature has been successfully dispatched for the first time.

In the case of method invocations failing due to ambivalence, you may want to use the `java.cast()` function provided by the Java module to resolve the ambivalence.

##### Accessing Java Classes and Objects
```lua
-- Access static members
System = java.require("java.lang.System")
millis = System:currentTimeMillis()
System.out:println("Time: " .. millis)

-- Access instance members
StringBuilder = java.require("java.lang.StringBuilder")
sb = StringBuilder:new()
sb:append("Hello"):append(" World")
result = sb:toString()

-- Property access (through getter/setter)
thread = java.new("java.lang.Thread"):currentThread()
name = thread.name  -- calls getName()
```

##### Lua Mapping of Java Objects

| Java Element | Lua Mapping |
|----------|---------|
| Class | Table-like value, containing static fields and methods |
| Object | Table-like value, containing fields, methods and properties |
| toString() | `__tostring` metamethod |
| equals() | `__eq` metamethod |
| Comparable | `__le` and `__lt` metamethods |
| [] | Lua value, similar to array operations |
| List | `__ipairs` metamethod |
| Map | `__pairs` metamethod |

#### Java Module Functions

##### java.require
```lua
-- Load class and assign to variable
System = java.require("java.lang.System")
currentTime = System:currentTimeMillis()

-- Load class and import to namespace
java.require("java.lang.System", true)  -- Can now be used directly as java.lang.System
```

##### java.new
```lua
-- Create object
StringBuilder = java.require("java.lang.StringBuilder")
sb = java.new(StringBuilder)  -- or StringBuilder:new()

-- Create array
byteArray = java.new("byte", 10)  -- Create a byte array of length 10
stringArray = java.new("java.lang.String", 5)  -- Create a string array
```

##### java.instanceof
```lua
Object = java.require("java.lang.Object")
obj = Object:new()
isObj = java.instanceof(obj, Object)  -- true
```

##### java.cast
```lua
-- Resolve method overload ambiguity
StringBuilder = java.require("java.lang.StringBuilder")
sb = StringBuilder:new()
sb:append(java.cast(1, "int"))  -- Explicitly specify type
```

##### java.proxy
```lua
-- Implement Runnable interface
runnableImpl = {
    run = function()
        print("Executing in Java thread")
    end
}

proxy = java.proxy(runnableImpl, "java.lang.Runnable")
-- Now proxy can be used as Runnable
```

##### java.pairs and java.ipairs
```lua
-- Iterate Java Map
map = java.new("java.util.HashMap")
map:put("key1", "value1")
map:put("key2", "value2")

for k, v in java.pairs(map) do
    print(k, v)
end

-- Iterate Java List
list = java.new("java.util.ArrayList")
list:add("item1")
list:add("item2")

for i, v in java.ipairs(list) do
    print(i, v)
end
```

##### java.totable
```lua
-- Wrap Java collection as Lua table
list = java.new("java.util.ArrayList")
wrappedList = java.totable(list)
wrappedList[1] = "first"  -- Directly use table syntax to operate Java List
```

#### Type Conversion Rules

From Lua to Java conversion:
| Lua Type | Java Type | Priority |
|---------|----------|--------|
| nil | null | 1 |
| boolean | Boolean, boolean | 1 |
| number | Various numeric types | 1 |
| string | String, byte[] | 1 |
| table | Map, List, Array | 1 |

#### Error Handling

In Lua, error handling follows the normal pattern:
```lua
-- Use pcall to catch errors
success, error = pcall(function() 
    java.require("non.existent.Class") 
end)
if not success then
    print(tostring(error))  -- Must use tostring to get error message
end
```

When Java methods throw exceptions, JNLua converts them to Lua errors:
```lua
-- If Java method throws an exception, Lua error will be received here
local success, result = pcall(function()
    -- Call Java method that might throw an exception
    someJavaObject:dangerousOperation()
end)
```

## Advanced Topics

### Implementing Java Interfaces in Lua

Java interfaces can be implemented in Lua. The following example shows how to implement a Runnable interface in Lua:

```java
import com.naef.jnlua.LuaState;

public class InterfaceProxy { 
	public static void main(String[] args) { 
		LuaState luaState = new LuaState(); 
		try { 
			// Open libraries 
			luaState.openLibs(); 

			// Implement interface in Lua 
			luaState.load("runnable = { run = function() print(\"I am running\") end }", "=interface"); 
			luaState.call(0, 0); 

			// Get proxy 
			luaState.getGlobal("runnable"); 
			Runnable runnable = luaState.getProxy(-1, Runnable.class); 
			luaState.pop(1); 

			// Run it 
			Thread thread = new Thread(runnable); 
			thread.start(); 
			thread.join(); 
		} catch (InterruptedException e) { 
			e.printStackTrace(); 
		} finally { 
			luaState.close(); 
		} 
	} 
}
```

### JNLua Console

JNLua includes a simple Lua console for experimenting with JNLua. The console is provided by the `LuaConsole` class.

The console collects input until a line with the sole content of the word **go** is encountered. At that point, the collected input is run as a Lua chunk. If the Lua chunk loads and runs successfully, the console displays the returned values of the chunk as well as the execution time based on a `System.nanoTime()` measurement. Otherwise, the console shows the error that has occurred.

Expressions can be printed by prepending **=** to the expression at the beginning of a chunk. The console translates **=** into **return** followed by a space and executes the chunk immediately. No separate **go** is required. Therefore, expressions printed this way must be entered on a single line.

#### Running the Console

To start the JNLua console, execute the following command:

```bash
java -cp jnlua-1.0.4.jar com.naef.jnlua.console.LuaConsole
```

#### Console Features

##### Multi-line Input
The console allows entering multi-line Lua code blocks:

```
> function factorial(n)
>> if n <= 1 then
>>   return 1
>> else
>>   return n * factorial(n-1)
>> end
>> go
>
```

##### Expression Evaluation
Quick evaluation of expressions using the `=` prefix:

```
> = 2 + 2
4
> = java.require("java.lang.System"):currentTimeMillis()
1627834567890
```

##### Java Integration
The console comes with Java integration enabled by default, allowing direct access to Java classes:

```
> System = java.require("java.lang.System")
> = System:currentTimeMillis()
1627834567890
> out = System.out
> out:println("Hello from Java!")
Hello from Java!
```

##### Execution Timing
When using the `go` command, the console shows execution time:

```
> for i=1,1000000 do end
> go
(nil) [executed in 15 ms]
```

### Java VM Module

The JNLua Java VM module is a Lua module written in C that allows a Lua process to create a _Java Virtual Machine_ and run Java code in that machine. In essence, the Java VM module supports bootstrapping JNLua from the Lua side.

#### Prerequisites

In order to use the Java VM module, the following prerequisites must be met:

* The JNLua Java VM module is called `javavm`. The module must be on the Lua C path, i.e. `package.cpath`. Pre-built versions for the Win32 platform (64-bit and 32-bit) are provided in the `native` assembly. Please see [Building the Native Library](BuildingTheNativeLibrary.md) for information on how to build the Java VM module yourself.
* The Java virtual machine library and its dependent libraries must be on the system library path. The exact steps required to ensure this depend on the platform and the Java virtual machine, for example:
  * Win32: Add `{JDK}\jre\bin` and `{JDK}\jre\bin\server` to `PATH`.
  * Linux: Add `{JDK}/jre/lib/{ARCH}` and `{JDK}/jre/lib/{ARCH}/server` to `LD_LIBRARY_PATH`.
* The JNLua Java library, i.e. `jnlua-{version}.jar`, must be installed.

#### Example

The following Lua code provides an example of using the Java VM module:

```
javavm = require("javavm")
javavm.create("-Djava.class.path=jnlua-1.0.2.jar")
System = java.require("java.lang.System")
System.out:println("Hello, world!")
javavm.destroy()
```

#### Advanced Example

For more complex scenarios, you can manage multiple JVM options:

```
javavm = require("javavm")

-- Create JVM with multiple options
options = {
    "-Djava.class.path=myapp.jar;jnlua-1.0.2.jar",
    "-Xmx512m",
    "-XX:+UseG1GC"
}
vm = javavm.create(unpack(options))

-- Use Java functionality
System = java.require("java.lang.System")
System.out:println("JVM created with options!")

-- Clean up
javavm.destroy()
```

#### Limitations

The JNI specification allows for only one Java virtual machine to be concurrently attached to a thread. Therefore, the Java VM module supports only one virtual machine at any point in time.

In practice, Java virtual machine implementations can be even more restrictive and allow only one Java virtual machine to be created for the lifetime of a process, i.e. even after the Java virtual machine has been destroyed, subsequent attempts of creating a new Java virtual machine fail.

#### Functions

The Java VM module provides the following functions.

##### `javavm.create`

Syntax:
```
vm = javavm.create({ option }) 
```

The function creates a Java virtual machine using the options provided and returns the virtual machine. In addition, the JNLua [Java Module](JavaModule.md) is loaded into the Lua state.

If there already is a virtual machine, the function raises an error.

Example:
```
javavm.create("-Djava.class.path=jnlua-1.0.1.jar")
```

##### `javavm.get`

Syntax:
```
vm = javavm.get()
```

The function returns the current virtual machine, or `nil` if there is none.

##### `javavm.destroy`

Syntax:
```
success = javavm.destroy()
```

The function destroys the current virtual machine and returns a boolean indicating whether the operation was successful. Calling the function when there is no current virtual machine returns `false`.

##### `javavm.attach`

Syntax:
```
attached_vm = javavm.attach()
```

Attaches to an existing Java Virtual Machine. This is useful when you need to interact with a JVM that was created by another component in your application.

##### `javavm.detach`

Syntax:
```
success = javavm.detach()
```

Detaches from the current Java Virtual Machine. This allows other parts of the application to manage the JVM lifecycle independently.

## Building from Source

JNLua uses a native library which integrates Lua with Java by means of the Java Native API (JNI). The native library is written in ANSI C and invokes the actual Lua implementation provided by a shared Lua library.

The optional Java VM module is built alongside the native library. It is a Lua module written in C that allows a Lua process to create a Java Virtual Machine and run Java code in that machine. In essence, the Java VM module supports bootstrapping JNLua from the Lua side.

The sources of the native library and the Java VM module are part of the `src` assembly that is downloadable from the JNLua web site. As an alternative, the sources can be checked out from the project repository. The assembly or checkout contains the following directories that are relevant for building the native library and Java VM module:

| **Directory** | **Description** |
|:--------------|:----------------|
| `jnlua-{version}/src/main/c` | C sources of the native library and the Java VM module|
| `jnlua-{version}/src/main/c/{platform}` | Makefiles for various platforms |

### Build Requirements

Building the JNLua native library and Java VM module requires a shared Lua library. For many platforms, you can download a shared Lua library from the [LuaBinaries project](http://luabinaries.sourceforge.net/) website. Many Linux distributions provide the shared Lua library as a package.

- JNLua 0.9 requires Lua 5.1
- JNLua 1.0 requires Lua 5.2
- JNLua 1.0.4 requires LuaJIT

### Platform Support

Currently, JNLua provides Makefiles for the Win32, Linux and MacOSX platforms. The build process is deliberately kept simple. It should therefore be relatively easy to adapt a Makefile for a platform that is not supported out-of-the-box from one of the existing Makefiles.

### Build Steps

#### Linux
```bash
cd src/main/c/Linux
make
```

#### Windows
```cmd
cd src/main/c\Win32
nmake
```
(in the Visual Studio Command Prompt)

#### macOS
```bash
cd src/main/c/MacOSX
make
```

#### Linux-aarch64
```bash
cd src/main/c/Linux-aarch64
./build.sh
```

#### Win32
```bash
cd src/main/c\Win32
.\build.sh
```

### Custom Builds

To build the native library and Java VM module for a platform, change into the appropriate directory and edit the Makefile to customize the paths for your environment. The paths typically define the location of the shared Lua library and headers on your system as well as the location of the Java Development Kit (JDK).

When the paths are set correctly, run **make** from the platform directory. On the Windows platform, open the _Visual Studio Command Prompt_ from the Start Menu and then run **nmake** from the platform directory.

### CMake Builds (Linux-aarch64)

For some platforms, JNLua also provides CMakeLists.txt files:

```bash
cd src/main/c/Linux-aarch64
cmake .
make
```

### Build Configuration

When building the native library, you may need to adjust the following settings in the Makefile:

- `LUA_DIR`: Path to the Lua installation directory
- `JAVA_HOME`: Path to the Java Development Kit (JDK)
- `LUA_LIB`: Name of the Lua library (e.g., `lua52`, `lua51`, `luajit`)
- Compiler flags specific to your platform

The native library build process creates both the core JNLua library and the optional Java VM module. Both components are essential for the complete JNLua functionality.

## Troubleshooting

### Common Issues

1. **UnsatisfiedLinkError**: This usually occurs when the native library cannot be found. Ensure the native library file is in a location where the Java Virtual Machine can find it.

2. **ClassNotFoundException**: This happens when the JNLua jar file is not in the classpath.

3. **Method dispatch ambivalence**: When calling overloaded Java methods from Lua, you may encounter ambivalence errors. Use `java.cast()` to resolve these issues.

4. **Memory issues**: Large Lua states or many Java objects referenced from Lua can cause memory issues. Remember to close Lua states when done and be aware of object lifecycle.

### Debugging Tips

- Enable verbose logging to track interactions between Java and Lua
- Use the JNLua console for interactive testing
- Check the Lua stack regularly to avoid stack overflow
- Monitor memory usage when processing large amounts of data

## Requirements

JNLua 0.9 is based on the Java 6 Platform (JDK 1.6) and Lua 5.1.

JNLua 1.0 is based on the Java 6 Platform (JDK 1.6) and Lua 5.2.

JNLua 1.0.4 requires LuaJIT

Max Raskin has done a port of JNLua for the [Android platform](https://github.com/airminer/jnlua-android).

## License

JNLua is licensed under the MIT license which at the time of this writing is the same license as the one of Lua.