package com.naef.jnlua;

import com.esotericsoftware.reflectasm.ClassAccess;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.naef.jnlua.LuaState.toClass;

/**
 * Created by Will on 2017/2/13.
 */
public final class Invoker extends JavaFunction {
    private static final class InvokerKey {
        private final Class<?> clz;
        private final String name;
        private final int hashCode;

        public InvokerKey(Class<?> clz, String name) {
            this.clz = clz;
            this.name = name;
            this.hashCode = clz.hashCode() * 31 + name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof InvokerKey)) return false;
            InvokerKey other = (InvokerKey) obj;
            return clz == other.clz && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    // ========================================================================
    // [Performance] Invoker Instance Cache and Pre-computed UTF-8 Byte Arrays
    // ========================================================================
    // Static cache: Reuses Invoker instances for same class+member combinations
    // - Key: (Class, member name) -> Value: Invoker instance
    // - Thread-safe using ConcurrentHashMap
    // - Eliminates repeated ClassAccess lookups and Invoker object creation
    private static final ConcurrentHashMap<InvokerKey, Invoker> INVOKERS = new ConcurrentHashMap<>();
    
    public final ClassAccess access;
    public final String className;
    
    // [Performance] Pre-computed UTF-8 byte array for class name
    // - Computed once in constructor (line 58)
    // - Eliminates repeated String.getBytes(UTF8) calls in pushMetaFunction()
    // - Complements LuaState.getCanonicalName() optimization
    public final byte[] classNameBytes;
    
    public final String attr;
    
    // [Performance] Pre-computed UTF-8 byte array for attribute name
    // - Computed once in constructor (line 61)
    // - Used in pushMetaFunction() for JNI boundary crossing
    // - Reduces encoding overhead for frequently accessed members
    public final byte[] attrBytes;
    public final String type;
    public final String name;
    private final int index;
    private final boolean isField;
    private boolean isPushed = false;
    private final boolean isArray;
    private final Integer[] candidates;

    public Invoker(ClassAccess access, String className, String name, String attr, String attrType, boolean isArray) {
        this.isMaintainTable = true;
        this.access = access;
        this.className = className;
        this.classNameBytes = className != null ? className.getBytes(LuaState.UTF8) : null;
        this.name = name;
        this.attr = attr;
        this.attrBytes = attr != null ? attr.getBytes(LuaState.UTF8) : null;
        this.type = attrType;
        this.isArray = isArray;
        this.isField = type.equals(ClassAccess.FIELD);
        Integer[] ids = null;
        if (isField)
            this.index = access.indexOfField(attr);
        else {
            ids = access.indexesOf(attr, type);
            this.index = ids.length == 1 ? ids[0] : -1;
            if (this.index > -1) ids = null;
        }
        this.candidates = ids;
        setName(name, isField ? "(Field)" : "(Method)");
    }

    public final Boolean isStatic() {
        if (type.equals(ClassAccess.FIELD)) {
            return Modifier.isStatic(access.classInfo.fieldModifiers[access.indexOfField(attr)]);
        } else if (type.equals(ClassAccess.METHOD)) {
            return Modifier.isStatic(access.classInfo.methodModifiers[access.indexOfMethod(attr)]);
        }
        return false;
    }

    public final void read(LuaState luaState, Object[] args) {
        // ====================================================================
        // [Performance] Push cached meta-function to Lua using pre-computed byte arrays
        // ====================================================================
        // - Uses classNameBytes and attrBytes (pre-computed in constructor)
        // - Avoids repeated String.getBytes(UTF8) encoding on every field/method access
        // - LuaState.ARRAY_NAME_BYTES is also pre-computed (static constant)
        // - isPushed flag prevents redundant pushes for the same field
        if ((!isField || !isPushed) && classNameBytes != null) {
            luaState.pushMetaFunction(classNameBytes, isArray ? LuaState.ARRAY_NAME_BYTES : attrBytes, this, (byte) (isField ? 3 : 2));
            isPushed = true;
        }
        if (isField) {
            call(luaState, args);
        } else if (className == null) {
            luaState.pushJavaObject(this);
        } /*else
            luaState.pushMetaFunction(className, name.substring(className.length() + 1), this, isField);*/
    }

    public final void write(LuaState luaState, Object[] args) {
        LuaState.checkArg(type.equals(ClassAccess.FIELD), "Attempt to override method %s", name);
        final int index = access.indexOfField(attr);
        final int last = args.length - 1;
        if (isTableArgs)
            args[last] = luaState.getConverter().convertLuaValue(luaState, last + 1, types[last], access.classInfo.fieldTypes[index]);
        access.set(args[0], index, args[last]);
    }

    @Override
    public final void call(LuaState luaState, Object[] args) {
        if (isField) {
            luaState.pushJavaObject(access.get(args[0], index));
            return;
        }
        int argCount = args.length;
        Object instance = argCount == 0 ? null : args[0];
        Object[] arg = args;
        final Class<?> clz = access.classInfo.baseClass;
        int startIndex = 0;
        if (instance != null && (clz == toClass(instance) || clz.getName().equals(String.valueOf(instance)))) {
            ++startIndex;
            arg = Arrays.copyOfRange(arg, startIndex, argCount);
        } else instance = null;
        if (!type.equals(ClassAccess.METHOD) && argCount > startIndex && args[startIndex] instanceof String && args[startIndex].equals(attr)) {
            ++startIndex;
            arg = Arrays.copyOfRange(arg, 1, argCount);
        }
        Class<?>[] argTypes = ClassAccess.args2Types(arg);
        if(isTableArgs) {
            for (int i = 0; i < argTypes.length; i++) {
                if (argTypes[i] == null && types[i] == LuaType.TABLE) {
                    argTypes[i] = AbstractMap.class;
                }
            }
        }

        final int index = this.index > -1 ? this.index : access.indexOfMethod(null, attr, candidates, argTypes);
        if (isTableArgs) {
            Class<?>[] clzz = type.equals(ClassAccess.METHOD) ? access.classInfo.methodParamTypes[index] : access.classInfo.constructorParamTypes[index];
            for (int i = 0; i < argTypes.length; i++) {
                if (types[i + startIndex] == LuaType.TABLE) {
                    //final int ref=((Double)args[i + startIndex]).intValue();
                    //luaState.rawGet(LuaState.REGISTRYINDEX,ref);
                    final Converter converter = luaState.getConverter();
                    if (List.class.isAssignableFrom(clzz[i]))
                        arg[i] = converter.convertLuaValue(luaState, i + 1 + startIndex, types[i + startIndex], List.class);
                    else if (clzz[i].isArray())
                        arg[i] = converter.convertLuaValue(luaState, i + 1 + startIndex, types[i + startIndex], clzz[i]);
                    else if (Map.class.isAssignableFrom(clzz[i]))
                        arg[i] = converter.convertLuaValue(luaState, i + 1 + startIndex, types[i + startIndex], Map.class);
                    else
                        arg[i] = converter.convertLuaValue(luaState, i + 1 + startIndex, types[i + startIndex], Object.class);
                    //luaState.unref(LuaState.REGISTRYINDEX,ref);
                }
            }
        }
        Object result;
        if (type.equals(ClassAccess.METHOD)) {
            result = access.invokeWithIndex(instance, index, arg);
            if (access.classInfo.returnTypes[index] == Void.TYPE) return;
        } else result = access.newInstanceWithIndex(index, arg);
        luaState.pushJavaObject(result);
    }

    public final static Invoker getInvoker(final Object... args) {
        if (args.length < 2 || args[0] == null || !(args[1] instanceof String)) return null;
        final Class<?> clz = toClass(args[0]);
        if (clz == null || clz.isArray()) return null;
        return INVOKERS.get(new InvokerKey(clz, (String) args[1]));
    }

    public final static Invoker get(final Class<?> clz, final String attr, final String prefix) {
        final String attrName = (prefix == null ? "" : prefix) + attr;
        InvokerKey key = new InvokerKey(clz, attrName);
        Invoker invoker = INVOKERS.get(key);
        if (invoker != null) return invoker;

        final String className = LuaState.toClassName(clz);
        String fullName = className + "." + attr;
        ClassAccess<?> access = ClassAccess.access(clz);
        String type = access.getNameType(attrName);
        if (type == null) return null;
        invoker = new Invoker(access, className, fullName, attr, type, clz.isArray());
        Invoker existing = INVOKERS.putIfAbsent(key, invoker);
        return existing != null ? existing : invoker;
    }

    @Override
    public final String toString() {
        return name + (type.equals(ClassAccess.FIELD) ? "" : "(...)");
    }
}
