package com.naef.jnlua;

import com.esotericsoftware.reflectasm.ClassAccess;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.naef.jnlua.LuaState.toClass;

/**
 * Created by Will on 2017/2/13.
 */
public final class Invoker extends JavaFunction {
    public static HashMap<String, Invoker> invokers = new HashMap();
    public final ClassAccess access;
    public final String attr;
    public final String type;
    public final String name;

    public Invoker(ClassAccess access, String name, String attr, String attrType) {
        this.access = access;
        this.name = name;
        this.attr = attr;
        this.type = attrType;
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
        if (type.equals(ClassAccess.FIELD)) {
            final int index = access.indexOfField(attr);
            luaState.pushJavaObject(access.get(args[0], index));
        } else luaState.pushJavaFunction(this);
    }

    public final void write(LuaState luaState, Object[] args) {
        LuaState.checkArg(type.equals(ClassAccess.FIELD), "Attempt to override method %s", name);
        final int index = access.indexOfField(attr);
        final int last = args.length - 1;
        if (isTableArgs)
            args[last] = luaState.getConverter().convertLuaValue(luaState, last, access.classInfo.fieldTypes[index]);
        access.set(args[0], index, args[last]);
    }

    @Override
    public final void call(LuaState luaState, Object[] args) {
        //System.out.println(name+":"+(type.equals(ClassAccess.METHOD)?"CALL":"INDEX"));
        LuaState.checkArg(!type.equals(ClassAccess.FIELD), "Attempt to call field %s", name);
        int argCount = args.length;
        Object instance = argCount == 0 ? null : args[0];
        Object[] arg = args;
        final Class clz = access.classInfo.baseClass;
        int startIndex = 0;
        if (instance != null && (clz == toClass(instance) || clz.getName().equals(String.valueOf(instance)))) {
            ++startIndex;
            arg = Arrays.copyOfRange(arg, startIndex, argCount);
        } else instance = null;
        if (!type.equals(ClassAccess.METHOD) && argCount > startIndex && args[startIndex] instanceof String && args[startIndex].equals(attr)) {
            ++startIndex;
            arg = Arrays.copyOfRange(arg, 1, argCount);
        }
        Class[] argTypes = ClassAccess.args2Types(arg);
        if (isTableArgs) {
            for (int i = 0; i < argTypes.length; i++) {
                if (luaState.type(i + 1 + startIndex) == LuaType.TABLE) argTypes[i] = null;
            }
        }
        final int index = access.indexOfMethod(null, attr, argTypes);
        if (isTableArgs) {
            Class[] clzz = type.equals(ClassAccess.METHOD) ? access.classInfo.methodParamTypes[index] : access.classInfo.constructorParamTypes[index];
            for (int i = 0; i < argTypes.length; i++) {
                if (luaState.type(i + 1 + startIndex) == LuaType.TABLE) {
                    if (List.class.isAssignableFrom(clzz[i]))
                        arg[i] = luaState.getConverter().convertLuaValue(luaState, i + 1 + startIndex, List.class);
                    else if (clzz[i].isArray())
                        arg[i] = luaState.getConverter().convertLuaValue(luaState, i + 1 + startIndex, clzz[i]);
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
        final Class clz = toClass(args[0]);
        if (clz == null || clz.isArray()) return null;
        return invokers.get(clz.getCanonicalName() + "." + args[1]);
    }

    public final static Invoker get(final Class clz, final String attr, final String prefix) {
        String key = clz.getCanonicalName() + "." + attr;
        ClassAccess access = ClassAccess.access(clz);
        String type = access.getNameType((prefix == null ? "" : prefix) + attr);
        if (type == null) return null;
        Invoker invoker = new Invoker(access, key, attr, type);
        invokers.put(key, invoker);
        return invoker;
    }

    @Override
    public final String toString() {
        return name + (type.equals(ClassAccess.FIELD) ? "" : "(...)");
    }
}
