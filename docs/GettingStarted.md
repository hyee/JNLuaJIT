# Getting Started with JNLua

This guide will help you get started with JNLua quickly.

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

## Using Lua from Java

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

## Using Java from Lua

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

## Using JSR-223

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

## More Advanced Examples

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

### Error Handling

Error handling works like normal in Lua. The notable difference is that error values originating from Java are not strings but rather error objects. The error message is accessible by means of the Lua `tostring()` function.

The following example illustrates error handling:

```
succeeded, error = pcall(function () java.require("undefined") end) 
assert(not succeeded) 
print(tostring(error)) -- must invoke tostring() to extract message 
```