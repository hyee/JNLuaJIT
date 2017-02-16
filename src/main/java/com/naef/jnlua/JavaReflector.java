/*
 * $Id: DefaultJavaReflector.java 174 2013-07-28 20:46:22Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

import com.esotericsoftware.reflectasm.ClassAccess;

import java.lang.reflect.Array;
import java.util.Iterator;

import static com.esotericsoftware.reflectasm.util.NumberUtils.convert;

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
    private JavaFunction javaFields = new AccessorPairs(ClassAccess.FIELD);
    private JavaFunction javaMethods = new AccessorPairs(ClassAccess.METHOD);
    private JavaFunction javaProperties = new AccessorPairs(null);

    // -- Static methods

    /**
     * Creates a new instances;
     */
    JavaReflector() {
    }

    public static Class<?> toClass(Object object) {
        return object == null ? null : object instanceof Class<?> ? (Class<?>) object : object.getClass();
    }

    public static String toClassName(Object object) {
        Class clz = toClass(object);
        return clz == null ? null : clz.getCanonicalName();
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
        IPAIRS,
        /**
         * <code>__pairs</code> metamethod.
         */
        PAIRS,
        /**
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
    private class Index implements JavaFunction {
        @Override
        public void call(LuaState luaState, Object[] args) {
            // Get object and class
            Object object = args[0];
            Class<?> objectClass = toClass(object);

            // Handle arrays
            if (objectClass.isArray()) {
                luaState.checkArg(args[1] instanceof Number, "attempt to read array with %s accessor", toClassName(args[0]));
                int index = ((Number) args[1]).intValue();
                int length = Array.getLength(object);
                luaState.checkArg(index >= 1 && index <= length, "attempt to read array of length %d at index %d", length, index);
                luaState.pushJavaObject(Array.get(object, index - 1));
            }
            // Handle objects
            String key = String.valueOf(args[args.length - 1]);
            luaState.checkArg(key != null, "attempt to read class %s with %s accessor", toClassName(object), toClassName(args[args.length - 1]));
            Invoker invoker = Invoker.get(objectClass, key, "");
            luaState.checkArg(invoker != null, "attempt to read class %s with accessor '%s' (undefined)", toClassName(object), key);
            invoker.read(luaState, args);
        }
    }

    /**
     * <code>__newindex</code> metamethod implementation.
     */
    private class NewIndex implements JavaFunction {
        @Override
        public void call(LuaState luaState, Object[] args) {
            // Get object and class
            Object object = args[0];
            Class<?> objectClass = toClass(object);

            // Handle arrays
            if (objectClass.isArray()) {
                luaState.checkArg(args[1] instanceof Number, "attempt to write array with %s accessor", toClassName(args[1]));
                int index = ((Number) args[1]).intValue();
                int length = Array.getLength(object);
                luaState.checkArg(index >= 1 && index <= length, "attempt to write array of length %d at index %d", length, index);

                Class<?> componentType = objectClass.getComponentType();
                luaState.checkArg(toClass(args[2]) == componentType, "attempt to write array of %s at index %d with %s value", toClassName(componentType), index, toClassName(args[2]));
                Object value = convert(args[2], componentType);
                Array.set(object, index - 1, value);
            }

            // Handle objects
            String key = (String) args[1];
            luaState.checkArg(key != null, "attempt to read class %s with %s accessor", toClassName(object), toClassName(args[args.length - 1]));
            Invoker invoker = Invoker.get(objectClass, key, "");
            luaState.checkArg(invoker != null, "attempt to read class %s with accessor '%s' (undefined)", toClassName(object), key);
            invoker.write(luaState, args);
        }
    }

    /**
     * <code>__len</code> metamethod implementation.
     */
    private class Length implements JavaFunction {
        @Override
        public void call(LuaState luaState, Object[] args) {
            Object object = args[0];
            if (object.getClass().isArray())
                luaState.pushInteger(Array.getLength(object));
            else luaState.pushInteger(0);
        }
    }

    /**
     * <code>__eq</code> metamethod implementation.
     */
    private class Equal implements JavaFunction {
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
    private class LessThan implements JavaFunction {
        @SuppressWarnings("unchecked")
        @Override
        public void call(LuaState luaState, Object[] args) {
            luaState.checkArg(args[0] instanceof Comparable, "class %s does not implement Comparable", toClassName(args[0]));
            Comparable<Object> comparable = convert(args[0], Comparable.class);
            Object object = args[1];
            luaState.pushBoolean(comparable.compareTo(object) < 0);
        }
    }

    /**
     * <code>__le</code> metamethod implementation.
     */
    private class LessThanOrEqual implements JavaFunction {
        @SuppressWarnings("unchecked")
        @Override
        public void call(LuaState luaState, Object[] args) {
            luaState.checkArg(args[0] instanceof Comparable, "class %s does not implement Comparable", toClassName(args[0]));
            Comparable<Object> comparable = convert(args[0], Comparable.class);
            Object object = args[1];
            luaState.pushBoolean(comparable.compareTo(object) <= 0);
        }
    }

    /**
     * Provides an iterator for accessors.
     */
    private class AccessorPairs implements JavaFunction {
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
            if (accessType != null)
                access = ClassAccess.access(toClass(object));
            // Create iterator
            luaState.pushJavaObject(new AccessorNext(access, accessType));
            luaState.pushJavaObject(object);
            luaState.pushNil();
        }

        // -- Member types

        /**
         * Provides the next function for iterating accessors.
         */
        private class AccessorNext implements JavaFunction {
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
                    if (id == 1 ^ accessType.equals(ClassAccess.FIELD)) continue;
                    key = key.substring(1);
                    luaState.pushString(key);
                    Invoker invoker = Invoker.get(this.access.classInfo.baseClass, key, Character.toString(id));
                    invoker.read(luaState, args);
                    return;
                }
            }
        }
    }

    /**
     * <code>__tostring</code> metamethod implementation.
     */
    private class ToString implements JavaFunction {
        @Override
        public void call(LuaState luaState, Object[] args) {
            Object object = args[0];
            luaState.pushString(object != null ? object.toString() : "null");
            return;
        }
    }
}
