/*
 * $Id: DefaultJavaReflector.java 174 2013-07-28 20:46:22Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

import com.esotericsoftware.reflectasm.ClassAccess;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import static com.esotericsoftware.reflectasm.util.NumberUtils.convert;
import static com.esotericsoftware.reflectasm.util.NumberUtils.getDistance;
import static com.naef.jnlua.LuaState.toClass;
import static com.naef.jnlua.LuaState.toClassName;

/**
 * Default implementation of the <code>JavaReflector</code> interface.
 */
public class JavaReflector {
    // -- Static
    private static final JavaReflector INSTANCE = new JavaReflector();
    // -- State
    private JavaFunction index = new Index();
    private JavaFunction newIndex = new NewIndex();
    private JavaFunction equal = new Equal();
    private JavaFunction length = new Length();
    private JavaFunction lessThan = new LessThan();
    private JavaFunction lessThanOrEqual = new LessThanOrEqual();
    private JavaFunction toString = new ToString();
    private JavaFunction pairs = new Pairs();
    private JavaFunction ipairs = new IPairs();
    private JavaFunction javaFields = new AccessorPairs(ClassAccess.FIELD);
    private JavaFunction javaMethods = new AccessorPairs(ClassAccess.METHOD);
    private JavaFunction javaProperties = new AccessorPairs(null);

    // -- Static methods

    /**
     * Creates a new instances;
     */
    JavaReflector() {
    }

    // -- Construction

    /**
     * Returns the instance of this class.
     *
     * @return the instance
     */
    public static JavaReflector getInstance() {
        ClassAccess.IS_SINGLE_THREAD_MODE = true;
        return INSTANCE;
    }

    public enum Metamethod {
        /**
         * <code>__index</code> metamethod.
         */
        INDEX, /**
         * <code>__newindex</code> metamethod.
         */
        NEWINDEX, /**
         * <code>__len</code> metamethod.
         */
        LEN, /**
         * <code>__eq</code> metamethod.
         */
        EQ, /**
         * <code>__lt</code> metamethod.
         */
        LT, /**
         * <code>__le</code> metamethod.
         */
        LE, /**
         * <code>__unm</code> metamethod.
         */
        UNM, /**
         * <code>__add</code> metamethod.
         */
        ADD, /**
         * <code>__sub</code> metamethod.
         */
        SUB, /**
         * <code>__mul</code> metamethod.
         */
        MUL, /**
         * <code>__div</code> metamethod.
         */
        DIV, /**
         * <code>__mod</code> metamethod.
         */
        MOD, /**
         * <code>__pow</code> metamethod.
         */
        POW, /**
         * <code>__concat</code> metamethod.
         */
        CONCAT, /**
         * <code>__call</code> metamethod.
         */
        CALL, /**
         * <code>__ipairs</code> metamethod.
         */
        IPAIRS, /**
         * <code>__pairs</code> metamethod.
         */
        PAIRS, /**
         * <code>__tostring</code> metamethod.
         */
        TOSTRING, /**
         * <code>__javafields</code> metamethod.
         */
        JAVAFIELDS, /**
         * <code>__javamethods</code> metamethod.
         */
        JAVAMETHODS, /**
         * <code>__javaproperties</code> metamethod.
         */
        JAVAPROPERTIES;

        // -- Operations

        /**
         * Returns the Lua metamethod name.
         *
         * @return the metamethod name
         */
        public String getMetamethodName() {
            return "__" + toString().toLowerCase();
        }
    }

    // -- JavaReflector methods
    public JavaFunction getMetamethod(Metamethod metamethod) {
        switch (metamethod) {
            case INDEX:
                return index;
            case NEWINDEX:
                return newIndex;
            case EQ:
                return equal;
            case LEN:
                return length;
            case LT:
                return lessThan;
            case LE:
                return lessThanOrEqual;
            case IPAIRS:
                return ipairs;
            case PAIRS:
                return pairs;
            case TOSTRING:
                return toString;
            case JAVAFIELDS:
                return javaFields;
            case JAVAMETHODS:
                return javaMethods;
            case JAVAPROPERTIES:
                return javaProperties;
            default:
                return null;
        }
    }

    /**
     * <code>__index</code> metamethod implementation.
     */
    private class Index extends JavaFunction {
        @Override
        public void call(LuaState luaState, Object[] args) {
            // Get object and class
            Object object = args[0];
            Class<?> objectClass = toClass(object);

            // Handle arrays
            if (objectClass.isArray()) {
                LuaState.checkArg(args[1] instanceof Number, "attempt to read array with %s accessor", toClassName(args[0]));
                int index = ((Number) args[1]).intValue();
                int length = Array.getLength(object);
                LuaState.checkArg(index >= 1 && index <= length, "attempt to read array of length %d at index %d", length, index);
                luaState.pushJavaObject(Array.get(object, index - 1));
                return;
            }
            // Handle objects
            String key = String.valueOf(args[args.length - 1]);
            LuaState.checkArg(key != null, "attempt to read class '%s' with '%s' accessor", toClassName(object), toClassName(args[args.length - 1]));
            Invoker invoker = Invoker.get(objectClass, key, "");
            LuaState.checkArg(invoker != null, "attempt to read class '%s' with accessor '%s' (undefined)", toClassName(object), key);
            //luaState.setClassMetaField(object,key,invoker);
            invoker.read(luaState, args);
        }
    }

    /**
     * <code>__newindex</code> metamethod implementation.
     */
    private class NewIndex extends JavaFunction {
        @Override
        public void call(LuaState luaState, Object[] args) {
            // Get object and class
            Object object = args[0];
            Class<?> objectClass = toClass(object);

            // Handle arrays
            if (objectClass.isArray()) {
                LuaState.checkArg(args[1] instanceof Number, "attempt to write array with %s accessor", toClassName(args[1]));
                int index = ((Number) args[1]).intValue();
                int length = Array.getLength(object);
                LuaState.checkArg(index >= 1 && index <= length, "attempt to write array of length %d at index %d", length, index);

                Class<?> componentType = objectClass.getComponentType();
                LuaState.checkArg(getDistance(toClass(args[2]), componentType) > 0, "attempt to write array of %s at index %d with %s value", toClassName(componentType), index, toClassName(args[2]));
                Object value = convert(args[2], componentType);
                Array.set(object, index - 1, value);
                return;
            }

            // Handle objects
            String key = String.valueOf(args[1]);
            LuaState.checkArg(key != null, "attempt to read class %s with %s accessor", toClassName(object), toClassName(args[args.length - 1]));
            Invoker invoker = Invoker.get(objectClass, key, "");
            LuaState.checkArg(invoker != null, "attempt to read class %s with accessor '%s' (undefined)", toClassName(object), key);
            invoker.write(luaState, args);
        }
    }

    /**
     * <code>__len</code> metamethod implementation.
     */
    private class Length extends JavaFunction {
        @Override
        public void call(LuaState luaState, Object[] args) {
            Object object = args[0];
            if (object.getClass().isArray()) luaState.pushInteger(Array.getLength(object));
            else luaState.pushInteger(0);
        }
    }

    /**
     * <code>__eq</code> metamethod implementation.
     */
    private class Equal extends JavaFunction {
        @Override
        public void call(LuaState luaState, Object[] args) {
            Object object1 = args[0];
            Object object2 = args[1];
            luaState.pushBoolean(object1 == object2 || object1 != null && object1.equals(object2));
        }
    }

    /**
     * <code>__lt</code> metamethod implementation.
     */
    private class LessThan extends JavaFunction {
        @SuppressWarnings("unchecked")
        @Override
        public void call(LuaState luaState, Object[] args) {
            LuaState.checkArg(args[0] instanceof Comparable, "class %s does not implement Comparable", toClassName(args[0]));
            Comparable<Object> comparable = convert(args[0], Comparable.class);
            Object object = args[1];
            luaState.pushBoolean(comparable.compareTo(object) < 0);
        }
    }

    /**
     * <code>__le</code> metamethod implementation.
     */
    private class LessThanOrEqual extends JavaFunction {
        @SuppressWarnings("unchecked")
        @Override
        public void call(LuaState luaState, Object[] args) {
            LuaState.checkArg(args[0] instanceof Comparable, "class %s does not implement Comparable", toClassName(args[0]));
            Comparable<Object> comparable = convert(args[0], Comparable.class);
            Object object = args[1];
            luaState.pushBoolean(comparable.compareTo(object) <= 0);
        }
    }

    /**
     * Provides an iterator for maps. For <code>NavigableMap</code> objects, the
     * function returns a stateless iterator which allows concurrent
     * modifications to the map. For other maps, the function returns an
     * iterator based on <code>Iterator</code> which does not support concurrent
     * modifications.
     */
    /**
     * Provides an iterator for lists and arrays.
     */
    private static class IPairs extends JavaFunction {
        // -- Static
        private final JavaFunction listNext = new ListNext();
        private final JavaFunction arrayNext = new ArrayNext();

        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            if (args[0] instanceof List) {
                luaState.pushJavaFunction(listNext);
            } else if (args[0] instanceof JavaModule.ToTable.LuaList) {
                luaState.pushJavaFunction(listNext);
            } else {
                LuaState.checkArg(toClass(args[0]).isArray(), "expected list or array, got %s", toClassName(args[0]));
                luaState.pushJavaFunction(arrayNext);
            }
            luaState.pushJavaObject(args[0]);
            luaState.pushInteger(0);
        }

        @Override
        public String getName() {
            return "ipairs";
        }

        /**
         * Provides a stateless iterator function for lists.
         */
        private static class ListNext extends JavaFunction {
            @Override
            public void call(LuaState luaState, Object[] args) {

                List<?> list;
                if (args[0] instanceof JavaModule.ToTable.LuaList)
                    list = ((JavaModule.ToTable.LuaList) args[0]).getList();
                else list = (List) args[0];
                int size = list.size();
                int index = ((Number) args[1]).intValue();
                index++;
                if (index >= 1 && index <= size) {
                    luaState.pushInteger(index);
                    luaState.pushJavaObject(list.get(index - 1));
                } else {
                    luaState.pushNil();
                }
            }
        }

        /**
         * Provides a stateless iterator function for arrays.
         */
        private static class ArrayNext extends JavaFunction {
            @Override
            public void call(LuaState luaState, Object[] args) {
                Object array = args[0];
                int length = Array.getLength(array);
                int index = ((Number) args[1]).intValue();
                index++;
                if (index >= 1 && index <= length) {
                    luaState.pushInteger(index);
                    luaState.pushJavaObject(Array.get(array, index - 1));
                } else {
                    luaState.pushNil();
                }
            }
        }
    }

    /**
     * Provides an iterator for maps. For <code>NavigableMap</code> objects, the
     * function returns a stateless iterator which allows concurrent
     * modifications to the map. For other maps, the function returns an
     * iterator based on <code>Iterator</code> which does not support concurrent
     * modifications.
     */
    private static class Pairs extends JavaFunction {
        // -- Static
        private final JavaFunction navigableMapNext = new NavigableMapNext();

        // -- JavaFunction methods
        @SuppressWarnings("unchecked")
        @Override
        public void call(LuaState luaState, Object[] args) {
            Map<Object, Object> map = null;
            if (args[0] instanceof JavaModule.ToTable.LuaMap) map = ((JavaModule.ToTable.LuaMap) args[0]).getMap();
            else if (args[0] instanceof Map) map = (Map) args[0];
            else {
                new AccessorPairs("alll").call(luaState, args);
                return;
            }
            if (map instanceof NavigableMap) {
                luaState.pushJavaFunction(navigableMapNext);
            } else {
                luaState.pushJavaFunction(new MapNext(map.entrySet().iterator()));
            }
            luaState.pushJavaObject(map);
            luaState.pushNil();
        }

        @Override
        public String getName() {
            return "pairs";
        }

        /**
         * Provides a stateful iterator function for maps.
         */
        private static class MapNext extends JavaFunction {
            // -- State
            private Iterator<Map.Entry<Object, Object>> iterator;

            // -- Construction

            /**
             * Creates a new instance.
             */
            public MapNext(Iterator<Map.Entry<Object, Object>> iterator) {
                this.iterator = iterator;
            }

            // -- JavaFunction methods
            @Override
            public void call(LuaState luaState, Object[] args) {
                if (iterator.hasNext()) {
                    Map.Entry<Object, Object> entry = iterator.next();
                    luaState.pushJavaObject(entry.getKey());
                    luaState.pushJavaObject(entry.getValue());
                } else {
                    luaState.pushNil();
                }
            }
        }

        /**
         * Provides a stateless iterator function for navigable maps.
         */
        private static class NavigableMapNext extends JavaFunction {
            // -- JavaFunction methods
            @SuppressWarnings("unchecked")
            @Override
            public void call(LuaState luaState, Object[] args) {
                NavigableMap<Object, Object> navigableMap = (NavigableMap) args[0];
                Object key = args[1];
                Map.Entry<Object, Object> entry;
                if (key != null) {
                    entry = navigableMap.higherEntry(key);
                } else {
                    entry = navigableMap.firstEntry();
                }
                if (entry != null) {
                    luaState.pushJavaObject(entry.getKey());
                    luaState.pushJavaObject(entry.getValue());
                } else {
                    luaState.pushNil();
                }
            }
        }
    }

    /**
     * Provides an iterator for accessors.
     */
    private static class AccessorPairs extends JavaFunction {
        // -- State
        String accessType;

        // -- Construction

        /**
         * Creates a new instance.
         */
        public AccessorPairs(String accessType) {
            this.accessType = accessType;
        }

        // -- JavaFunction methods
        @Override
        public void call(LuaState luaState, Object[] args) {
            // Get object
            ClassAccess access = null;
            Object object = args[0];
            if (accessType != null) access = ClassAccess.access(toClass(object));
            // Create iterator
            luaState.pushJavaObject(new AccessorNext(access, accessType));
            luaState.pushJavaObject(object);
            luaState.pushNil();
        }

        // -- Member types

        /**
         * Provides the next function for iterating accessors.
         */
        private static class AccessorNext extends JavaFunction {
            // -- State
            ClassAccess access;
            String accessType;
            Iterator<String> iterator = null;

            // -- Construction

            /**
             * Creates a new instance.
             */
            public AccessorNext(ClassAccess access, String accessType) {
                this.access = access;
                this.accessType = accessType;
                if (access != null) iterator = access.classInfo.attrIndex.keySet().iterator();
            }

            // -- JavaFunction methods
            @Override
            public void call(LuaState luaState, Object[] args) {
                if (iterator == null) return;
                while (true) {
                    if (!iterator.hasNext()) return;
                    String key = iterator.next();
                    char id = key.charAt(0);
                    if ((id == 1 ^ accessType.equals(ClassAccess.FIELD)) && !accessType.equals("all")) continue;
                    key = key.substring(1);
                    Invoker invoker = Invoker.get(this.access.classInfo.baseClass, key, Character.toString(id));
                    if (invoker == null || (args[0] instanceof Class) && !invoker.isStatic()) continue;
                    luaState.pushString(key);
                    invoker.read(luaState, args);
                    return;
                }
            }
        }
    }

    /**
     * <code>__tostring</code> metamethod implementation.
     */
    private class ToString extends JavaFunction {
        @Override
        public void call(LuaState luaState, Object[] args) {
            Object object = args[0];
            luaState.pushString(object != null ? object.toString() : "null");
        }
    }
}
