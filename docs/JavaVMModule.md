# Java VM Module

The JNLua Java VM module is a Lua module written in C that allows a Lua process to create a _Java Virtual Machine_ and run Java code in that machine. In essence, the Java VM module supports bootstrapping JNLua from the Lua side.

The Java VM module is a Lua module written in C that allows a Lua process to create a Java Virtual Machine and run Java code in that machine. For better structure, the documentation of the Java VM module is on this separate page.

## Prerequisites

In order to use the Java VM module, the following prerequisites must be met:

* The JNLua Java VM module is called `javavm`. The module must be on the Lua C path, i.e. `package.cpath`. Pre-built versions for the Win32 platform (64-bit and 32-bit) are provided in the `native` assembly. Please see [Building the Native Library](BuildingTheNativeLibrary.md) for information on how to build the Java VM module yourself.
* The Java virtual machine library and its dependent libraries must be on the system library path. The exact steps required to ensure this depend on the platform and the Java virtual machine, for example:
  * Win32: Add `{JDK}\jre\bin` and `{JDK}\jre\bin\server` to `PATH`.
  * Linux: Add `{JDK}/jre/lib/{ARCH}` and `{JDK}/jre/lib/{ARCH}/server` to `LD_LIBRARY_PATH`.
* The JNLua Java library, i.e. `jnlua-{version}.jar`, must be installed.

## Example

The following Lua code provides an example of using the Java VM module:

```
javavm = require("javavm")
javavm.create("-Djava.class.path=jnlua-1.0.2.jar")
System = java.require("java.lang.System")
System.out:println("Hello, world!")
javavm.destroy()
```

## Advanced Example

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

## Limitations

The JNI specification allows for only one Java virtual machine to be concurrently attached to a thread. Therefore, the Java VM module supports only one virtual machine at any point in time.

In practice, Java virtual machine implementations can be even more restrictive and allow only one Java virtual machine to be created for the lifetime of a process, i.e. even after the Java virtual machine has been destroyed, subsequent attempts of creating a new Java virtual machine fail.

## Functions

The Java VM module provides the following functions.

### `javavm.create`

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

### `javavm.get`

Syntax:
```
vm = javavm.get()
```

The function returns the current virtual machine, or `nil` if there is none.

### `javavm.destroy`

Syntax:
```
success = javavm.destroy()
```

The function destroys the current virtual machine and returns a boolean indicating whether the operation was successful. Calling the function when there is no current virtual machine returns `false`.

### `javavm.attach`

Syntax:
```
attached_vm = javavm.attach()
```

Attaches to an existing Java Virtual Machine. This is useful when you need to interact with a JVM that was created by another component in your application.

### `javavm.detach`

Syntax:
```
success = javavm.detach()
```

Detaches from the current Java Virtual Machine. This allows other parts of the application to manage the JVM lifecycle independently.