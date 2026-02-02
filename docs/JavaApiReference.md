# Java API Reference

## LuaState Class

`LuaState` is the core class of JNLua, representing a Lua instance. It corresponds to the `lua_State` pointer in the underlying Lua C API, providing access to the entire Lua virtual machine.

The interface of the Lua state class corresponds largely to the underlying Lua C API which is documented in the [Lua Reference Manual](http://www.lua.org/manual/5.1). Method names have been adjusted to Java camel case and the `lua_` and `luaL_` prefixes haven been omitted since this information is implied in the Java package name. Also, the Java API takes advantage of method overloading and uses a single method name for C API functions that primarily differ in their number arguments. For example, the Java API provides `rawGet(int)` and `rawGet(int, int)` whereas the C API provides `lua_rawget()` and `lua_rawgeti()`.

A JNLua Lua state subsumes the Lua main thread and all additional Lua threads created by means of the coroutine API. Therefore, there is no separate Java Lua state instance for each Lua thread. Instead, Lua threads are treated as Lua values and referenced by stack index in the coroutine API methods. The coroutine API methods have been slightly adapted from their C behavior to that end. Functionality-wise, they pretty much correspond to the functions in the Lua coroutine module.

You should free Lua states that are no longer in use by explicitly invoking the `LuaState.close()` method. Otherwise, resources on the native side are not freed until the Lua state (or more precisely, the _finalize guardian_ of the Lua state) is collected by the Java garbage collector.

### Creation and Destruction
```java
// Create a new Lua state
LuaState luaState = new LuaState();

// Close and release resources
luaState.close();
```

### Basic Operations
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

### Java Function Implementation
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

### Proxy Object Creation
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

### Implementing Modules in Java
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

## JavaModule Class

The Java module provides a set of Lua functions that enable Lua code to access Java functionality.

### Provided Functions
- `java.require(typeName [, import])` - Loads a Java type
- `java.new(type|typeName, {dimension})` - Creates a Java object or array
- `java.instanceof(object, type|typeName)` - Checks type instance
- `java.cast(value, type|typeName)` - Type conversion
- `java.proxy(table, interface|interfaceName...)` - Creates interface proxy
- `java.pairs(map)` - Map iterator
- `java.ipairs(array|list)` - Array/List iterator
- `java.totable(list|map)` - Wraps as Lua table
- `java.tolua(list|map|array)` - Converts to native Lua table

## Threading and Synchronization

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

## Error Handling

### JNLua Exception Hierarchy
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

### Exception Descriptions
- `LuaRuntimeException`: Lua runtime error, such as calling a non-existent function
- `LuaSyntaxException`: Lua code syntax error
- `LuaMemoryAllocationException`: Out of memory error
- `LuaGcMetamethodException`: Metamethod error during garbage collection
- `LuaMessageHandlerException`: Message handler error

All exceptions are derived from `LuaException`.

Note that all Lua exceptions are _unchecked_ exceptions. It is recommended that clients use a try...catch clause to catch `LuaException` and its subclasses.

The Lua runtime exception class provides the method `LuaRuntimeException.getLuaStackTrace()` which returns the Lua stack trace of the error. Additional methods of the class allow printing the Lua stack trace to various outputs.

### Best Practices
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