/*
 * $Id: DefaultJavaReflector.java 174 2013-07-28 20:46:22Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

import com.naef.jnlua.reflect.*;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Default implementation of the <code>JavaReflector</code> interface.
 */
public class DefaultJavaReflector implements JavaReflector {
    // -- Static
    private static final DefaultJavaReflector INSTANCE = new DefaultJavaReflector();

    // -- State
    private Map<Class<?>, Map<String, Accessor>> accessors = new HashMap<Class<?>, Map<String, Accessor>>();
    private ReadWriteLock accessorLock = new ReentrantReadWriteLock();
    private JavaFunction index = new Index();
    private JavaFunction newIndex = new NewIndex();
    private JavaFunction equal = new Equal();
    private JavaFunction length = new Length();
    private JavaFunction lessThan = new LessThan();
    private JavaFunction lessThanOrEqual = new LessThanOrEqual();
    private JavaFunction toString = new ToString();
    private JavaFunction javaFields = new AccessorPairs(FieldAccessor.class);
    private JavaFunction javaMethods = new AccessorPairs(InvocableAccessor.class);
    private JavaFunction javaProperties = new AccessorPairs(PropertyAccessor.class);

    // -- Static methods

    /**
     * Creates a new instances;
     */
    private DefaultJavaReflector() {
    }

    // -- Construction

    /**
     * Returns the instance of this class.
     *
     * @return the instance
     */
    public static DefaultJavaReflector getInstance() {
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

    // -- Private methods

    /**
     * Returns the accessors of an object.
     */
    private Map<String, Accessor> getObjectAccessors(Object object) {
        // Check cache
        Class<?> clazz = Accessor.getObjectClass(object);
        accessorLock.readLock().lock();
        try {
            Map<String, Accessor> result = accessors.get(clazz);
            if (result != null) {
                return result;
            }
        } finally {
            accessorLock.readLock().unlock();
        }

        // Fill in
        Map<String, Accessor> result = createClassAccessors(clazz);
        accessorLock.writeLock().lock();
        try {
            if (!accessors.containsKey(clazz)) {
                accessors.put(clazz, result);
            } else {
                result = accessors.get(clazz);
            }
        } finally {
            accessorLock.writeLock().unlock();
        }
        return result;
    }

    /**
     * Creates the accessors of a class.
     */
    private Map<String, Accessor> createClassAccessors(Class<?> clazz) {
        Map<String, Accessor> result = new HashMap<String, Accessor>();

        // Fields
        Field[] fields = clazz.getFields();
        for (int i = 0; i < fields.length; i++) {
            result.put(fields[i].getName(), new FieldAccessor(fields[i]));
        }

        // Methods
        Map<String, Map<List<Class<?>>, Invocable>> accessibleMethods = new HashMap<String, Map<List<Class<?>>, Invocable>>();
        Method[] methods = clazz.getMethods();
        for (int i = 0; i < methods.length; i++) {
            // Do not overwrite fields
            Method method = methods[i];
            if (result.containsKey(method.getName())) {
                continue;
            }

            // Attempt to find the method in a public class if the declaring
            // class is not public
            if (!Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                method = getPublicClassMethod(clazz, method.getName(), method.getParameterTypes());
                if (method == null) {
                    continue;
                }
            }

            // For each method name and parameter type list, keep
            // only the method declared by the most specific class
            Map<List<Class<?>>, Invocable> overloaded = accessibleMethods.get(method.getName());
            if (overloaded == null) {
                overloaded = new HashMap<List<Class<?>>, Invocable>();
                accessibleMethods.put(method.getName(), overloaded);
            }
            List<Class<?>> parameterTypes = Arrays.asList(method.getParameterTypes());
            Invocable currentInvocable = overloaded.get(parameterTypes);
            if (currentInvocable != null && method.getDeclaringClass().isAssignableFrom(currentInvocable.getDeclaringClass())) {
                continue;
            }

            overloaded.put(parameterTypes, new InvocableMethod(method));
        }
        for (Map.Entry<String, Map<List<Class<?>>, Invocable>> entry : accessibleMethods.entrySet()) {
            result.put(entry.getKey(), new InvocableAccessor(clazz, entry.getValue().values()));
        }

        // Constructors
        Constructor<?>[] constructors = clazz.getConstructors();
        List<Invocable> accessibleConstructors = new ArrayList<Invocable>(constructors.length);
        for (int i = 0; i < constructors.length; i++) {
            // Ignore constructor if the declaring class is not public
            if (!Modifier.isPublic(constructors[i].getDeclaringClass().getModifiers())) {
                continue;
            }
            accessibleConstructors.add(new InvocableConstructor(constructors[i]));
        }
        if (clazz.isInterface()) {
            accessibleConstructors.add(new InvocableProxy(clazz));
        }
        if (!accessibleConstructors.isEmpty()) {
            result.put("new", new InvocableAccessor(clazz, accessibleConstructors));
        }

        // Properties
        BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
        PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
        for (int i = 0; i < propertyDescriptors.length; i++) {
            // Do not overwrite fields or methods
            if (result.containsKey(propertyDescriptors[i].getName())) {
                continue;
            }

            // Attempt to find the read/write methods in a public class if the
            // declaring class is not public
            Method method = propertyDescriptors[i].getReadMethod();
            if (method != null && !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                method = getPublicClassMethod(clazz, method.getName(), method.getParameterTypes());
                try {
                    propertyDescriptors[i].setReadMethod(method);
                } catch (IntrospectionException e) {
                }
            }
            method = propertyDescriptors[i].getWriteMethod();
            if (method != null && !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                method = getInterfaceMethod(clazz, method.getName(), method.getParameterTypes());
                try {
                    propertyDescriptors[i].setWriteMethod(method);
                } catch (IntrospectionException e) {
                }
            }

            // Do not process properties without a read and a write method
            if (propertyDescriptors[i].getReadMethod() == null && propertyDescriptors[i].getWriteMethod() == null) {
                continue;
            }
            result.put(propertyDescriptors[i].getName(), new PropertyAccessor(clazz, propertyDescriptors[i]));
        }
        return result;
    }

    /**
     * Returns a public class method matching a method name and parameter list.
     * The public class can be a superclass or interface.
     */
    private Method getPublicClassMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        Method method = getPublicSuperclassMethod(clazz, methodName, parameterTypes);
        if (method != null) {
            return method;
        }
        return getInterfaceMethod(clazz, methodName, parameterTypes);
    }

    /**
     * Returns a public superclass method matching a method name and parameter
     * list.
     */
    private Method getPublicSuperclassMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {

        while ((clazz=clazz.getSuperclass()) != null) {
            // Process public superclasses only
            if (Modifier.isPublic(clazz.getModifiers())) {
                // Find method in superclass
                try {
                    Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
                    if (Modifier.isPublic(method.getModifiers())) {
                        return method;
                    }
                } catch (NoSuchMethodException e) {
                    // Not found
                }
            }
        }

        // Not found
        return null;
    }

    /**
     * Returns an interface method matching a method name and parameter list.
     */
    private Method getInterfaceMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        do {
            // Get interfaces
            Class<?>[] interfaces = clazz.getInterfaces();
            for (int i = 0; i < interfaces.length; i++) {
                // Ignore non-public interfaces
                if (!Modifier.isPublic(interfaces[i].getModifiers())) {
                    continue;
                }

                // Find method in the current interface
                try {
                    return interfaces[i].getDeclaredMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException e) {
                    // Not found
                }

                // Check superinterfaces
                Method method = getInterfaceMethod(interfaces[i], methodName, parameterTypes);
                if (method != null) {
                    return method;
                }
            }

            // Check superclass
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        // Not found
        return null;
    }



    // -- Nested types

    /**
     * <code>__index</code> metamethod implementation.
     */
    private class Index implements JavaFunction {
        public int invoke(LuaState luaState) {
            // Get object and class
            Object object = luaState.toJavaObject(1, Object.class);
            Class<?> objectClass = Accessor.getObjectClass(object);

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
            Map<String, Accessor> objectAccessors = getObjectAccessors(object);
            String key = luaState.toString(-1);
            if (key == null) {
                throw new LuaRuntimeException(String.format("attempt to read class %s with %s accessor", object.getClass().getCanonicalName(), luaState.typeName(-1)));
            }
            Accessor accessor = objectAccessors.get(key);
            if (accessor == null) {
                throw new LuaRuntimeException(String.format("attempt to read class %s with accessor '%s' (undefined)", objectClass.getCanonicalName(), key));
            }
            accessor.read(luaState, object);
            return 1;
        }
    }

    /**
     * <code>__newindex</code> metamethod implementation.
     */
    private class NewIndex implements JavaFunction {
        public int invoke(LuaState luaState) {
            // Get object and class
            Object object = luaState.toJavaObject(1, Object.class);
            Class<?> objectClass = Accessor.getObjectClass(object);

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
            Map<String, Accessor> objectAccessors = getObjectAccessors(object);
            String key = luaState.toString(2);
            if (key == null) {
                throw new LuaRuntimeException(String.format("attempt to write class %s with %s accessor", object.getClass().getCanonicalName(), luaState.typeName(2)));
            }
            Accessor accessor = objectAccessors.get(key);
            if (accessor == null) {
                throw new LuaRuntimeException(String.format("attempt to write class %s with accessor '%s' (undefined)", objectClass.getCanonicalName(), key));
            }
            accessor.write(luaState, object);
            return 0;
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
        private Class<?> accessorClass;

        // -- Construction

        /**
         * Creates a new instance.
         */
        public AccessorPairs(Class<?> accessorClass) {
            this.accessorClass = accessorClass;
        }

        // -- JavaFunction methods
        @Override
        public int invoke(LuaState luaState) {
            // Get object
            Object object = luaState.toJavaObject(1, Object.class);
            Class<?> objectClass = Accessor.getObjectClass(object);

            // Create iterator
            Map<String, Accessor> objectAccessors = getObjectAccessors(object);
            Iterator<Entry<String, Accessor>> iterator = objectAccessors.entrySet().iterator();
            luaState.pushJavaObject(new AccessorNext(iterator, objectClass == object));
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
            private Iterator<Entry<String, Accessor>> iterator;
            private boolean isStatic;

            // -- Construction

            /**
             * Creates a new instance.
             */
            public AccessorNext(Iterator<Entry<String, Accessor>> iterator, boolean isStatic) {
                this.iterator = iterator;
                this.isStatic = isStatic;
            }

            // -- JavaFunction methods
            @Override
            public int invoke(LuaState luaState) {
                while (iterator.hasNext()) {
                    Entry<String, Accessor> entry = iterator.next();
                    Accessor accessor = entry.getValue();

                    // Filter by accessor class
                    if (accessor.getClass() != accessorClass) {
                        continue;
                    }

                    // Filter by non-static, static
                    if (isStatic) {
                        if (!accessor.isStatic()) {
                            continue;
                        }
                    } else {
                        if (!accessor.isNotStatic()) {
                            continue;
                        }
                    }

                    // Push match
                    luaState.pushString(entry.getKey());
                    Object object = luaState.toJavaObject(1, Object.class);
                    accessor.read(luaState, object);
                    return 2;
                }

                // End iteration
                return 0;
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
