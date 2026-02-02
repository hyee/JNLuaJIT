# JSR 223 Support

JNLua fully supports the JSR 223: Scripting for the Java Platform specification.

## Registration Attributes

| Attribute | Value |
|---------|-------|
| Engine Name | JNLua |
| Engine Version | `LuaState.VERSION` |
| Language Name | Lua |
| Language Version | `LuaState.LUA_VERSION` |
| Names | lua, Lua, jnlua, JNLua |
| MIME Types | application/x-lua, text/x-lua |
| Extensions | lua |

## Basic Usage

```java
import javax.script.*;

ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("Lua");

// Execute script
engine.eval("print('Hello from Lua')");

// Set binding
engine.put("name", "JNLua");
Object result = engine.eval("return 'Hello, ' .. name");

// Compile script in advance
Compilable compilable = (Compilable) engine;
CompiledScript compiled = compilable.compile("return name");
Object result2 = compiled.eval();
```

## Method Call Syntax

```java
// Generate method call syntax
String methodCall = engine.getFactory().getMethodCallSyntax("sb", "append", "name");
engine.eval(methodCall);  // Actually executes sb:append(name)
```

## Function Calls

```java
// Call Lua function
Invocable invocable = (Invocable) engine;
invocable.invokeFunction("print", "Hello from Java");
```

## Interface Implementation

```java
// Implement Java interface from Lua
engine.eval("runnable = { run = function() print('Running from Lua') end }");
Object luaRunnable = engine.get("runnable");
Runnable runnable = invocable.getInterface(luaRunnable, Runnable.class);
new Thread(runnable).start();
```

## Binding Scopes

JSR 223 uses the concept of _bindings_ to describe making objects accessible between the host environment (Java) and the script engine (Lua). In this context, JSR 223 further distinguishes between _global scope_ and _engine scope_:

- Global scope: Based on the script engine manager, global bindings are typically shared across all script engines created by the script engine manager
- Engine scope: Based on each script engine, engine bindings are typically unique to each script engine

The JNLua JSR 223 provider supports dedicated bindings for the engine scope. These bindings are directly tied to the Lua script engine and constitute a map view of the global variables of the underlying Lua state. This means that changes in the global variables of the Lua state immediately appear in the engine scope bindings and vice versa.