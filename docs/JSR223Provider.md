JNLua includes a provider for [JSR 223: Scripting for the Java Platform](http://www.jcp.org/en/jsr/detail?id=223). JSR 223 allows the use of scripting languages on Java in a generic way. JNLua also supports the optional JSR 223 interfaces Compilable and Invocable.

# Registration Attributes #

JNLua is registered with the JSR 223 script engine manager using the following attributes:

| **Attribute** | **Value** |
|:--------------|:----------|
| Engine Name | JNLua |
| Engine Version | `LuaState.VERSION` |
| Language Name | Lua |
| Language Version | `LuaState.LUA_VERSION` |
| Names | lua, Lua, jnlua, JNLua |
| MIME Types | application/x-lua, text/x-lua |
| Extensions | lua |

# Bindings #

JSR 223 uses the term _bindings_ to describe the concept of making objects in the Java host environment accessible in the script engine (and vice versa.) In that context, JSR 223 further distinguishes a _global scope_ and an _engine scope_. The global scope is on a per script engine manager basis and global bindings are typically shared across all script engines created by a script engine manager; the engine scope is on a per script engine basis and engine bindings are typically unique per script engine.

The JNLua JSR 223 provider supports specialized bindings for the engine scope. These bindings are tied directly to the Lua script engine and constitute a map view on the global variables of the underlying Lua state. This means that changes in the global variables of the Lua state immediately show in the engine scope bindings and vice versa. To comply with the JSR 223 API, the map view contains only those entries from the global environment which have non-empty string keys. (Non-empty string keys are the norm for the Lua global environment. However, Lua does not prevent you from using other types of keys.)

The JNLUa JSR 223 provider accepts any binding that complies to the specification. For bindings other than its specialized engine scope bindings, it simply copies the bound objects into the global environment of the Lua state before evaluating scripts.

# Example #

The full description of JSR 223 is beyond the scope of the JNLua documentation. See the JSR 223 specification and related documentation for more information.

The following code shows a simple demo application using JNLua by means of JSR 223. Note that the import list makes no direct reference to JNLua.

```
import javax.script.Compilable; 
import javax.script.CompiledScript; 
import javax.script.Invocable; 
import javax.script.ScriptEngine; 
import javax.script.ScriptEngineManager; 
import javax.script.ScriptException; 

public class Simple { 
	public static void main(String[] args) { 
		// Acquire a script engine manager 
		ScriptEngineManager manager = new ScriptEngineManager(); 

		// Get a script engine by name 
		ScriptEngine engine = manager.getEngineByName("Lua"); 

		// Populate the engine with some objects 
		String name = "Lua"; 
		StringBuilder sb = new StringBuilder(); 
		engine.put("name", name); 
		engine.put("sb", sb); 

		// Evaluate a script 
		try { 
			Object nameFromEngine = engine.eval("return name"); 
			System.out.println("The name of the language is " + nameFromEngine); 
		} catch (ScriptException e) { 
			e.printStackTrace(); 
		} 

		// Evaluate a script generated via the factory 
		try { 
			String methodCall = engine.getFactory().getMethodCallSyntax("sb", "append", "name"); 
			engine.eval(methodCall); // evaluates 'sb:append(name)' 
		} catch (ScriptException e) { 
			e.printStackTrace(); 
		} 
		System.out.println("The string builder says " + sb.toString()); 

		// Pre-compile a script and evaluate it repeatedly 
		Compilable compilable = (Compilable) engine; 
		try { 
			CompiledScript compiledScript = compilable.compile("return name"); 
			for (int i = 0; i < 3; i++) { 
				Object nameFromEngine = compiledScript.eval(); 
				System.out.println("The name of the language is still " + nameFromEngine); 
			} 
		} catch (ScriptException e) { 
			e.printStackTrace(); 
		} 

		// Invoke a function 
		Invocable invocable = (Invocable) engine; 
		try { 
			invocable.invokeFunction("print", "Lua is Lua"); 
		} catch (ScriptException e) { 
			e.printStackTrace(); 
		} catch (NoSuchMethodException e) { 
			e.printStackTrace(); 
		} 

		// Let Lua implement an interface... 
		try { 
			engine.eval("runnable = { run = function () print(\"This is Lua\") end }"); 
			Object luaRunnable = engine.get("runnable"); 
			Runnable runnable = invocable.getInterface(luaRunnable, Runnable.class); 

			// ... and run it from another thread 
			Thread thread = new Thread(runnable); 
			thread.start(); 
			thread.join(); 
		} catch (ScriptException e) { 
			e.printStackTrace(); 
		} catch (InterruptedException e) { 
			e.printStackTrace(); 
		} 
	} 
} 
```