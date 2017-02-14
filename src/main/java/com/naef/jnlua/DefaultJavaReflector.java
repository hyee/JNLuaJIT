/*
 * $Id: DefaultJavaReflector.java 174 2013-07-28 20:46:22Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

import com.esotericsoftware.reflectasm.ClassAccess;

import java.lang.reflect.Array;
import java.util.Iterator;

/**
 * Default implementation of the <code>JavaReflector</code> interface.
 */
public class DefaultJavaReflector implements JavaReflector {
    // -- Static
    private static final DefaultJavaReflector INSTANCE = new DefaultJavaReflector();
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
    private DefaultJavaReflector() {
    }

    static Class<?> toClass(Object object) {
        return object instanceof Class<?> ? (Class<?>) object : object.getClass();
    }

    // -- Construction

    /**
     * Returns the instance of this class.
     *
     * @return the instance
     */
    public static DefaultJavaReflector getInstance() {
        ClassAccess.IS_SINGLE_THREAD_MODE = true;
        return INSTANCE;
    }

    // -- JavaReflector methods
    @Override
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
        public int invoke(LuaState luaState) {
            // Get object and class
            Object object = luaState.toJavaObject(1, Object.class);
            Class<?> objectClass = toClass(object);

            // Handle arrays
            if (objectClass.isArray()) {
                if (!luaState.isNumber(2)) {
                    throw new LuaRuntimeException(String.format("attempt to read array with %s accessor", luaState.typeName(2)));
                }
                int index = luaState.toInteger(2);
                int length = Array.getLength(object);
                if (index < 1 || index > length) {
                    throw new LuaRuntimeException(String.format("attempt to read array of length %d at index %d", length, index));
                }
                luaState.pushJavaObject(Array.get(object, index - 1));
                return 1;
            }

            // Handle objects
            String key = luaState.toString(-1);
            if (key == null) {
                throw new LuaRuntimeException(String.format("attempt to read class %s with %s accessor", object.getClass().getCanonicalName(), luaState.typeName(-1)));
            }
            Invoker invoker = Invoker.get(objectClass, key, "");
            if (invoker == null) {
                throw new LuaRuntimeException(String.format("attempt to read class %s with accessor '%s' (undefined)", objectClass.getCanonicalName(), key));
            }
            return invoker.read(luaState, object);
        }
    }

    /**
     * <code>__newindex</code> metamethod implementation.
     */
    private class NewIndex implements JavaFunction {
        public int invoke(LuaState luaState) {
            // Get object and class
            Object object = luaState.toJavaObject(1, Object.class);
            Class<?> objectClass = toClass(object);

            // Handle arrays
            if (objectClass.isArray()) {
                if (!luaState.isNumber(2)) {
                    throw new LuaRuntimeException(String.format("attempt to write array with %s accessor", luaState.typeName(2)));
                }
                int index = luaState.toInteger(2);
                int length = Array.getLength(object);
                if (index < 1 || index > length) {
                    throw new LuaRuntimeException(String.format("attempt to write array of length %d at index %d", length, index));
                }
                Class<?> componentType = objectClass.getComponentType();
                if (!luaState.isJavaObject(3, componentType)) {
                    throw new LuaRuntimeException(String.format("attempt to write array of %s at index %d with %s value", componentType.getCanonicalName(), index, luaState.typeName(3)));
                }
                Object value = luaState.toJavaObject(3, componentType);
                Array.set(object, index - 1, value);
                return 0;
            }

            // Handle objects
            String key = luaState.toString(2);
            if (key == null) {
                throw new LuaRuntimeException(String.format("attempt to write class %s with %s accessor", object.getClass().getCanonicalName(), luaState.typeName(2)));
            }
            Invoker invoker = Invoker.get(objectClass, key, "");
            if (invoker == null) {
                throw new LuaRuntimeException(String.format("attempt to write class %s with accessor '%s' (undefined)", objectClass.getCanonicalName(), key));
            }
            return invoker.write(luaState, object);
        }
    }

    /**
     * <code>__len</code> metamethod implementation.
     */
    private class Length implements JavaFunction {
        @Override
        public int invoke(LuaState luaState) {
            Object object = luaState.toJavaObject(1, Object.class);
            if (object.getClass().isArray()) {
                luaState.pushInteger(Array.getLength(object));
                return 1;
            }
            luaState.pushInteger(0);
            return 1;
        }
    }

    /**
     * <code>__eq</code> metamethod implementation.
     */
    private class Equal implements JavaFunction {
        @Override
        public int invoke(LuaState luaState) {
            Object object1 = luaState.toJavaObject(1, Object.class);
            Object object2 = luaState.toJavaObject(2, Object.class);
            luaState.pushBoolean(object1 == object2 || object1 != null && object1.equals(object2));
            return 1;
        }
    }

    /**
     * <code>__lt</code> metamethod implementation.
     */
    private class LessThan implements JavaFunction {
        @SuppressWarnings("unchecked")
        @Override
        public int invoke(LuaState luaState) {
            if (!luaState.isJavaObject(1, Comparable.class)) {
                throw new LuaRuntimeException(String.format("class %s does not implement Comparable", luaState.typeName(1)));
            }
            Comparable<Object> comparable = luaState.toJavaObject(1, Comparable.class);
            Object object = luaState.toJavaObject(2, Object.class);
            luaState.pushBoolean(comparable.compareTo(object) < 0);
            return 1;
        }
    }

    /**
     * <code>__le</code> metamethod implementation.
     */
    private class LessThanOrEqual implements JavaFunction {
        @SuppressWarnings("unchecked")
        @Override
        public int invoke(LuaState luaState) {
            if (!luaState.isJavaObject(1, Comparable.class)) {
                throw new LuaRuntimeException(String.format("class %s does not implement Comparable", luaState.typeName(1)));
            }
            Comparable<Object> comparable = luaState.toJavaObject(1, Comparable.class);
            Object object = luaState.toJavaObject(2, Object.class);
            luaState.pushBoolean(comparable.compareTo(object) <= 0);
            return 1;
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
        public int invoke(LuaState luaState) {
            // Get object
            ClassAccess access = null;
            Object object = luaState.toJavaObject(1, Object.class);
            if (accessType != null)
                access = ClassAccess.access(toClass(object));
            // Create iterator
            luaState.pushJavaObject(new AccessorNext(access, accessType));
            luaState.pushJavaObject(object);
            luaState.pushNil();
            return 3;
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
            public int invoke(LuaState luaState) {
                if (iterator == null) return 0;
                while (true) {
                    if (!iterator.hasNext()) return 0;
                    String key = iterator.next();
                    char id = key.charAt(0);
                    if (id == 1 ^ accessType.equals(ClassAccess.FIELD)) continue;
                    key = key.substring(1);
                    luaState.pushString(key);
                    Invoker invoker = Invoker.get(this.access.classInfo.baseClass, key, Character.toString(id));
                    Object object = luaState.toJavaObject(1, Object.class);
                    invoker.read(luaState, object);
                    return 2;
                }
            }
        }
    }

    /**
     * <code>__tostring</code> metamethod implementation.
     */
    private class ToString implements JavaFunction {
        @Override
        public int invoke(LuaState luaState) {
            Object object = luaState.toJavaObject(1, Object.class);
            luaState.pushString(object != null ? object.toString() : "null");
            return 1;
        }
    }
}
