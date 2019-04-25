/*
 * $Id: JavaModule.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.naef.jnlua.JavaReflector.Metamethod;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static com.esotericsoftware.reflectasm.util.NumberUtils.convert;
import static com.naef.jnlua.LuaState.toClassName;

/**
 * Provides the Java module for Lua. The Java module contains Java functions for
 * using Java in Lua.
 */
public class JavaModule {
    // -- Static
    private static final JavaModule INSTANCE = new JavaModule();
    private static final Map<String, Class<?>> PRIMITIVE_TYPES = new HashMap<>();
    private static final JavaFunction[] EMPTY_MODULE = new JavaFunction[0];
    private static final Set<Class<?>> WRAPPER_TYPES = new HashSet<>();

    static {
        PRIMITIVE_TYPES.put("boolean", Boolean.TYPE);
        PRIMITIVE_TYPES.put("byte", Byte.TYPE);
        PRIMITIVE_TYPES.put("char", Character.TYPE);
        PRIMITIVE_TYPES.put("double", Double.TYPE);
        PRIMITIVE_TYPES.put("float", Float.TYPE);
        PRIMITIVE_TYPES.put("int", Integer.TYPE);
        PRIMITIVE_TYPES.put("long", Long.TYPE);
        PRIMITIVE_TYPES.put("short", Short.TYPE);
        PRIMITIVE_TYPES.put("void", Void.TYPE);
        WRAPPER_TYPES.add(Boolean.class);
        WRAPPER_TYPES.add(Character.class);
        WRAPPER_TYPES.add(Byte.class);
        WRAPPER_TYPES.add(Short.class);
        WRAPPER_TYPES.add(Integer.class);
        WRAPPER_TYPES.add(Long.class);
        WRAPPER_TYPES.add(Float.class);
        WRAPPER_TYPES.add(Double.class);
        WRAPPER_TYPES.add(Void.class);
        WRAPPER_TYPES.add(String.class);
        WRAPPER_TYPES.add(BigInteger.class);
        WRAPPER_TYPES.add(BigDecimal.class);
    }

    // -- State
    private final JavaFunction[] functions = {new Require(), new New(), new InstanceOf(), new Cast(), new Proxy(), new Pairs(), new IPairs(), new ToTable(), new Elements(), new Fields(), new Methods(), new Properties()};

    // -- Static methods

    /**
     * Singleton.
     */
    private JavaModule() {
    }

    // -- Construction

    /**
     * Returns the instance of the Java module.
     *
     * @return the instance
     */
    public static JavaModule getInstance() {
        return INSTANCE;
    }

    // -- Operations

    /**
     * Loads a type. The named type is a primitive type or a class.
     */
    private static Class<?> loadType(LuaState luaState, String typeName) {
        Class<?> clazz;
        if ((clazz = PRIMITIVE_TYPES.get(typeName)) != null) {
            return clazz;
        }
        try {
            if (!typeName.contains(".")) typeName = "java.lang." + typeName;
            clazz = luaState.getClassLoader().loadClass(typeName);
            return clazz;
        } catch (ClassNotFoundException e) {
            throw new LuaRuntimeException(e);
        }
    }

    /**
     * Opens this module in a Lua state. The method is invoked by
     * {@link LuaState#openLibs()} or by
     * {@link LuaState#openLib(LuaState.Library)} if
     * {@link LuaState.Library#JAVA} is passed.
     *
     * @param luaState the Lua state to open in
     */
    public void open(LuaState luaState) {
        luaState.register("java", functions);
        luaState.pop(1);
    }

    /**
     * Returns a table-like Lua value for the specified map. The returned value
     * corresponds to the return value of the <code>totable()</code> function
     * provided by this Java module.
     *
     * @param map the map
     * @return the table-like Lua value
     */
    public TypedJavaObject toTable(Map<?, ?> map) {
        return ToTable.toTable(map);
    }

    // -- Private methods

    /**
     * Returns a table-like Lua value for the specified list. The returned value
     * corresponds to the return value of the <code>totable()</code> function
     * provided by this Java module.
     *
     * @param list the list
     * @return the table-like Lua value
     */
    public TypedJavaObject toTable(List<?> list) {
        return ToTable.toTable(list);
    }

    // -- Nested types

    /**
     * Imports a Java class into the Lua namespace. Returns the class and a
     * status code. The status code indicates if the class was stored in the Lua
     * namespace. Primitive types and classes without a package are not stored
     * in the Lua namespace.
     */
    private static class Require extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public int invoke(LuaState luaState) {
            // Check arguments
            String className = luaState.checkString(1);
            boolean doImport = luaState.checkBoolean(2, false);

            // Load
            Class<?> clazz = loadType(luaState, className);
            luaState.pushJavaObject(clazz);

            // Import
            if (doImport) {
                className = clazz.getName();
                int lastDotIndex = className.lastIndexOf('.');
                if (lastDotIndex >= 0) {
                    String packageName = className.substring(0, lastDotIndex);
                    className = className.substring(lastDotIndex + 1);
                    luaState.register(packageName, EMPTY_MODULE);
                    luaState.pushJavaObject(clazz);
                    luaState.setField(-2, className);
                    luaState.pop(1);
                } else {
                    luaState.pushJavaObject(clazz);
                    luaState.setGlobal(className);
                }
            }
            luaState.pushBoolean(doImport);

            // Return
            return 2;
        }

        @Override
        public String getName() {
            return "require";
        }
    }

    /**
     * Creates and returns a new Java object or array thereof. The first
     * argument designates the type to instantiate, either as a class or a
     * string. The remaining arguments are the dimensions.
     */

    private static class New extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            // Find class
            Class<?> clazz;
            if (args[0] instanceof Class) {
                clazz = (Class) args[0];
            } else {
                String className = String.valueOf(args[0]);
                clazz = loadType(luaState, className);
            }
            boolean isArray = args.length > 1 ? true : false;
            for (int i = 1; i < args.length; i++) {
                if (args[i] == null) {
                    isArray = false;
                    break;
                }
                final Class c = args[i].getClass();
                if (c != Integer.class && c != int.class && c != double.class && c != Double.class) {
                    isArray = false;
                    break;
                }
                if (((Number) args[i]).intValue() != ((Number) args[i]).doubleValue()) {
                    isArray = false;
                    break;
                }
            }
            if (isArray) {
                // Instantiate
                Object object;
                int dimensionCount = args.length - 1;
                switch (dimensionCount) {
                    case 0:
                        object = Array.newInstance(clazz);
                        break;
                    case 1:
                        object = Array.newInstance(clazz, ((Number) args[1]).intValue());
                        break;
                    default:
                        int[] dimensions = new int[dimensionCount];
                        for (int i = 0; i < dimensionCount; i++) {
                            dimensions[i] = ((Number) args[1 + i]).intValue();
                        }
                        object = Array.newInstance(clazz, dimensions);
                }
                // Return
                luaState.pushJavaObject(object);
            } else {
                Invoker invoker = Invoker.get(clazz, ClassAccess.NEW, "\3");
                LuaState.checkArg(invoker != null, "Cannot find constructor of class %s", toClassName(clazz));
                invoker.call(luaState, args);
            }
        }

        @Override
        public String getName() {
            return "new";
        }
    }

    /**
     * Returns whether an object is an instance of a type. The object is given
     * as the first argument. the type is given as the second argument, either
     * as a class or as a type name.
     */
    private static class InstanceOf extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            // Get the object
            Object object = args[0];

            // Find class
            Class<?> clazz;
            if (args[1] instanceof Class) {
                clazz = (Class) args[1];
            } else {
                String className = String.valueOf(args[1]);
                clazz = loadType(luaState, className);
            }

            // Type check
            luaState.pushBoolean(clazz.isInstance(object));
        }

        @Override
        public String getName() {
            return "instanceof";
        }
    }

    /**
     * Creates a typed Java object.
     */
    private static class Cast extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            // Find class
            final Class<?> clazz;
            if (args[1] instanceof Class) {
                clazz = (Class) args[1];
            } else {
                String className = String.valueOf(args[1]);
                clazz = loadType(luaState, className);
            }

            // Get the object
            final Object object = convert(args[0], clazz);

            // Push result
            luaState.pushJavaObject(new TypedJavaObject() {
                @Override
                public Object getObject() {
                    return object;
                }

                @Override
                public Class<?> getType() {
                    return clazz;
                }

                @Override
                public boolean isStrong() {
                    return false;
                }
            });
        }

        @Override
        public String getName() {
            return "cast";
        }
    }

    /**
     * Creates a dynamic proxy object the implements a set of Java interfaces in
     * Lua.
     */
    private static class Proxy extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public int invoke(LuaState luaState) {
            // Check table
            luaState.checkType(1, LuaType.TABLE);

            // Get interfaces
            int interfaceCount = luaState.getTop() - 1;
            luaState.checkArg(2, interfaceCount > 0, "no interface specified");
            Class<?>[] interfaces = new Class<?>[interfaceCount];
            for (int i = 0; i < interfaceCount; i++) {
                if (luaState.isJavaObject(i + 2, Class.class)) {
                    interfaces[i] = luaState.checkJavaObject(i + 2, Class.class);
                } else {
                    String interfaceName = luaState.checkString(i + 2);
                    interfaces[i] = loadType(luaState, interfaceName);
                }
            }

            // Create proxy
            luaState.pushJavaObjectRaw(luaState.getProxy(1, interfaces));
            return 1;
        }

        @Override
        public String getName() {
            return "proxy";
        }
    }

    /**
     * Provides the ipairs iterator from the Java reflector.
     */
    private static class IPairs extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            LuaState.checkArg(args[0] != null, "Java object expected, got %s", toClassName(args[0]));
            JavaFunction metamethod = luaState.getMetamethod(args[0], Metamethod.IPAIRS);
            metamethod.call(luaState, args);
        }

        @Override
        public String getName() {
            return "ipairs";
        }
    }

    /**
     * Provides the ipairs iterator from the Java reflector.
     */
    private static class Pairs extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            LuaState.checkArg(args[0] != null, "Java object expected, got %s", toClassName(args[0]));
            JavaFunction metamethod = luaState.getMetamethod(args[0], Metamethod.PAIRS);
            metamethod.call(luaState, args);
        }

        @Override
        public String getName() {
            return "pairs";
        }
    }

    /**
     * Provides a wrapper object for table-like map and list access from Lua.
     */
    protected static class ToTable extends JavaFunction {
        // -- Static methods

        /**
         * Returns a table-like Lua value for the specified map.
         */
        @SuppressWarnings("unchecked")
        public static TypedJavaObject toTable(Map<?, ?> map) {
            return new LuaMap((Map<Object, Object>) map);
        }

        /**
         * Returns a table-list Lua value for the specified list.
         */
        @SuppressWarnings("unchecked")
        public static TypedJavaObject toTable(List<?> list) {
            return new LuaList((List<Object>) list);
        }

        // -- JavaFunction methods
        @SuppressWarnings("unchecked")
        @Override
        public void call(LuaState luaState, Object[] args) {
            if (args[0] instanceof Map) {
                Map<Object, Object> map = (Map<Object, Object>) args[0];
                luaState.pushJavaObject(new LuaMap(map));
            } else if (args[0] instanceof List) {
                List<Object> list = (List<Object>) args[0];
                luaState.pushJavaObject(new LuaList(list));
            } else {
                LuaState.checkArg(1 == 2, "expected map or list, got %s", toClassName(args[0]));
            }
        }

        @Override
        public String getName() {
            return "totable";
        }

        // -- Member types

        /**
         * Provides table-like access in Lua to a Java map.
         */
        protected static class LuaMap extends JavaReflector implements TypedJavaObject {
            // -- Static
            private static final JavaFunction INDEX = new Index();
            private static final JavaFunction NEW_INDEX = new NewIndex();
            // -- State
            private Map<Object, Object> map;

            // -- Construction

            /**
             * Creates a new instance.
             */
            public LuaMap(Map<Object, Object> map) {
                this.map = map;
            }

            // -- Properties

            /**
             * Returns the map.
             */
            public Map<Object, Object> getMap() {
                return map;
            }

            // -- JavaReflector methods
            @Override
            public JavaFunction getMetamethod(Metamethod metamethod) {
                switch (metamethod) {
                    case INDEX:
                        return INDEX;
                    case NEWINDEX:
                        return NEW_INDEX;
                    default:
                        return null;
                }
            }

            // -- TypedJavaObject methods
            @Override
            public Object getObject() {
                return map;
            }

            @Override
            public Class<?> getType() {
                return Map.class;
            }

            @Override
            public boolean isStrong() {
                return true;
            }

            // -- Member types

            /**
             * __index implementation for maps.
             */
            private static class Index extends JavaFunction {
                // -- JavaFunction methods
                @Override
                public void call(LuaState luaState, Object[] args) {
                    LuaMap luaMap = (LuaMap) args[0];
                    Object key = args[1];
                    LuaState.checkArg(key != null, "attempt to read map with %s accessor", toClassName(args[1]));
                    luaState.pushJavaObject(luaMap.getMap().get(key));
                }
            }

            /**
             * __newindex implementation for maps.
             */
            protected static class NewIndex extends JavaFunction {
                // -- JavaFunction methods
                @Override
                public void call(LuaState luaState, Object[] args) {
                    LuaMap luaMap = (LuaMap) args[0];
                    Object key = args[1];
                    LuaState.checkArg(key != null, "attempt to write map with %s accessor", toClassName(args[1]));
                    Object value = args[2];
                    if (value != null) {
                        luaMap.getMap().put(key, value);
                    } else {
                        luaMap.getMap().remove(key);
                    }
                }
            }
        }

        /**
         * Provides table-like access in Lua to a Java list.
         */
        protected static class LuaList extends JavaReflector implements TypedJavaObject {
            // -- Static
            private static final JavaFunction INDEX = new Index();
            private static final JavaFunction NEW_INDEX = new NewIndex();
            private static final JavaFunction LENGTH = new Length();
            // -- State
            private List<Object> list;

            // -- Construction

            /**
             * Creates a new instance.
             */
            public LuaList(List<Object> list) {
                this.list = list;
            }

            // -- Properties

            /**
             * Returns the map.
             */
            public List<Object> getList() {
                return list;
            }

            // -- JavaReflector methods
            @Override
            public JavaFunction getMetamethod(Metamethod metamethod) {
                switch (metamethod) {
                    case INDEX:
                        return INDEX;
                    case NEWINDEX:
                        return NEW_INDEX;
                    case LEN:
                        return LENGTH;
                    default:
                        return null;
                }
            }

            // -- TypedJavaObject methods
            @Override
            public Object getObject() {
                return list;
            }

            @Override
            public Class<?> getType() {
                return List.class;
            }

            @Override
            public boolean isStrong() {
                return true;
            }

            // -- Member types

            /**
             * __index implementation for lists.
             */
            private static class Index extends JavaFunction {
                // -- JavaFunction methods
                @Override
                public void call(LuaState luaState, Object[] args) {
                    LuaList luaList = (LuaList) args[0];
                    System.out.println(Arrays.toString(args));
                    LuaState.checkArg(args[1] instanceof Number, "attempt to read list with %s accessor", toClassName(args[1]));
                    int index = ((Number) args[1]).intValue();
                    luaState.pushJavaObject(luaList.getList().get(index - 1));
                }
            }

            /**
             * __newindex implementation for lists.
             */
            private static class NewIndex extends JavaFunction {
                // -- JavaFunction methods
                @Override
                public void call(LuaState luaState, Object[] args) {
                    LuaList luaList = (LuaList) args[0];
                    LuaState.checkArg(args[1] instanceof Number, "attempt to read list with %s accessor", toClassName(args[1]));
                    int index = ((Number) args[1]).intValue();
                    Object value = args[2];
                    if (value != null) {
                        int size = luaList.getList().size();
                        if (index - 1 != size) {
                            luaList.getList().set(index - 1, value);
                        } else {
                            luaList.getList().add(value);
                        }
                    } else {
                        luaList.getList().remove(index - 1);
                    }
                }
            }

            /**
             * __len implementation for lists.
             */
            private static class Length extends JavaFunction {
                // -- JavaFunction methods
                @Override
                public void call(LuaState luaState, Object[] args) {
                    LuaList luaList = (LuaList) args[0];
                    luaState.pushInteger(luaList.getList().size());
                }
            }
        }
    }

    /**
     * Provides an iterator for Iterable objects.
     */
    private static class Elements extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            Iterable<?> iterable = (Iterable) args[0];
            luaState.pushJavaObject(new ElementIterator(iterable.iterator()));
            luaState.pushJavaObject(iterable);
            luaState.pushNil();
        }

        @Override
        public String getName() {
            return "elements";
        }

        // -- Member types
        private static class ElementIterator extends JavaFunction {
            // -- State
            private Iterator<?> iterator;

            // -- Construction

            /**
             * Creates a new instance.
             */
            public ElementIterator(Iterator<?> iterator) {
                this.iterator = iterator;
            }

            // -- JavaFunction methods
            @Override
            public void call(LuaState luaState, Object[] args) {
                if (iterator.hasNext()) {
                    luaState.pushJavaObject(iterator.next());
                } else {
                    luaState.pushNil();
                }
            }
        }
    }

    /**
     * Provides an iterator for Java object fields.
     */
    private static class Fields extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            JavaFunction metamethod = luaState.getMetamethod(args[0], Metamethod.JAVAFIELDS);
            metamethod.call(luaState, args);
        }

        @Override
        public String getName() {
            return "fields";
        }
    }

    /**
     * Provides an iterator for Java methods.
     */
    private static class Methods extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            JavaFunction metamethod = luaState.getMetamethod(args[0], Metamethod.JAVAMETHODS);
            metamethod.call(luaState, args);
        }

        @Override
        public String getName() {
            return "methods";
        }
    }

    /**
     * Provides an iterator for Java object properties.
     */
    private static class Properties extends JavaFunction {
        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            JavaFunction metamethod = luaState.getMetamethod(args[0], Metamethod.JAVAPROPERTIES);
            metamethod.call(luaState, args);
        }

        @Override
        public String getName() {
            return "properties";
        }
    }
}
