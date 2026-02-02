# JNLua Console

JNLua includes a simple Lua console for experimenting with JNLua. The console is provided by the `LuaConsole` class.

The console collects input until a line with the sole content of the word **go** is encountered. At that point, the collected input is run as a Lua chunk. If the Lua chunk loads and runs successfully, the console displays the returned values of the chunk as well as the execution time based on a `System.nanoTime()` measurement. Otherwise, the console shows the error that has occurred.

Expressions can be printed by prepending **=** to the expression at the beginning of a chunk. The console translates **=** into **return** followed by a space and executes the chunk immediately. No separate **go** is required. Therefore, expressions printed this way must be entered on a single line.

## Running the Console

To start the JNLua console, execute the following command:

```bash
java -cp jnlua-1.0.4.jar com.naef.jnlua.console.LuaConsole
```

## Console Features

### Multi-line Input
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

### Expression Evaluation
Quick evaluation of expressions using the `=` prefix:

```
> = 2 + 2
4
> = java.require("java.lang.System"):currentTimeMillis()
1627834567890
```

### Java Integration
The console comes with Java integration enabled by default, allowing direct access to Java classes:

```
> System = java.require("java.lang.System")
> = System:currentTimeMillis()
1627834567890
> out = System.out
> out:println("Hello from Java!")
Hello from Java!
```

### Execution Timing
When using the `go` command, the console shows execution time:

```
> for i=1,1000000 do end
> go
(nil) [executed in 15 ms]
```