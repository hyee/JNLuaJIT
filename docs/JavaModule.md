# Java Module

The Java module provides a small but comprehensive set of Lua functions providing Java language support for Lua. It is loaded automatically when JNLua is initialized.

## Available Functions

### java.require(typeName [, import])
Loads a Java type by name and returns the corresponding Java class proxy. If the import flag is true (defaults to false), the Java type is also imported to the global namespace under its unqualified name.

```lua
-- Load a class
System = java.require("java.lang.System")
currentTime = System:currentTimeMillis()

-- Load and import to namespace
java.require("java.lang.System", true)  -- Can now use java.lang.System directly
```

### java.new(type|typeName, {dimension})
Creates a new Java object or array. When a class is passed, creates a new instance using the default constructor. When a type name is passed, loads the class first. When dimensions are specified, creates a Java array.

```lua
-- Create new object
StringBuilder = java.require("java.lang.StringBuilder")
sb = java.new(StringBuilder)  -- equivalent to StringBuilder:new()

-- Create array
intArray = java.new("int", 10)  -- integer array of size 10
strArray = java.new("java.lang.String", 5)  -- string array of size 5
```

### java.instanceof(object, type|typeName)
Checks if a Java object is an instance of the given type.

```lua
ArrayList = java.require("java.util.ArrayList")
list = ArrayList:new()
if java.instanceof(list, "java.util.List") then
    print("It's a List")
end
```

### java.cast(value, type|typeName)
Casts a value to the specified type. This is particularly useful when resolving method overload ambiguities.

```lua
StringBuilder = java.require("java.lang.StringBuilder")
sb = StringBuilder:new()
-- Resolve overload ambiguity by casting
sb:append(java.cast(42, "int"))
```

### java.proxy(table, interface|interfaceName...)
Creates a Java proxy implementing the specified interface(s). The table provides the implementations for the interface methods.

```lua
-- Create a Runnable proxy
runnableTable = {
    run = function()
        print("Running from Java!")
    end
}
runnable = java.proxy(runnableTable, "java.lang.Runnable")
-- Now runnable can be used anywhere a Runnable is expected
```

### java.pairs(map)
Returns an iterator suitable for Lua's `pairs()` function to iterate over a Java Map.

```lua
HashMap = java.require("java.util.HashMap")
map = HashMap:new()
map:put("key1", "value1")
map:put("key2", "value2")

for k, v in java.pairs(map) do
    print(k, v)  -- Iterates over key-value pairs
end
```

### java.ipairs(array|list)
Returns an iterator suitable for Lua's `ipairs()` function to iterate over a Java array or List.

```lua
ArrayList = java.require("java.util.ArrayList")
list = ArrayList:new()
list:add("first")
list:add("second")

for i, v in java.ipairs(list) do
    print(i, v)  -- Iterates with 1-based indices
end
```

### java.totable(list|map)
Wraps a Java List or Map in a Lua table proxy that behaves like a regular Lua table but operates on the underlying Java collection.

```lua
ArrayList = java.require("java.util.ArrayList")
list = ArrayList:new()
wrapped = java.totable(list)
wrapped[1] = "first"  -- Adds to Java list
wrapped[2] = "second"
print(wrapped[1])  -- Gets from Java list
```

### java.tolua(list|map|array)
Converts a Java List, Map, or Array to a native Lua table with copies of the data.

```lua
ArrayList = java.require("java.util.ArrayList")
list = ArrayList:new()
list:add("one")
list:add("two")
luaTable = java.tolua(list)  -- Creates a native Lua table
```

## Additional Functions Available on Java Objects

When working with Java objects in Lua, additional utility functions are available directly on the Java objects:

### java_class_name
Returns the Java class name of the object.

```lua
obj = java.new("java.lang.Object")
print(obj.java_class_name)  -- prints "java.lang.Object"
```

### java_fields(obj)
Returns a `pairs` function for iterating over the object's fields, equivalent to `java.fields(obj)`.

```lua
obj = java.new("java.lang.String", "test")
for fieldName, fieldValue in obj.java_fields(obj) do
    print(fieldName, fieldValue)
end
```

### java_methods(obj)
Returns a `pairs` function for iterating over the object's methods, equivalent to `java.methods(obj)`.

### java_properties(obj)
Returns a `pairs` function for iterating over the object's properties, equivalent to `java.properties(obj)`.

### to_table(object)
Provides access to Java list/map functionality, same as `java.totable(obj)`.

### to_lua(object)
Converts Java list/map/array into native Lua table, same as `java.tolua(obj)`.

### JNI_GC(obj)
Calls the `__gc` function for cleanup purposes.

## Advanced Usage

### Working with Static Members
```lua
System = java.require("java.lang.System")
currentTime = System:currentTimeMillis()  -- Calling static method
out = System.out  -- Accessing static field
out:println("Hello from Java!")
```

### Constructor Invocation
```lua
ArrayList = java.require("java.util.ArrayList")
list = ArrayList:new()  -- Calling constructor
-- Or alternatively:
list = java.new(ArrayList)
```

### Property Access
```lua
thread = java.new("java.lang.Thread"):currentThread()
name = thread.name  -- Calls getName() method
thread.priority = 10  -- Calls setPriority(10)
```

### Method Overloading Resolution
```lua
-- When methods are overloaded, use java.cast to specify types
PrintStream = java.require("java.io.PrintStream")
out = System.out
out:print(java.cast(42, "int"))  -- Specifically call print(int)
out:print(java.cast(42.0, "double"))  -- Specifically call print(double)
```
