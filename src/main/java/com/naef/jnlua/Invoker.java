package com.naef.jnlua;

import com.esotericsoftware.reflectasm.ClassAccess;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static com.naef.jnlua.LuaState.toClass;

/**
 * Created by Will on 2017/2/13.
 */
public final class Invoker extends JavaFunction {
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
            int index = access.indexOfField(attr);
            luaState.pushJavaObject(access.get(Modifier.isStatic(access.classInfo.fieldModifiers[index]) ? null : args[0], index));
        } else luaState.pushJavaFunction(this);
    }

    public final void write(LuaState luaState, Object[] args) {
        LuaState.checkArg(type.equals(ClassAccess.FIELD), "Attempt to override method %s", name);
        int index = access.indexOfField(attr);
        access.set(Modifier.isStatic(access.classInfo.fieldModifiers[index]) ? null : args[0], index, args[args.length - 1]);
    }

    @Override
    public final void call(LuaState luaState, Object[] args) {
        LuaState.checkArg(!type.equals(ClassAccess.FIELD), "Attempt to call field %s", name);

        Object instance = args[0];
        int argCount = args.length;
        Object[] arg = args;
        final Class clz = access.classInfo.baseClass;
        if (instance != null && (clz == toClass(instance) || clz.getName().equals(String.valueOf(instance))))
            arg = Arrays.copyOfRange(arg, 1, argCount);
        else instance = null;
        Object result;
        if (type.equals(ClassAccess.METHOD)) {
            final int index = access.indexOfMethod(null, attr, ClassAccess.args2Types(arg));
            result = access.invokeWithIndex(Modifier.isStatic(access.classInfo.methodModifiers[index]) ? null : instance, index, arg);
            if (access.classInfo.returnTypes[index] == Void.TYPE) return;
        } else result = access.newInstance(arg);
        luaState.pushJavaObject(result);
    }

    public final static Invoker get(final Class clz, final String attr, final String prefix) {
        Invoker invoker = (Invoker) ClassAccess.readCache(clz, attr);
        if (invoker == null) {
            String key = clz.getCanonicalName().trim() + "." + attr;
            ClassAccess access = ClassAccess.access(clz);
            String type = access.getNameType((prefix == null ? "" : prefix) + attr);
            if (type == null) return null;
            invoker = new Invoker(access, key, attr, type);
            ClassAccess.writeCache(clz, attr, invoker);
        }
        return invoker;
    }

    @Override
    public final String toString() {
        return name + (type.equals(ClassAccess.FIELD) ? "" : "(...)");
    }
}
