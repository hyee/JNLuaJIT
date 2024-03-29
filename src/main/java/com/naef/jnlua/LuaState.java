/*
 * $Id: LuaState.java 156 2012-10-05 22:57:25Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.naef.jnlua.JavaReflector.Metamethod;

import java.io.*;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.TRANSIENT;

/**
 * JNLua core class representing a Lua instance.
 * <p/>
 * <p>
 * The class performs extensive checking on all arguments and its state.
 * Specifically, the following exceptions are thrown under the indicated
 * conditions:
 * </p>
 * <p/>
 * <table class="doc">
 * <tr>
 * <th>Exception</th>
 * <th>When</th>
 * </tr>
 * <tr>
 * <td>{@link java.lang.NullPointerException}</td>
 * <td>if an argument is <code>null</code> and the API does not explicitly
 * specify that the argument may be <code>null</code></td>
 * </tr>
 * <tr>
 * <td>{@link java.lang.IllegalStateException}</td>
 * <td>if the Lua state is closed and the API does not explicitly specify that
 * the method may be invoked on a closed Lua state</td>
 * </tr>
 * <tr>
 * <td>{@link java.lang.IllegalArgumentException}</td>
 * <td>if a stack index refers to an undefined stack location and the API does
 * not explicitly specify that the stack index may be undefined</td>
 * </tr>
 * <tr>
 * <td>{@link java.lang.IllegalArgumentException}</td>
 * <td>if a stack index refers to an stack location with value type that is
 * different from the value type explicitly specified in the API</td>
 * </tr>
 * <tr>
 * <td>{@link java.lang.IllegalArgumentException}</td>
 * <td>if a count is negative or out of range and the API does not explicitly
 * specify that the count may be negative or out of range</td>
 * </tr>
 * <tr>
 * <td>{@link com.naef.jnlua.LuaRuntimeException}</td>
 * <td>if a Lua runtime error occurs</td>
 * </tr>
 * <tr>
 * <td>{@link com.naef.jnlua.LuaSyntaxException}</td>
 * <td>if a the syntax of a Lua chunk is incorrect</td>
 * </tr>
 * <tr>
 * <td>{@link com.naef.jnlua.LuaMemoryAllocationException}</td>
 * <td>if the Lua memory allocator runs out of memory or if a JNI allocation
 * fails</td>
 * </tr>
 * </table>
 */
public class LuaState {
    public final static Charset UTF8 = StandardCharsets.UTF_8;
    volatile static LuaState mainLuaState = null;
    volatile static LuaState ownLuaState = null;
    // -- Static
    /**
     * Registry pseudo-index.
     */
    public static final int REGISTRYINDEX;
    /**
     * Environment pseudo-index.
     */
    public static final int ENVIRONINDEX = -10001;
    public static final String JNLUA_JAVAFUNC = "jnlua.JavaFunction";
    public static final String JNLUA_OBJECT = "jnlua.Object";
    /**
     * Globals pseudo-index.
     */
    public static final int GLOBALSINDEX = -10002;
    /**
     * Multiple returns pseudo return value count.
     */
    public static final int MULTRET = -1;
    /**
     * Status indicating that a thread is suspended.
     */
    public static final int YIELD = 1;
    /**
     * The JNLua version. The format is &lt;major&gt;.&lt;minor&gt;.
     */
    public static final String VERSION = "0.9";
    /**
     * The Lua version. The format is &lt;major&gt;.&lt;minor&gt;.
     */
    public static final String LUA_VERSION;
    /**
     * The API version.
     */
    private static final int APIVERSION = 2;
    public static final int RIDX_MAINTHREAD = 1;
    public static final int RIDX_GLOBALS = 2;
    /**
     * The yield flag. This field is modified from both the JNI side and Java
     * side and signals a pending yield.
     */
    protected boolean yield;
    protected static int trace;
    final int top;

    static {
        NativeSupport.getInstance().getLoader().load();
        REGISTRYINDEX = -10000;
        LUA_VERSION = lua_version();
    }

    public int trace(int trace) {
        if (trace > -1 && LuaState.trace != trace) {
            LuaState.trace = trace;
            lua_trace(trace);
            ClassAccess.IS_DEBUG = trace > 0 && ((trace & 8) == 0);
            System.out.println("Trace level set to " + trace);
        }
        return LuaState.trace;
    }

    public final static Class<?> toClass(final Object object) {
        if (object == null) return null;
        if (object instanceof TypedJavaObject) return ((TypedJavaObject) object).getObject().getClass();
        return object instanceof Class<?> ? (Class<?>) object : object.getClass();
    }

    public final static String toClassName(final Object object) {
        final Class clz = toClass(object);
        if (clz == null) return null;
        String clzName = clz.getCanonicalName();
        if (clzName == null) clzName = clz.getName();
        return clzName;
    }

    // -- State
    /**
     * Whether the <code>lua_State</code> on the JNI side is owned by the Java
     * state and must be closed when the Java state closes.
     */
    private final boolean ownState;
    /**
     * The <code>lua_State</code> pointer on the JNI side. <code>0</code>
     * implies that this Lua state is closed. The field is modified exclusively
     * on the JNI side and must not be touched on the Java side.
     */
    protected long luaState;
    /**
     * The <code>lua_State</code> pointer on the JNI side for the running
     * coroutine. This field is modified exclusively on the JNI side and must
     * not be touched on the Java side.
     */
    protected long luaThread;
    protected final int JNLUA_OBJECTS;
    /**
     * The maximum amount of memory the may be used by the Lua state, in bytes.
     * This can be adjusted to limit the amount of memory a state may use. If
     * it is reduced while a VM is active this can very quickly lead to out of
     * memory errors.
     */
    private int luaMemoryTotal;
    /**
     * The amount of memory currently used by the Lua state, in bytes. This is
     * set from the JNI side and must not be modified from the Java side.
     */
    private int luaMemoryUsed;
    /**
     * Ensures proper finalization of this Lua state.
     */
    private final Object finalizeGuardian;
    /**
     * The class loader for dynamically loading classes.
     */
    private ClassLoader classLoader;
    /**
     * Reflects Java objects.
     */
    private JavaReflector javaReflector;
    /**
     * Converts between Lua types and Java types.
     */
    protected Converter converter;
    private final HashMap<String, JavaFunction> javaFunctions;
    /**
     * Set of Lua proxy phantom references for pre-mortem cleanup.
     */
    private final Set<LuaValueProxyRef> proxySet = new HashSet<>();
    /**
     * Reference queue for pre-mortem cleanup.
     */
    private final ReferenceQueue<LuaValueProxyImpl> proxyQueue = new ReferenceQueue<>();

    // -- Construction

    /**
     * Creates a new instance. The class loader of this Lua state is set to the
     * context class loader of the calling thread. The Java reflector and the
     * converter are initialized with the default implementations. The Lua
     * state may allocate as much memory as it wants.
     *
     * @see #getClassLoader()
     * @see #setClassLoader(ClassLoader)
     * @see #getJavaReflector()
     * @see #setJavaReflector(JavaReflector)
     * @see #getConverter()
     * @see #setConverter(Converter)
     */
    public LuaState() {
        this(0L, 0);
    }

    public LuaState(long luaState) {
        this(luaState, 0);
    }

    /**
     * Creates a new instance. The class loader of this Lua state is set to the
     * context class loader of the calling thread. The Java reflector and the
     * converter are initialized with the default implementations. The Lua
     * state may allocate only as much memory as specified. This is enforced
     * by a custom allocator that is only used if a maximum memory is given.
     *
     * @param memory the maximum amount of memory this Lua state may use, in bytes
     * @see #getClassLoader()
     * @see #setClassLoader(ClassLoader)
     * @see #getJavaReflector()
     * @see #setJavaReflector(JavaReflector)
     * @see #getConverter()
     * @see #setConverter(Converter)
     */
    public LuaState(int memory) {
        this(0L, validateMemory(memory));
    }

    /**
     * Creates a new instance.
     */
    private LuaState(long luaState, int memory) {
        ownState = luaState == 0L;
        luaMemoryTotal = memory;
        lua_trace(trace);

        JNLUA_OBJECTS = lua_newstate(APIVERSION, luaState);

        check();
        // Create a finalize guardian
        finalizeGuardian = new Object() {
            @Override
            public void finalize() {
                closeInternal();
            }
        };
        // Set fields
        classLoader = Thread.currentThread().getContextClassLoader();
        javaReflector = JavaReflector.getInstance();
        converter = Converter.getInstance();

        // Add metamethods
        int len = JavaReflector.Metamethod.values().length;
        javaFunctions = new HashMap<>();
        for (int i = 0; i < len; i++) {
            final JavaReflector.Metamethod metamethod = JavaReflector.Metamethod.values()[i];
            final JavaFunction func = new JavaFunction() {
                final String metaMethodName = metamethod.getMetamethodName();
                final JavaFunction func = javaReflector.getMetamethod(metamethod);
                private String className = null;

                @Override
                public void call(LuaState luaState, Object[] args) {
                    className = toClassName(args[0]);
                    setName(className, ".", metaMethodName);
                    if (!(args[0] instanceof JavaReflector)) {
                        Invoker invoker = Invoker.getInvoker(args);
                        if (invoker != null) {
                            //System.out.println(invoker.name+":"+metaMethodName);
                            if (metaMethodName.equals("__newindex")) invoker.write(luaState, args);
                            else if (metaMethodName.equals("__index")) invoker.read(luaState, args);
                            else invoker.invoke(luaState);
                            return;
                        }
                        func.call(luaState, args);
                    } else {
                        //System.out.println(args[0]+":"+metaMethodName);
                        final JavaFunction function = getMetamethod(args[0], metamethod);
                        if (function == null) throw new UnsupportedOperationException(metaMethodName);
                        function.call(luaState, args);
                    }
                }

                @Override
                public String getName() {
                    return metaMethodName;
                }
            };
            javaFunctions.put(metamethod.getMetamethodName(), func);
            pushJavaObjectRaw(func);
            lua_setfield(luaThread, -2, metamethod.getMetamethodName().getBytes(UTF8));
        }

        lua_pop(luaThread, 1);
        top = lua_gettop(luaThread);
        if (ownState) openLibs();
        register(new JavaFunction() { //{ Class/Instance,methodName,args}
            public final void call(LuaState luaState, final Object[] args) {
                Invoker invoker = Invoker.getInvoker(args);
                if (invoker == null) invoker = Invoker.get(toClass(args[0]), (String) args[1], "");
                invoker.call(luaState, args);
            }

            public final String getName() {
                return "invoke";
            }
        });
        if (!ownState && mainLuaState == null) mainLuaState = this;
        else ownLuaState = this;
        lua_newstate_done(luaThread);
        pairInit();
    }

    public static LuaState getMainLuaState() {
        return mainLuaState;
    }

    //cl
    public final void cleanup() {
        LuaValueProxyRef luaValueProxyRef;
        while ((luaValueProxyRef = (LuaValueProxyRef) proxyQueue.poll()) != null) {
            proxySet.remove(luaValueProxyRef);
            if ((trace & 5) == 1) {
                println("[JVM] GC: lua_unref(" + luaValueProxyRef.getReference() + ")");
            }
            lua_unref(luaThread, REGISTRYINDEX, luaValueProxyRef.getReference());
        }
    }

    // -- Properties

    /**
     * Validates a value specified as the new maximum allowed memory use. This
     * is used in particular to validate values passed to the constructor.
     *
     * @param value the value to validate
     * @return the value itself
     */
    private static int validateMemory(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Maximum memory must be larger than zero.");
        }
        return value;
    }

    // -- Native methods
    final private static native String lua_version();

    /**
     * Returns the class loader of this Lua state. The class loader is used for
     * dynamically loading classes.
     * <p/>
     * <p>
     * The method may be invoked on a closed Lua state.
     * </p>
     *
     * @return the class loader
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Sets the class loader of this Lua state. The class loader is used for
     * dynamically loading classes.
     * <p/>
     * <p>
     * The method may be invoked on a closed Lua state.
     * </p>
     *
     * @param classLoader the class loader to set
     */
    public void setClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new NullPointerException();
        }
        this.classLoader = classLoader;
    }

    /**
     * Returns the Java reflector of this Lua state.
     * <p/>
     * <p>
     * The method may be invoked on a closed Lua state.
     * </p>
     *
     * @return the Java reflector converter
     */
    public JavaReflector getJavaReflector() {
        return javaReflector;
    }

    /**
     * Sets the Java reflector of this Lua state.
     * <p/>
     * <p>
     * The method may be invoked on a closed Lua state.
     * </p>
     *
     * @param javaReflector the Java reflector
     */
    public void setJavaReflector(JavaReflector javaReflector) {
        if (javaReflector == null) {
            throw new NullPointerException();
        }
        this.javaReflector = javaReflector;
    }

    /**
     * Returns a metamethod for a specified object. If the object implements the
     * {@link JavaReflector} interface, the metamethod is first
     * queried from the object. If the object provides the requested metamethod,
     * that metamethod is returned. Otherwise, the method returns the metamethod
     * provided by the Java reflector configured in this Lua state.
     * <p/>
     * <p>
     * Clients requiring access to metamethods should go by this method to
     * ensure consistent class-by-class overriding of the Java reflector.
     * </p>
     *
     * @param obj the object, or <code>null</code>
     * @return the Java reflector
     */
    public JavaFunction getMetamethod(Object obj, Metamethod metamethod) {
        if (obj != null && obj instanceof JavaReflector) {
            JavaFunction javaFunction = ((JavaReflector) obj).getMetamethod(metamethod);
            if (javaFunction != null) {

                return javaFunction;
            }
        }
        return javaReflector.getMetamethod(metamethod);
    }

    /**
     * Returns the converter of this Lua state.
     * <p/>
     * <p>
     * The method may be invoked on a closed Lua state.
     * </p>
     *
     * @return the converter
     */
    public Converter getConverter() {
        return converter;
    }

    // -- Memory

    /**
     * Sets the converter of this Lua state.
     * <p/>
     * <p>
     * The method may be invoked on a closed Lua state.
     * </p>
     *
     * @param converter the converter
     */
    public void setConverter(Converter converter) {
        if (converter == null) {
            throw new NullPointerException();
        }
        this.converter = converter;
    }

    protected final void setExecThread(final long thread) {
        if (luaThread != thread)
            luaThread += thread - luaThread;
    }

    public static void println(String message) {
        System.out.println(message);
        System.out.flush();
    }

    public static void printCallStack(String top) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        for (int i = 2; i < elements.length; i++) {
            StackTraceElement s = elements[i];
            System.out.println("\tat " + s.getClassName() + "." + s.getMethodName() + "(" + s.getFileName() + ":" + s.getLineNumber() + ")");
        }
    }

    public static byte[] getCanonicalName(final Object obj) {
        final String name = toClassName(obj);
        return name == null ? null : name.getBytes(UTF8);
    }

    /**
     * Returns whether this Lua state is open.
     * <p/>
     * <p>
     * The method may be invoked on a closed Lua state.
     * </p>
     *
     * @return whether this Lua state is open
     */
    public final boolean isOpen() {
        return isOpenInternal();
    }

    /**
     * Returns the maximum memory consumption of this Lua state. This is the
     * maximum raw memory Lua may allocate for this state, in bytes.
     *
     * @return the maximum memory consumption
     */
    public int getTotalMemory() {
        return luaMemoryTotal;
    }

    // -- Life cycle

    /**
     * Sets the maximum amount of memory this Lua state may allocate. This is
     * the size of the raw memory the Lua library may allocate for this tate,
     * in bytes. Note that you can only set the maximum memory consumption for
     * states that were created to enforce a maximum memory consumption.
     *
     * @param value the new maximum memory size this state may allocate
     */
    public void setTotalMemory(int value) {
        if (luaMemoryTotal < 1) {
            throw new IllegalStateException("cannot set maximum memory for this state");
        }
        luaMemoryTotal = validateMemory(value);
    }

    /**
     * Returns the current amount of unused memory by this Lua state. This is
     * the size of the total available memory minus the raw memory currently
     * allocated by this state, in bytes.
     * <p/>
     * This is guaranteed to be less or equal to {@link #getTotalMemory()} and
     * larger or equal to zero.
     * <p/>
     * This only returns something not zero if a maximum memory consumption is
     * enforced by this state. Otherwise it will always return zero.
     *
     * @return the current memory consumption
     */
    public int getFreeMemory() {
        // This is the reason we use free amount instead of used amount: if we
        // lower the max memory we can get below used memory, which would be
        // weird; so we just say free memory is zero, which is more intuitive
        // and true at the same time.
        return Math.max(0, luaMemoryTotal - luaMemoryUsed);
    }

    // -- Registration

    /**
     * Closes this Lua state and releases all resources.
     * <p/>
     * <p>
     * The method may be invoked on a closed Lua state and has no effect in that
     * case.
     * </p>
     */
    public void close() {
        closeInternal();
    }

    /**
     * Performs a garbage collection operation.
     *
     * @param what the operation to perform
     * @param data the argument required by some operations (see Lua Reference
     *             Manual)
     * @return a return value depending on the GC operation performed (see Lua
     * Reference Manual)
     */
    public int gc(GcAction what, int data) {
        check();
        return lua_gc(luaThread, what.ordinal(), data);
    }

    /**
     * Opens the specified library in this Lua state.
     *
     * @param library the library
     */
    public void openLib(Library library) {
        check();
        library.open(this);
    }

    /**
     * Opens the Lua standard libraries and the JNLua Java module in this Lua
     * state.
     * <p/>
     * <p>
     * The method opens all libraries defined by the {@link Library}
     * enumeration.
     * </p>
     */
    public void openLibs() {
        check();
        for (Library library : Library.values()) {
            library.open(this);
        }
    }

    // -- Load and dump

    /**
     * Registers a named Java function as a global variable.
     */
    public void register(String moduleName, JavaFunction[] javaFunctions, boolean global) {
        check();
        /*
         * The following code corresponds to luaL_requiref() and must be kept in
         * sync. The original code cannot be called due to the necessity of
         * pushing each C function with an individual closure.
         */
        newTable(0, javaFunctions.length);
        for (int i = 0; i < javaFunctions.length; i++) {
            String name = javaFunctions[i].getName();
            checkArg(name != null, "anonymous function at index %d", i);
            pushJavaObjectRaw(javaFunctions[i]);
            setField(-2, name);
        }
        lua_findtable(luaThread, REGISTRYINDEX, "_LOADED", javaFunctions.length);
        pushValue(-2);
        setField(-2, moduleName);
        pop(1);
        if (global) {
            rawGet(REGISTRYINDEX, RIDX_GLOBALS);
            pushValue(-2);
            setField(-2, moduleName);
            pop(1);
        }
    }

    public void pushGlobal(String name, Object value) {
        pushJavaObject(value);
        setGlobal(name);
    }

    /**
     * Registers a named Java function as a global variable.
     *
     * @param javaFunction the Java function to register
     */
    public synchronized void register(JavaFunction javaFunction) {
        String name = javaFunction.getName();
        if (name == null) {
            throw new IllegalArgumentException("anonymous function");
        }
        pushJavaObjectRaw(javaFunction);
        setGlobal(name);
    }

    /**
     * Registers a module and pushes the module on the stack. The module name is
     * allowed to contain dots to define module hierarchies.
     *
     * @param moduleName    the module name
     * @param javaFunctions the Java functions of the module
     */
    public void register(String moduleName, JavaFunction[] javaFunctions) {
        check();
        /*
         * The following code corresponds to luaL_openlib() and must be kept in
         * sync. The original code cannot be called due to the necessity of
         * pushing each C function with an individual closure.
         */

        lua_findtable(luaThread, REGISTRYINDEX, "_LOADED", 1);
        getField(-1, moduleName);
        if (!isTable(-1)) {
            pop(1);
            String conflict = lua_findtable(luaThread, GLOBALSINDEX, moduleName, javaFunctions.length);
            if (conflict != null) {
                throw new IllegalArgumentException(String.format("naming conflict for module name '%s' at '%s'", moduleName, conflict));
            }
            pushValue(-1);
            setField(-3, moduleName);
        }
        remove(-2);
        for (int i = 0; i < javaFunctions.length; i++) {
            String name = javaFunctions[i].getName();
            if (name == null) {
                throw new IllegalArgumentException(String.format("anonymous function at index %d", i));
            }
            pushJavaObjectRaw(javaFunctions[i]);
            setField(-2, name);
        }
    }

    /**
     * Loads a Lua chunk from an input stream and pushes it on the stack as a
     * function. The Lua chunk must be either a UTF-8 encoded source chunk or a
     * pre-compiled binary chunk.
     *
     * @param inputStream the input stream
     * @param chunkName   the name of the chunk for use in error messages
     * @throws IOException if an IO error occurs
     */
    public void load(InputStream inputStream, String chunkName, String mode) throws IOException {
        if (chunkName == null) {
            throw new NullPointerException();
        }
        check();
        lua_load(luaThread, inputStream, chunkName.startsWith("=") ? chunkName : "=" + chunkName, mode);
    }

    // -- Call

    /**
     * Loads a Lua chunk from a string and pushes it on the stack as a function.
     * The string must contain a source chunk.
     *
     * @param chunk     the Lua source chunk
     * @param chunkName the name of the chunk for use in error messages
     */
    public void load(String chunk, String chunkName) {
        try {
            load(new ByteArrayInputStream(chunk.getBytes(UTF8)), chunkName.startsWith("=") ? chunkName : "=" + chunkName, "t");
        } catch (IOException e) {
            throw new LuaMemoryAllocationException(e.getMessage(), e);
        }
    }

    // -- Global

    /**
     * Dumps the function on top of the stack as a pre-compiled binary chunk
     * into an output stream.
     *
     * @param outputStream the output stream
     * @throws IOException if an IO error occurs
     */
    public void dump(OutputStream outputStream) throws IOException {
        check();
        lua_dump(luaThread, outputStream);
    }

    /**
     * Calls a Lua function. The function to call and the specified number of
     * arguments are on the stack. After the call, the specified number of
     * returns values are on stack. If the number of return values has been
     * specified as {@link #MULTRET}, the number of values on the stack
     * corresponds the to number of values actually returned by the called
     * function.
     *
     * @param argCount    the number of arguments
     * @param returnCount the number of return values, or {@link #MULTRET} to accept all
     *                    values returned by the function
     * @return number of return values
     */
    public int call(int argCount, int returnCount) {
        return lua_call(luaThread, argCount, returnCount);
    }

    /**
     * Calls a Lua function on the top stack. It will push the input argument values
     * onto the stack and then call the function, then pop out all the values over the function
     * as the return values, afterwards the
     * function is popped from top of the stack
     *
     * @param args the Lua function arguments
     * @return the input arguments plus the return values
     */
    public Object[] call(Object... args) {
        LuaType typ = type(-1);
        checkArg(typ == LuaType.FUNCTION || typ == LuaType.TABLE && type(-2) == LuaType.FUNCTION, "Invalid object. Not a function, table or userdata .");
        final int index = typ == LuaType.TABLE ? -2 : -1;
        final int top = lua_gettop(luaThread) + index;
        int nargs = args == null ? 0 : args.length;
        if (nargs > 0) for (int i = 0; i < nargs; i++)
            pushJavaObject(args[i]);
        nargs += lua_call(luaThread, nargs - index - 1, MULTRET) - nargs;
        if (nargs > 0) {
            Object[] res = new Object[nargs];
            for (int i = 1; i <= nargs; i++)
                res[i - 1] = toJavaObject(top + i, Object.class);
            pop(nargs);
            return res;
        }
        return null;
    }
    // -- Stack push

    /**
     * Pushes the value of a global variable on the stack.
     *
     * @param name the global variable name
     * @return The type of top stack if operation is successful
     */
    public LuaType getGlobal(String name) {
        check();
        return LuaType.get(lua_getglobal(luaThread, name.getBytes(UTF8)));
    }

    /**
     * Sets the value on top of the stack as a global variable and pops the
     * value from the stack.
     *
     * @param name the global variable name
     */
    public void setGlobal(String name) throws LuaMemoryAllocationException, LuaRuntimeException {
        check();
        lua_setglobal(luaThread, name.getBytes(UTF8));
    }

    /**
     * Pushes a boolean value on the stack.
     *
     * @param b the boolean value to push
     */
    public void pushBoolean(boolean b) {
        check();
        lua_pushboolean(luaThread, b ? 1 : 0);
    }

    /**
     * Pushes a byte array value as a string value on the stack.
     *
     * @param b the byte array to push
     */
    public void pushByteArray(byte[] b) {
        check();
        if (b == null) lua_pushnil(luaThread);
        else lua_pushbytearray(luaThread, b, b.length);
    }

    /**
     * Pushes an integer value as a number value on the stack.
     *
     * @param n the integer value to push
     */
    public void pushInteger(long n) {
        check();
        lua_pushinteger(luaThread, n);
    }

    /**
     * Pushes a Java object on the stack. The object is pushed "as is", i.e.
     * without conversion.
     * <p/>
     * <p>
     * If you require to push a Lua value that represents the Java object, then
     * invoke <code>pushJavaObject(object)</code>.
     * </p>
     * <p/>
     * <p>
     * You cannot push <code>null</code> without conversion since
     * <code>null</code> is not a Java object. The converter converts
     * <code>null</code> to <code>nil</code>.
     * </p>
     *
     * @param object the Java object
     * @see #pushJavaObject(Object)
     */
    protected final void pushJavaObjectRaw(final Object object) {
        if (object instanceof JavaFunction) {
            lua_pushjavafunction(luaThread, (JavaFunction) object, ((JavaFunction) object).getName().getBytes(UTF8));
        } else {
            final Class clz = toClass(object);
            lua_pushjavaobject(luaThread, object, clz.isArray() ? "[]".getBytes() : getCanonicalName(clz));
        }
    }

    /**
     * Pushes a Java object on the stack with conversion. The object is
     * processed the by the configured converter.
     *
     * @param object the Java object
     * @see #getConverter()
     * @see #setConverter(Converter)
     */
    public final void pushJavaObject(final Object object) {
        check();
        if (object == null) {
            lua_pushnil(luaThread);
        } else if (object instanceof JavaFunction) {
            lua_pushjavafunction(luaThread, (JavaFunction) object, ((JavaFunction) object).getName().getBytes(UTF8));
        } else {
            converter.convertJavaObject(this, object);
        }
    }

    public final void pushJavaFunction(final JavaFunction object) {
        lua_pushjavafunction(luaThread, object, object.getName().getBytes(UTF8));
    }

    /**
     * Pushes a nil value on the stack.
     */
    public void pushNil() {
        check();
        lua_pushnil(luaThread);
    }

    // -- Stack type test

    /**
     * Pushes a number value on the stack.
     *
     * @param n the number to push
     */
    public void pushNumber(double n) {
        check();
        lua_pushnumber(luaThread, n);
    }

    /**
     * Pushes a string value on the stack.
     *
     * @param s the string value to push
     */
    public void pushString(final String s) {
        check();
        if (s == null) lua_pushnil(luaThread);
        else {
            final byte[] b = s.getBytes(UTF8);
            lua_pushbytearray(luaThread, b, b.length);
        }
    }

    /**
     * Pushes a string value on the stack and convert to number.
     *
     * @param s the string value to push
     */
    public void pushStr2Num(final String s) {
        check();
        if (s == null || s.equals(""))
            lua_pushnil(luaThread);
        else {
            byte[] bytes = s.getBytes(UTF8);
            lua_pushstr2num(luaThread, bytes, bytes.length);
        }
    }

    public void pushStr2Num(BigDecimal s) {
        pushStr2Num(s == null ? null : s.toString());
    }

    public void pushStr2Num(BigInteger s) {
        pushStr2Num(s == null ? null : s.toString());
    }

    /**
     * Returns whether the value at the specified stack index is a boolean.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is a boolean
     */
    public boolean isBoolean(int index) {
        check();
        return lua_isboolean(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is a C function.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is a function
     */
    public boolean isCFunction(int index) {
        check();
        return lua_iscfunction(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is a function
     * (either a C function, a Java function or a Lua function.)
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is a function
     */
    public boolean isFunction(int index) {
        check();
        return lua_isfunction(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is a Java
     * function.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is a function
     */
    public boolean isJavaFunction(int index) {
        check();
        return lua_isjavafunction(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is a Java object.
     * <p/>
     * <p>
     * Note that the method does not perform conversion. If you want to check if
     * a value <i>is convertible to</i> a Java object, then invoke <code>
     * isJavaObject(index, Object.class)</code>.
     * </p>
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is a Java object
     * @see #isJavaObject(int, Class)
     */
    public boolean isJavaObjectRaw(int index) {
        check();
        return lua_isjavaobject(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is convertible to
     * a Java object of the specified type. The conversion is checked by the
     * configured converter.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is convertible to a Java object of the
     * specified type
     * @see #setConverter(Converter)
     * @see #getConverter()
     */
    public boolean isJavaObject(int index, Class<?> type) {
        check();
        return converter.getTypeDistance(this, index, type) != Integer.MAX_VALUE;
    }

    /**
     * Returns whether the value at the specified stack index is
     * <code>nil</code>.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is <code>nil</code>
     */
    public boolean isNil(int index) {
        check();
        return lua_isnil(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is undefined.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is undefined
     */
    public boolean isNone(int index) {
        check();
        return lua_isnone(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is undefined or
     * <code>nil</code>.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is undefined
     */
    public boolean isNoneOrNil(int index) {
        check();
        return lua_isnoneornil(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is a number or a
     * string convertible to a number.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is a number or a string convertible to a number
     */
    public boolean isNumber(int index) {
        check();
        return lua_isnumber(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is a string or a
     * number (which is always convertible to a string.)
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is a string or a number
     */
    public boolean isString(int index) {
        check();
        return lua_isstring(luaThread, index) != 0;
    }

    // -- Stack query

    /**
     * Returns whether the value at the specified stack index is a table.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is a table
     */
    public boolean isTable(int index) {
        check();
        return lua_istable(luaThread, index) != 0;
    }

    /**
     * Returns whether the value at the specified stack index is a thread.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return whether the value is a thread
     */
    public boolean isThread(int index) {
        check();
        return lua_isthread(luaThread, index) != 0;
    }

    /**
     * Returns whether the values at two specified stack indexes are equal
     * according to Lua semantics.
     *
     * @param index1 the first stack index
     * @param index2 the second stack index
     * @return whether the values are equal
     */
    public boolean equal(int index1, int index2) {
        check();
        return lua_equal(luaThread, index1, index2) != 0;
    }

    /**
     * Returns whether a value at a first stack index is less than the value at
     * a second stack index according to Lua semantics.
     *
     * @param index1 the first stack index
     * @param index2 the second stack index
     * @return whether the value at the first index is less than the value at
     * the second index
     */
    public boolean lessThan(int index1, int index2) throws LuaMemoryAllocationException, LuaRuntimeException {
        check();
        return lua_lessthan(luaThread, index1, index2) != 0;
    }

    /**
     * Returns the length of the value at the specified stack index. The
     * definition of the length depends on the type of the value. For strings,
     * it is the length of the string, for tables it is the result of the length
     * operator. For other types, the return value is undefined.
     *
     * @param index the stack index
     * @return the length
     */
    public int length(int index) {
        check();
        return lua_objlen(luaThread, index);
    }

    /**
     * Bypassing metatable logic, returns whether the values at two specified
     * stack indexes are equal according to Lua semantics.
     *
     * @param index1 the first stack index
     * @param index2 the second stack index
     * @return whether the values are equal
     */
    public boolean rawEqual(int index1, int index2) {
        check();
        return lua_rawequal(luaThread, index1, index2) != 0;
    }

    /**
     * Returns the boolean representation of the value at the specified stack
     * index. The boolean representation is <code>true</code> for all values
     * except <code>false</code> and <code>nil</code>.
     *
     * @param index the stack index
     * @return the boolean representation of the value
     */
    public boolean toBoolean(int index) {
        check();
        return lua_toboolean(luaThread, index) != 0;
    }

    /**
     * Returns the byte array representation of the value at the specified stack
     * index. The value must be a string or a number. If the value is a number,
     * it is in place converted to a string. Otherwise, the method returns
     * <code>null</code>.
     *
     * @param index the stack index
     * @return the byte array representation of the value
     */
    public byte[] toByteArray(int index) {
        check();
        return lua_tobytearray(luaThread, index);
    }

    public String where(int lv) {
        byte[] bytes = lua_where(luaThread, lv);
        if (bytes == null) return "";
        return new String(bytes, UTF8);
    }

    public long absIndex(int index) {
        check();
        return lua_absindex(luaThread, index);
    }

    /**
     * Returns the integer representation of the value at the specified stack
     * index. The value must be a number or a string convertible to a number.
     * Otherwise, the method returns <code>0</code>.
     *
     * @param index the stack index
     * @return the integer representation, or <code>0</code>
     */
    public long toInteger(int index) {
        check();
        return lua_tointeger(luaThread, index);
    }

    /**
     * Returns the integer representation of the value at the specified stack
     * index. The value must be a number or a string convertible to a number.
     * Otherwise, the method returns <code>0</code>.
     *
     * @param index the stack index
     * @return the integer representation, or <code>0</code>
     */
    public Long toIntegerX(int index) {
        check();
        return lua_tointegerx(luaThread, index);
    }

    /**
     * Returns the Java function of the value at the specified stack index. If
     * the value is not a Java function, the method returns <code>null</code>.
     *
     * @param index the stack index
     * @return the Java function, or <code>null</code>
     */
    public JavaFunction toJavaFunction(int index) {
        check();
        return lua_tojavafunction(luaThread, index);
    }

    /**
     * Returns the Java object of the value at the specified stack index. If the
     * value is not a Java object, the method returns <code>null</code>.
     * <p/>
     * <p>
     * Note that the method does not convert values to Java objects. If you
     * require <i>any</i> Java object that represents the value at the specified
     * index, then invoke <code>toJavaObject(index, Object.class)</code>.
     * </p>
     *
     * @param index the stack index
     * @return the Java object, or <code>null</code>
     * @see #toJavaObject(int, Class)
     */
    public Object toJavaObjectRaw(int index) {
        check();
        return lua_tojavaobject(luaThread, index);
    }

    /**
     * Returns a Java object of the specified type representing the value at the
     * specified stack index. The value must be convertible to a Java object of
     * the specified type. The conversion is executed by the configured
     * converter.
     *
     * @param index the stack index
     * @param type  the Java type to convert to
     * @return the object
     * @throws ClassCastException if the conversion is not supported by the converter
     * @see #getConverter()
     * @see #setConverter(Converter)
     */
    public <T> T toJavaObject(int index, Class<T> type) {
        check();
        return converter.convertLuaValue(this, index, type);
    }

    /**
     * Returns the number representation of the value at the specified stack
     * index. The value must be a number or a string convertible to a number.
     * Otherwise, the method returns <code>0.0</code>.
     *
     * @param index the stack index
     * @return the number representation, or <code>0.0</code>
     */
    public double toNumber(int index) {
        check();
        return lua_tonumber(luaThread, index);
    }

    /**
     * Returns the number representation of the value at the specified stack
     * index. The value must be a number or a string convertible to a number.
     * Otherwise, the method returns <code>0.0</code>.
     *
     * @param index the stack index
     * @return the number representation, or <code>0.0</code>
     */
    public Double toNumberX(int index) {
        check();
        return lua_tonumberx(luaThread, index);
    }

    /**
     * Returns the pointer representation of the value at the specified stack
     * index. The value must be a table, thread, function or userdata (such as a
     * Java object.) Otherwise, the method returns <code>0L</code>. Different
     * values return different pointers. Other than that, the returned value has
     * no portable significance.
     *
     * @param index the stack index
     * @return the pointer representation, or <code>0L</code> if none
     */
    public long toPointer(int index) {
        check();
        return lua_topointer(luaThread, index);
    }

    /**
     * Returns the string representation of the value at the specified stack
     * index. The value must be a string or a number. If the value is a number,
     * it is in place converted to a string. Otherwise, the method returns
     * <code>null</code>.
     *
     * @param index the stack index
     * @return the string representation, or <code>null</code>
     */
    public String toString(int index) {
        check();
        byte[] bytes = lua_tobytearray(luaThread, index);
        return bytes == null ? null : new String(bytes, UTF8);
    }

    // -- Stack operation

    /**
     * Returns the type of the value at the specified stack index.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the stack index
     * @return the type, or <code>null</code> if the stack index is undefined
     */
    public LuaType type(int index) {
        check();
        int type = lua_type(luaThread, index);
        return LuaType.get(type);
    }

    /**
     * Returns the name of the type at the specified stack index. The type name
     * is the display text for the Lua type except for Java objects where the
     * type name is the canonical class name.
     * <p/>
     * <p>
     * The stack index may be undefined.
     * </p>
     *
     * @param index the index
     * @return the type name
     * @see LuaType#displayText()
     * @see Class#getCanonicalName()
     */
    public String typeName(int index) {
        check();
        LuaType type = type(index);
        if (type == null) {
            return "no value";
        }
        switch (type) {
            case JAVAOBJECT:
                Object object = toJavaObjectRaw(index);
                if (object != null) {
                    Class<?> clazz;
                    if (object instanceof Class<?>) {
                        clazz = (Class<?>) object;
                    } else {
                        clazz = object.getClass();
                    }
                    return clazz.getCanonicalName();
                }
                break;
        }
        return type.displayText();
    }

    /**
     * Concatenates the specified number values on top of the stack and replaces
     * them with the concatenated value.
     *
     * @param n the number of values to concatenate
     */
    public void concat(int n) {
        check();
        lua_concat(luaThread, n);
    }

    /**
     * Moves the element at index fromidx into the valid index toidx without shifting any element
     * (therefore replacing the value at that position).
     * them with the concatenated value.
     *
     * @param from from index
     * @param to   to index
     */
    public void copy(int from, int to) {
        check();
        lua_copy(luaThread, from, to);
    }

    /**
     * Returns the number of values on the stack.
     *
     * @return the number of values on the tack
     */
    public int getTop() {
        check();
        return lua_gettop(luaThread);
    }

    /**
     * Sets the specified index as the new top of the stack.
     * <p/>
     * <p>
     * The new top of the stack may be above the current top of the stack. In
     * this case, new values are set to <code>nil</code>.
     * </p>
     *
     * @param index the index of the new top of the stack
     */
    public void setTop(int index) {
        check();
        lua_settop(luaThread, index);
    }

    /**
     * Pops the value on top of the stack inserting it at the specified index
     * and moving up elements above that index.
     *
     * @param index the stack index
     */
    public void insert(int index) {
        check();
        lua_insert(luaThread, index);
    }

    /**
     * Pops values from the stack.
     *
     * @param count the number of values to pop
     */
    public void pop(int count) {
        check();
        lua_pop(luaThread, count);
    }

    /**
     * Pushes the value at the specified index on top of the stack.
     *
     * @param index the stack index
     */
    public void pushValue(int index) {
        check();
        lua_pushvalue(luaThread, index);
    }

    // -- Table

    /**
     * Removes the value at the specified stack index moving down elements above
     * that index.
     *
     * @param index the stack index
     */
    public void remove(int index) {
        check();
        lua_remove(luaThread, index);
    }

    /**
     * Replaces the value at the specified index with the value popped from the
     * top of the stack.
     *
     * @param index the stack index
     */
    public void replace(int index) {
        check();
        lua_replace(luaThread, index);
    }

    /**
     * Pushes on the stack the value indexed by the key on top of the stack in
     * the table at the specified index. The key is replaced by the value from
     * the table.
     *
     * @param index the stack index containing the table
     * @return The type of top stack if operation is successful
     */
    public LuaType getTable(int index) {
        check();
        return LuaType.get(lua_gettable(luaThread, index));
    }

    /**
     * Pushes on the stack the value indexed by the specified string key in the
     * table at the specified index.
     *
     * @param index the stack index containing the table
     * @param key   the string key
     * @return The type of top stack if operation is successful
     */
    public LuaType getField(int index, String key) {
        check();
        return LuaType.get(lua_getfield(luaThread, index, key.getBytes(UTF8)));
    }

    /**
     * Creates a new table and pushes it on the stack.
     */
    public void newTable() {
        check();
        lua_newtable(luaThread);
    }

    /**
     * Creates a new table with pre-allocated space for a number of array
     * elements and record elements and pushes it on the stack.
     *
     * @param arrayCount  the number of array elements
     * @param recordCount the number of record elements
     */
    public void newTable(int arrayCount, int recordCount) {
        check();
        lua_createtable(luaThread, arrayCount, recordCount);
    }

    public final int pushMetaFunction(final String className, final String functionName, final JavaFunction javaFunction, final byte callOnAccess) {
        check();
        return lua_pushmetafunction(luaThread, className.getBytes(UTF8), functionName.getBytes(UTF8), javaFunction, callOnAccess);
    }

    /**
     * Pops a key from the stack and pushes on the stack the next key and its
     * value in the table at the specified index. If there is no next key, the
     * key is popped but nothing is pushed. The method returns whether there is
     * a next key.
     *
     * @param index the stack index containing the table
     * @return whether there is a next key
     */
    public boolean next(int index) {
        check();
        return lua_next(luaThread, index) != 0;
    }

    /**
     * Bypassing metatable logic, pushes on the stack the value indexed by the
     * key on top of the stack in the table at the specified index. The key is
     * replaced by the value from the table.
     *
     * @param index the stack index containing the table
     * @return The type of top stack of operation is successful
     */
    public LuaType rawGet(int index) {
        check();
        return LuaType.get(lua_rawget(luaThread, index));
    }

    /**
     * Bypassing metatable logic, pushes on the stack the value indexed by the
     * specified integer key in the table at the specified index.
     *
     * @param index the stack index containing the table
     * @param key   the integer key
     * @return The type of top stack of operation is successful
     */
    public LuaType rawGet(int index, int key) {
        check();
        return LuaType.get(lua_rawgeti(luaThread, index, key));
    }

    /**
     * Bypassing metatable logic, sets the value on top of the stack in the
     * table at the specified index using the value on the second highest stack
     * position as the key. Both the value and the key are popped from the
     * stack.
     *
     * @param index the stack index containing the table
     */
    public void rawSet(int index) {
        check();
        lua_rawset(luaThread, index);
    }

    /**
     * Bypassing metatable logic, sets the value on top of the stack in the
     * table at the specified index using the specified integer key. The value
     * is popped from the stack.
     *
     * @param index the stack index containing the table
     * @param key   the integer key
     */
    public void rawSet(int index, int key) {
        check();
        lua_rawseti(luaThread, index, key);
    }

    // -- Metatable

    /**
     * Sets the value on top of the stack in the table at the specified index
     * using the value on the second highest stack position as the key. Both the
     * value and the key are popped from the stack.
     *
     * @param index the stack index containing the table
     */
    public void setTable(int index) {
        check();
        lua_settable(luaThread, index);
    }

    /**
     * Sets the value on top of the stack in the table at the specified index
     * using the specified string key. The value is popped from the stack.
     *
     * @param index the stack index containing the table
     * @param key   the string key
     */
    public void setField(int index, String key) {
        check();
        lua_setfield(luaThread, index, key.getBytes(UTF8));
    }

    /**
     * Pushes on the stack the value of the named field in the metatable of the
     * value at the specified index and returns <code>true</code>. If the value
     * does not have a metatable or if the metatable does not contain the named
     * field, nothing is pushed and the method returns <code>false</code>.
     *
     * @param index the stack index containing the value to get the metafield from
     * @param key   the string key
     * @return whether the metafield was pushed on the stack
     */
    public boolean getMetafield(int index, String key) {
        check();
        return lua_getmetafield(luaThread, index, key) != 0;
    }

    // -- Environment table

    /**
     * Pushes on the stack the metatable of the value at the specified index. If
     * the value does not have a metatable, the method returns
     * <code>false</code> and nothing is pushed.
     *
     * @param index the stack index containing the value to get the metatable from
     * @return whether the metatable was pushed on the stack
     */
    public boolean getMetatable(int index) {
        check();
        return lua_getmetatable(luaThread, index) != 0;
    }

    /**
     * Sets the value on top of the stack as the metatable of the value at the
     * specified index. The metatable to be set is popped from the stack
     * regardless whether it can be set or not.
     *
     * @param index the stack index containing the value to set the metatable for
     * @return whether the metatable was set
     */
    public void setMetatable(int index) {
        check();
        lua_setmetatable(luaThread, index);
    }

    // -- Thread

    /**
     * Pushes on the stack the environment table of the value at the specified
     * index. If the value does not have an environment table, <code>nil</code>
     * is pushed on the stack.
     *
     * @param index the stack index containing the value to get the environment
     *              table from
     */
    public void getFEnv(int index) {
        check();
        lua_getfenv(luaThread, index);
    }

    /**
     * Sets the value on top of the stack as the environment table of the value
     * at the specified index. The environment table to be set is popped from
     * the stack regardless whether it can be set or not.
     *
     * @param index the stack index containing the value to set the environment
     *              table for
     * @return whether the environment table was set
     */
    public boolean setFEnv(int index) {
        check();
        return lua_setfenv(luaThread, index) != 0;
    }

    /**
     * Pops the start function of a new Lua thread from the stack and creates
     * the new thread with that start function. The new thread is pushed on the
     * stack.
     */
    public void newThread() {
        check();
        lua_newthread(luaThread);
    }

    /**
     * Resumes the thread at the specified stack index, popping the specified
     * number of arguments from the top of the stack and passing them to the
     * resumed thread. The method returns the number of values pushed on the
     * stack as the return values of the resumed thread.
     *
     * @param index    the stack index containing the thread
     * @param argCount the number of arguments to pass
     * @return the number of values returned by the thread
     */
    public int resume(int index, int argCount) {
        check();
        return lua_resume(luaThread, index, argCount);
    }

    // -- Reference

    /**
     * Returns the status of the thread at the specified stack index. If the
     * thread is in initial state of has finished its execution, the method
     * returns <code>0</code>. If the thread has yielded, the method returns
     * {@link #YIELD}. Other return values indicate errors for which an
     * exception has been thrown.
     *
     * @param index the index
     * @return the status
     */
    public int status(int index) {
        check();
        return lua_status(luaThread, index);
    }

    /**
     * Yields the running thread, popping the specified number of values from
     * the top of the stack and passing them as return values to the thread
     * which has resumed the running thread. The method must be used exclusively
     * at the exit point of Java functions, i.e.
     * <code>return luaState.yield(n)</code>.
     *
     * @param returnCount the number of results to pass
     * @return the return value of the Java function
     */
    public int yield(int returnCount) {
        check();
        return lua_yield(luaThread, returnCount);
    }

    // -- Optimization

    /**
     * Stores the value on top of the stack in the table at the specified index
     * and returns the integer key of the value in that table as a reference.
     * The value is popped from the stack.
     *
     * @param index the stack index containing the table where to store the value
     * @return the reference integer key
     * @see #unref(int, int)
     */
    public int ref(int index) {
        check();
        return lua_ref(luaThread, index);
    }

    /**
     * Removes a previously created reference from the table at the specified
     * index. The value is removed from the table and its integer key of the
     * reference is freed for reuse.
     *
     * @param index     the stack index containing the table where the value was
     *                  stored
     * @param reference the reference integer key
     * @see #ref(int)
     */
    public void unref(int index, int reference) {
        check();
        lua_unref(luaThread, index, reference);
    }

    // -- Argument checking

    /**
     * Counts the number of entries in a table.
     * <p/>
     * <p>
     * The method provides optimized performance over a Java implementation of
     * the same functionality due to the reduced number of JNI transitions.
     * </p>
     *
     * @param index the stack index containing the table
     * @return the number of entries in the table
     */
    public int tableSize(int index) {
        check();
        return lua_tablesize(luaThread, index);
    }

    /**
     * Moves the specified number of sequential elements in a table used as an
     * array from one index to another.
     * <p/>
     * <p>
     * The method provides optimized performance over a Java implementation of
     * the same functionality due to the reduced number of JNI transitions.
     * </p>
     *
     * @param index the stack index containing the table
     * @param from  the index to move from
     * @param to    the index to move to
     * @param count the number of elements to move
     */
    public void tableMove(int index, int from, int to, int count) {
        check();
        lua_tablemove(luaThread, index, from, to, count);
    }

    /**
     * Checks if a condition is true for the specified function argument. If
     * not, the method throws a Lua runtime exception with the specified error
     * message.
     *
     * @param index     the argument index
     * @param condition the condition
     * @param msg       the error message
     */
    public void checkArg(int index, boolean condition, String msg) {
        check();
        if (condition) {
            return;
        }
        throw getArgException(index, msg);
    }

    public static void checkArg(boolean condition, String errorMessage, Object... args) {
        if (!condition) throw new LuaRuntimeException(String.format(errorMessage, args));
    }

    public static void loadLibrary(String pattern) {
        final Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        final String sep = File.pathSeparator;
        final String[] paths = System.getProperty("java.library.path", ".").split(sep);
        for (String path : paths) {
            final File folder = new File(path.trim());
            if (!folder.exists() || !folder.isDirectory()) continue;
            final File[] filesList = folder.listFiles();
            for (File f : filesList) {
                if (f.isFile()) {
                    String name = f.getName();
                    if (!(name.toLowerCase().endsWith(".dll") && sep.equals(";")) && !(name.toLowerCase().endsWith(".so") && sep.equals(":")))
                        continue;
                    if (p.matcher(name).find()) {
                        System.load(f.getAbsolutePath());
                        return;
                    }
                }
            }
        }
        checkArg(1 == 2, "Cannot find find library pattern '%s' in path '%s'", pattern, System.getProperty("java.library.path"));
    }

    /**
     * Checks if the value of the specified function argument is a boolean. If
     * so, the argument value is returned as a boolean. Otherwise, the method
     * throws a Lua runtime exception with a descriptive error message.
     *
     * @param index the argument index
     * @return the boolean value, or the default value
     */
    public boolean checkBoolean(int index) {
        check();
        if (!isBoolean(index)) {
            throw getArgTypeException(index, LuaType.BOOLEAN);
        }
        return toBoolean(index);
    }

    /**
     * Checks if the value of the specified function argument is a boolean. If
     * so, the argument value is returned as a boolean. If the value of the
     * specified argument is undefined or <code>nil</code>, the method returns
     * the specified default value. Otherwise, the method throws a Lua runtime
     * exception with a descriptive error message.
     *
     * @param index the argument index
     * @param d     the default value
     * @return the boolean value
     */
    public boolean checkBoolean(int index, boolean d) {
        check();
        if (isNoneOrNil(index)) {
            return d;
        }
        return checkBoolean(index);
    }

    /**
     * Checks if the value of the specified function argument is a string or a
     * number. If so, the argument value is returned as a byte array. Otherwise,
     * the method throws a Lua runtime exception with a descriptive error
     * message.
     *
     * @param index the argument index
     * @return the byte array value
     */
    public byte[] checkByteArray(int index) {
        check();
        if (!isString(index)) {
            throw getArgTypeException(index, LuaType.STRING);
        }
        return toByteArray(index);
    }

    /**
     * Checks if the value of the specified function argument is a string or a
     * number. If so, the argument value is returned as a byte array. If the
     * value of the specified argument is undefined or <code>nil</code>, the
     * method returns the specified default value. Otherwise, the method throws
     * a Lua runtime exception with a descriptive error message.
     *
     * @param index the argument index
     * @param d     the default value
     * @return the string value, or the default value
     */
    public byte[] checkByteArray(int index, byte[] d) {
        check();
        if (isNoneOrNil(index)) {
            return d;
        }
        return checkByteArray(index);
    }

    /**
     * Checks if the value of the specified function argument is a number or a
     * string convertible to a number. If so, the argument value is returned as
     * an integer. Otherwise, the method throws a Lua runtime exception with a
     * descriptive error message.
     *
     * @param index the argument index
     * @return the integer value
     */
    public long checkInteger(int index) {
        check();
        Long integer = toIntegerX(index);
        if (integer == null) {
            throw getArgTypeException(index, LuaType.NUMBER);
        }
        return integer.longValue();
    }

    /**
     * Checks if the value of the specified function argument is a number or a
     * string convertible to a number. If so, the argument value is returned as
     * an integer. If the value of the specified argument is undefined or
     * <code>nil</code>, the method returns the specified default value.
     * Otherwise, the method throws a Lua runtime exception with a descriptive
     * error message.
     *
     * @param index the argument index
     * @param d     the default value
     * @return the integer value, or the default value
     */
    public long checkInteger(int index, int d) {
        check();
        if (isNoneOrNil(index)) {
            return d;
        }
        return checkInteger(index);
    }

    /**
     * Checks if the value of the specified function argument is a number or a
     * string convertible to a number. If so, the argument value is returned as
     * a number. Otherwise, the method throws a Lua runtime exception with a
     * descriptive error message.
     *
     * @param index the argument index
     * @return the number value
     */
    public double checkNumber(int index) {
        Double number = toNumberX(index);
        if (number == null) {
            throw getArgTypeException(index, LuaType.NUMBER);
        }
        return number.doubleValue();
    }

    /**
     * Checks if the value of the specified function argument is a number or a
     * string convertible to a number. If so, the argument value is returned as
     * a number. If the value of the specified argument is undefined or
     * <code>nil</code>, the method returns the specified default value.
     * Otherwise, the method throws a Lua runtime exception with a descriptive
     * error message.
     *
     * @param index the argument index
     * @param d     the default value
     * @return the number value, or the default value
     */
    public double checkNumber(int index, double d) {
        check();
        if (isNoneOrNil(index)) {
            return d;
        }
        return checkNumber(index);
    }

    /**
     * Checks if the value of the specified function argument is convertible to
     * a Java object of the specified type. If so, the argument value is
     * returned as a Java object of the specified type. Otherwise, the method
     * throws a Lua runtime exception with a descriptive error message.
     * <p/>
     * <p>
     * Note that the converter converts <code>nil</code> to <code>null</code>.
     * Therefore, the method may return <code>null</code> if the value is
     * <code>nil</code>.
     * </p>
     *
     * @param index the argument index
     * @param clazz the expected type
     * @return the Java object, or <code>null</code>
     */
    public <T> T checkJavaObject(int index, Class<T> clazz) {
        check();
        if (!isJavaObject(index, clazz)) {
            throw getArgException(index, String.format("exptected %s, got %s", clazz.getCanonicalName(), typeName(index)));
        }
        return toJavaObject(index, clazz);
    }

    /**
     * Checks if the value of the specified function argument is convertible to
     * a Java object of the specified type. If so, the argument value is
     * returned as a Java object of the specified type. If the value of the
     * specified argument is undefined or <code>nil</code>, the method returns
     * the specified default value. Otherwise, the method throws a Lua runtime
     * exception with a descriptive error message.
     *
     * @param index the argument index
     * @param clazz the expected class
     * @param d     the default value
     * @return the Java object, or the default value
     */
    public <T> T checkJavaObject(int index, Class<T> clazz, T d) {
        check();
        if (isNoneOrNil(index)) {
            return d;
        }
        return checkJavaObject(index, clazz);
    }

    /**
     * Checks if the value of the specified function argument is a string or a
     * number matching one of the specified options. If so, the index position
     * of the matched option is returned. Otherwise, the method throws a Lua
     * runtime exception with a descriptive error message.
     *
     * @param index   the argument index
     * @param options the options
     * @return the index position of the matched option
     */
    public int checkOption(int index, String[] options) {
        return checkOption(index, options, null);
    }

    /**
     * Checks if the value of the specified function argument is a string or a
     * number matching one of the specified options. If so, the index position
     * of the matched option is returned. If the specified stack index is
     * non-valid or if its value is <code>nil</code>, the method matches the
     * specified default value. If no match is found, the method throws a Lua
     * runtime exception with a descriptive error message.
     *
     * @param index   the argument index
     * @param options the options
     * @param d       the default value
     * @return the index position of the matched option
     */
    public int checkOption(int index, String[] options, String d) {
        String s = d != null ? checkString(index, d) : checkString(index);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(s)) {
                return i;
            }
        }
        throw getArgException(index, String.format("invalid option '%s'", s));
    }

    /**
     * Checks if the value of the specified function argument is a string or a
     * number. If so, the argument value is returned as a string. Otherwise, the
     * method throws a Lua runtime exception with a descriptive error message.
     *
     * @param index the argument index
     * @return the string value
     */
    public String checkString(int index) {
        check();
        if (!isString(index)) {
            throw getArgTypeException(index, LuaType.STRING);
        }
        return toString(index);
    }

    // -- Proxy

    /**
     * Checks if the value of the specified function argument is a string or a
     * number. If so, the argument value is returned as a string. If the value
     * of the specified argument is undefined or <code>nil</code>, the method
     * returns the specified default value. Otherwise, the method throws a Lua
     * runtime exception with a descriptive error message.
     *
     * @param index the argument index
     * @param d     the default value
     * @return the string value, or the default value
     */
    public String checkString(int index, String d) {
        check();
        if (isNoneOrNil(index)) {
            return d;
        }
        return checkString(index);
    }

    /**
     * Checks if the value of the specified function argument is of the
     * specified type. If not, the method throws a Lua runtime exception with a
     * descriptive error message.
     *
     * @param index the argument index
     * @param type  the type
     */
    public void checkType(int index, LuaType type) {
        check();
        if (type(index) != type) {
            throw getArgTypeException(index, type);
        }
    }

    /**
     * Returns a proxy object for the Lua value at the specified index.
     *
     * @param index the stack index containing the Lua value
     * @return the Lua value proxy
     */
    public LuaValueProxy getProxy(int index) {
        pushValue(index);
        return new LuaValueProxyImpl(ref(REGISTRYINDEX));
    }

    // -- Private methods

    /**
     * Returns a proxy object implementing the specified interface in Lua. The
     * table at the specified stack index contains the method names from the
     * interface as keys and the Lua functions implementing the interface
     * methods as values. The returned object always implements the
     * {@link LuaValueProxy} interface in addition to the specified interface.
     *
     * @param index     the stack index containing the table
     * @param interfaze the interface
     * @return the proxy object
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(int index, Class<T> interfaze) {
        check();
        return (T) getProxy(index, new Class<?>[]{interfaze});
    }

    /**
     * Returns a proxy object implementing the specified list of interfaces in
     * Lua. The table at the specified stack index contains the method names
     * from the interfaces as keys and the Lua functions implementing the
     * interface methods as values. The returned object always implements the
     * {@link LuaValueProxy} interface in addition to the specified interfaces.
     *
     * @param index      the stack index containing the table
     * @param interfaces the interfaces
     * @return the proxy object
     */
    public LuaValueProxy getProxy(int index, Class<?>[] interfaces) {
        check();
        pushValue(index);
        if (!isTable(index)) {
            throw new IllegalArgumentException(String.format("index %d is not a table", index));
        }
        Class<?>[] allInterfaces = new Class<?>[interfaces.length + 1];
        System.arraycopy(interfaces, 0, allInterfaces, 0, interfaces.length);
        allInterfaces[allInterfaces.length - 1] = LuaValueProxy.class;
        int reference = ref(REGISTRYINDEX);
        try {
            Object proxy = Proxy.newProxyInstance(classLoader, allInterfaces, new LuaInvocationHandler(reference));
            reference = -1;
            return (LuaValueProxy) proxy;
        } finally {
            if (reference >= 0) {
                unref(REGISTRYINDEX, reference);
            }
        }
    }

    /**
     * Returns whether this Lua state is open.
     */
    final private boolean isOpenInternal() {
        return luaState != 0L;
    }

    /**
     * Closes this Lua state.
     */
    private void closeInternal() {
        if (isOpenInternal()) {
            lua_close(luaThread, ownState);
            if (isOpenInternal()) {
                throw new IllegalStateException("cannot close");
            }
        }
    }

    /**
     * Checks this Lua state.
     */
    private void check() {
        // Check open
        if (!isOpenInternal()) {
            throw new IllegalStateException("Lua state is closed");
        }
    }

    /**
     * Creates a Lua runtime exception to indicate an argument type error.
     */
    protected LuaRuntimeException getArgTypeException(int index, LuaType type) {
        return getArgException(index, String.format("expected %s, got %s", type.toString().toLowerCase(), type(index).toString().toLowerCase()));
    }

    /**
     * Creates a Lua runtime exception to indicate an argument error.
     *
     * @param extraMsg
     * @return
     */
    private LuaRuntimeException getArgException(int index, String extraMsg) {
        check();

        // Get execution point
        String name = null, nameWhat = null;
        LuaDebug luaDebug = lua_getstack(luaThread, 0);
        if (luaDebug != null) {
            lua_getinfo(luaThread, "n", luaDebug);
            name = luaDebug.getName();
            nameWhat = luaDebug.getNameWhat();
        }
        // Adjust for methods
        if ("method".equals(nameWhat)) {
            index--;
        }

        // Format message
        String msg;
        String argument = index > 0 ? String.format("argument #%d", index)
                : "self argument";
        if (name != null) {
            msg = String
                    .format("bad %s to '%s' (%s)", argument, name, extraMsg);
        } else {
            msg = String.format("bad %s (%s)", argument, extraMsg);
        }
        return new LuaRuntimeException(msg);
    }

    final private static native int lua_registryindex(long L);

    final private native int lua_newstate(int apiversion, long luaState);

    final private native void lua_close(long T, boolean ownState);

    final private native int lua_gc(long T, int what, int data);

    final private native void lua_openlib(long T, int lib);

    final private native void lua_openlibs(long L);

    final private native void lua_load(long T, InputStream inputStream, String chunkname, String mode) throws IOException;

    final private native void lua_dump(long T, OutputStream outputStream) throws IOException;

    final private native int lua_call(long T, int nargs, int nresults);

    final private native int lua_getglobal(long T, byte[] name);

    final private native void lua_setglobal(long T, byte[] name);

    final private native void lua_pushboolean(long T, int b);

    final private native void lua_pushbytearray(long T, byte[] b, int len);

    final private native void lua_pushinteger(long T, long n);

    final private native void lua_pushjavafunction(long T, JavaFunction f, byte[] funcName);

    final private native void lua_pushjavaobject(long T, Object object, byte[] className);

    final private native void lua_pushnil(long L);

    final private native void lua_pushnumber(long T, double n);

    final private native void lua_pushstring(long T, String s);

    final private native void lua_pushstr2num(long T, byte[] bytes, int size);

    final private native int lua_isboolean(long T, int index);

    final private native int lua_iscfunction(long T, int index);

    final private native int lua_isfunction(long T, int index);

    final private native int lua_isjavafunction(long T, int index);

    final private native int lua_isjavaobject(long T, int index);

    final private native int lua_isnil(long T, int index);

    final private native int lua_isnone(long T, int index);

    final private native int lua_isnoneornil(long T, int index);

    final private native int lua_isnumber(long T, int index);

    final private native int lua_isstring(long T, int index);

    final private native int lua_istable(long T, int index);

    final private native int lua_isthread(long T, int index);

    final private native int lua_equal(long T, int index1, int index2);

    final private native int lua_lessthan(long T, int index1, int index2);

    final private native int lua_objlen(long T, int index);

    final private native int lua_rawequal(long T, int index1, int index2);

    final private native int lua_toboolean(long T, int index);

    final private native byte[] lua_tobytearray(long T, int index);

    final private native long lua_tointeger(long T, int index);

    final private native Long lua_tointegerx(long T, int index);

    final private native JavaFunction lua_tojavafunction(long T, int index);

    final private native Object lua_tojavaobject(long T, int index);

    final private native double lua_tonumber(long T, int index);

    final private native Double lua_tonumberx(long T, int index);

    final private native int lua_absindex(long T, int index);

    final private native long lua_topointer(long T, int index);

    final private native String lua_tostring(long T, int index);

    final private native void lua_trace(int trace);

    final private native int lua_type(long T, int index);

    final private native void lua_concat(long T, int n);

    final private native void lua_copy(long T, int from, int to);

    final private native int lua_gettop(long L);

    final private native void lua_insert(long T, int index);

    final private native void lua_pop(long T, int n);

    final private native void lua_pushvalue(long T, int index);

    final private native void lua_remove(long T, int index);

    final private native void lua_replace(long T, int index);

    final private native void lua_settop(long T, int index);

    final private native void lua_createtable(long T, int narr, int nrec);

    final private native String lua_findtable(long T, int idx, String fname, int szhint);

    final private native int lua_gettable(long T, int index);

    final private native int lua_getfield(long T, int index, byte[] k);

    final private native void lua_newtable(long L);

    final private native int lua_next(long T, int index);

    final private native int lua_rawget(long T, int index);

    final private native int lua_rawgeti(long T, int index, int n);

    final private native void lua_rawset(long T, int index);

    final private native void lua_rawseti(long T, int index, int n);

    final private native void lua_settable(long T, int index);

    final private native void lua_setfield(long T, int index, byte[] k);

    final private native int lua_getmetatable(long T, int index);

    final private native void lua_setmetatable(long T, int index);

    final private native int lua_getmetafield(long T, int index, String k);

    final private native void lua_getfenv(long T, int index);

    final private native int lua_setfenv(long T, int index);

    final private native void lua_newthread(long L);

    final private native int lua_resume(long T, int index, int nargs);

    final private native int lua_status(long T, int index);

    final private native int lua_yield(long T, int nresults);

    final private native int lua_ref(long T, int index);

    final private native void lua_unref(long T, int index, int ref);

    final private native LuaDebug lua_getstack(long T, int level);

    final private native int lua_getinfo(long T, String what, LuaDebug ar);

    final private native String lua_funcname(long L);

    final private native int lua_narg(long T, int narg);

    final private native int lua_tablesize(long T, int index);

    final private native void lua_tablemove(long T, int index, int from, int to, int count);

    final private native byte[] lua_where(long T, int lv);

    final private native int lua_pushmetafunction(final long T, final byte[] className, final byte[] functionName, final JavaFunction functionObject, final byte callOnAccess);

    final private native void lua_newstate_done(long T);

    final private native void lua_table_pair_init(final long T, Object[] pair, byte[] types, Object[] paramArgs, byte[] paramTypes);

    final private native void lua_table_pair_push(final long T, int index, int options);

    final private native void lua_table_pair_get(final long T, int index, int options);


    protected Object[] keyPair = new Object[2];
    protected byte[] keyTypes = new byte[2];
    public LuaType[] keyLuaTypes = new LuaType[1];
    public LuaType[] kvLuaTypes = new LuaType[2];
    public final Object[] paramArgs = new Object[33];
    public final byte[] paramTypes = new byte[33];

    public final void pairInit() {
        check();
        keyPair = new Object[2];
        keyTypes = new byte[2];
        lua_table_pair_init(luaThread, keyPair, keyTypes, paramArgs, paramTypes);
    }

    public final static int PAIR_POP = 1;
    public final static int PAIR_INDEX_IS_REF = 3;
    public final static int PAIR_INSERT_MODE = 4;
    public final static int PAIR_RETURN_OLD_VALUE = 8;
    public final static int PAIR_LOAD_TABLE = 16;
    public final static int PAIR_PUSH_ARRAY = 64;

    public final Object tablePush(int tableIndex, int options, Object key, Object value, Class returnClass) {
        if (key == null) throw new NullPointerException("Invalid table key.");
        if ((options & PAIR_INSERT_MODE) > 0) {
            if (!(key instanceof Number))
                throw new IllegalArgumentException("Invalid key " + key + ", integer is expected.");
            key = ((Number) key).intValue();
        }
        keyPair[0] = key; //When PAIR_INSERT_MODE and key
        keyPair[1] = value;
        keyTypes[1] = 0;
        if ((options & PAIR_PUSH_ARRAY) > 0 && value != null)
            converter.toLuaTable(this, 1);
        converter.toLuaType(this, keyPair, keyTypes, keyTypes[1] > 0 ? 1 : 2, true);
        lua_table_pair_push(luaThread, tableIndex, options);
        if ((options & PAIR_RETURN_OLD_VALUE) == 0) return null;
        converter.getLuaValues(this, false, keyPair, keyTypes, keyPair, keyLuaTypes, returnClass);
        keyPair[1] = null;
        return keyPair[0];
    }

    public final void tablePushArray(Object[] key) {
        if (key == null) throw new NullPointerException("Attemp to create and null Lua table.");
        keyPair[0] = 1;
        keyTypes[0] = LuaType.NUMBER.id;
        keyPair[1] = key;
        converter.toLuaTable(this, 1);
        lua_table_pair_push(luaThread, key.length, 64 | 128);
    }

    public final Object tableGet(int tableIndex, int options, Object key, Class returnClass) {
        if (key == null && (options & 32) == 0) throw new NullPointerException("Invalid table key.");
        keyPair[0] = key;
        converter.toLuaType(this, keyPair, keyTypes, 1, (options & 32) == 0);
        lua_table_pair_get(luaThread, tableIndex, options);
        converter.getLuaValues(this, false, keyPair, keyTypes, keyPair, (options & 32) > 0 ? kvLuaTypes : keyLuaTypes, returnClass);
        return keyPair[0];
    }

    public final Object[] tableNext(int tableIndex, int options, Object key, Class valueClass) {
        tableGet(tableIndex, options | PAIR_LOAD_TABLE | 32, key, valueClass);
        return new Object[]{keyPair[0], keyPair[1]};
    }

    public final int pushJavaFunctionResult(final Object result) {
        paramArgs[0] = result;
        converter.toLuaType(this, paramArgs, paramTypes, 1, false);
        return 1;
    }


    // -- Enumerated types

    /**
     * Represents a Lua library.
     */
    public enum Library {
        /**
         * The base library, including the coroutine functions.
         */
        BASE,
        /**
         * The table library.
         */
        TABLE,
        /**
         * The IO library.
         */
        IO,
        /**
         * The OS library.
         */
        OS,
        /**
         * The string library.
         */
        STRING,
        /**
         * The math library.
         */
        MATH,
        /**
         * The debug library.
         */
        DEBUG,
        /**
         * The package library.
         */
        PACKAGE, BIT, JIT, FFI,
        /**
         * The Java library.
         */
        JAVA {
            @Override
            void open(LuaState luaState) {
                JavaModule.getInstance().open(luaState);
            }
        };

        // -- Methods

        /**
         * Opens this library.
         */
        void open(LuaState lua) {
            lua.lua_openlib(lua.luaThread, ordinal());
        }
    }

    /**
     * Represents a Lua garbage collector action. See the Lua Reference Manual
     * for an explanation of these actions.
     */
    public enum GcAction {
        /**
         * Stop.
         */
        STOP,
        /**
         * Restart.
         */
        RESTART,
        /**
         * Collect.
         */
        COLLECT,
        /**
         * Count memory in kilobytes.
         */
        COUNT,
        /**
         * Count reminder in bytes.
         */
        COUNTB,
        /**
         * Step.
         */
        STEP,
        /**
         * Set pause.
         */
        SETPAUSE,
        /**
         * Set step multiplier.
         */
        SETSTEPMUL
    }

    // -- Nested types

    /**
     * Phantom reference to a Lua value proxy for pre-mortem cleanup.
     */
    private static class LuaValueProxyRef extends PhantomReference<LuaValueProxyImpl> {
        // -- State
        private final int reference;

        // --Construction

        /**
         * Creates a new instance.
         */
        public LuaValueProxyRef(LuaValueProxyImpl luaProxyImpl, int reference) {
            super(luaProxyImpl, luaProxyImpl.getLuaState().proxyQueue);
            this.reference = reference;
        }

        // -- Properties

        /**
         * Returns the reference.
         */
        public int getReference() {
            return reference;
        }
    }

    /**
     * Lua value proxy implementation.
     */
    private class LuaValueProxyImpl implements LuaValueProxy {
        // -- State
        private final int reference;
        private final LuaValueProxyRef proxy;
        // -- Construction

        /**
         * Creates a new instance.
         */
        public LuaValueProxyImpl(int reference) {
            cleanup();
            this.reference = reference;
            this.proxy = new LuaValueProxyRef(this, reference);
            proxySet.add(proxy);
        }

        // -- LuaProxy methods
        @Override
        public LuaState getLuaState() {
            return LuaState.this;
        }

        @Override
        public void pushValue() {
            rawGet(REGISTRYINDEX, reference);
        }

        @Override
        public int getRef() {
            return reference;
        }

        @Override
        public void unRef() {
            proxySet.remove(this.proxy);
            LuaState.this.unref(REGISTRYINDEX, reference);
        }

        @Override
        public void finalize() {
            cleanup();
        }
    }

    /**
     * Invocation handler for implementing Java interfaces in Lua.
     */
    private class LuaInvocationHandler extends LuaValueProxyImpl implements InvocationHandler {
        // -- Construction

        public LuaInvocationHandler(int reference) {
            super(reference);
        }

        // -- InvocationHandler methods
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle LuaProxy methods
            if (method.getDeclaringClass() == LuaValueProxy.class) {
                return method.invoke(this, args);
            }

            // Handle Lua calls

            pushValue();
            getField(-1, method.getName());
            if (!isFunction(-1)) {
                pop(2);
                throw new UnsupportedOperationException(method.getName());
            }
            if ((method.getModifiers() & STATIC) > 0 || (method.getModifiers() & TRANSIENT) > 0)
                //if(!isTableArgs)
                remove(-2);
            else
                insert(-2);
            int argCount = args != null ? args.length : 0;
            while (argCount == 1 && args[0] != null && args[0].getClass().isArray()) {
                args = (Object[]) args[0];
                argCount = args.length;
            }
            Object[] ret = call(args);
            return ret != null && ret.length == 1 ? ret[0] : ret;
        }
    }

    /**
     * Lua debug structure.
     */
    private static class LuaDebug {
        /**
         * The <code>lua_Debug</code> pointer on the JNI side. <code>0</code>
         * implies that the activation record has been freed. The field is
         * modified exclusively on the JNI side and must not be touched on the
         * Java side.
         */
        private final long luaDebug;
        /**
         * Ensures proper finalization of this Lua debug structure.
         */
        private Object finalizeGuardian;

        /**
         * Creates a new instance.
         */
        private LuaDebug(long luaDebug, boolean ownDebug) {
            this.luaDebug = luaDebug;
            if (ownDebug) {
                finalizeGuardian = new Object() {
                    @Override
                    public void finalize() {
                        lua_debugfree();
                    }
                };
            }
        }

        // -- Properties

        /**
         * Returns a reasonable name for the function given by this activation
         * record, or <code>null</code> if none is found.
         */
        public String getName() {
            return lua_debugname();
        }

        /**
         * Explains the name of the function given by this activation record.
         */
        public String getNameWhat() {
            return lua_debugnamewhat();
        }

        // -- Native methods
        final private native void lua_debugfree();

        final private native String lua_debugname();

        final private native String lua_debugnamewhat();
    }
}
