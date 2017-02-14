package com.naef.jnlua;

import com.esotericsoftware.reflectasm.ClassAccess;

import java.lang.reflect.Modifier;

/**
 * Created by Will on 2017/2/13.
 */
public class Invoker implements JavaFunction {
    public ClassAccess access;
    public String attr;
    public String type;
    public String name;

    public Invoker(ClassAccess access, String name, String attr, String attrType) {
        this.access = access;
        this.name = name;
        this.attr = attr;
        this.type = attrType;
    }

    public int read(LuaState luaState, Object object) {
        if (type.equals(ClassAccess.FIELD)) {
            int index = access.indexOfField(attr);
            luaState.pushJavaObject(access.get(Modifier.isStatic(access.classInfo.fieldModifiers[index]) ? null : object, attr));
        } else luaState.pushJavaFunction(this);
        return 1;
    }

    public int write(LuaState luaState, Object object) {
        if (type.equals(ClassAccess.FIELD))
            access.set(object, attr, luaState.toJavaObject(-1, Object.class));
        else throw new LuaRuntimeException("Attempt to override method " + name);
        return 0;
    }

    public int invoke(LuaState luaState) {
        if (type.equals(ClassAccess.FIELD)) throw new LuaRuntimeException("Attempt to call field " + name);
        final int argCount = luaState.getTop() - 1;
        Object instance = luaState.toJavaObject(1, Object.class);
        Object[] args = new Object[argCount];
        for (int i = 0; i < argCount; i++)
            args[i] = luaState.toJavaObject(i + 2, Object.class);
        Object result;
        if (type.equals(ClassAccess.METHOD)) {
            final int index = access.indexOfMethod(null, attr, ClassAccess.args2Types(args));
            result = access.invokeWithIndex(Modifier.isStatic(access.classInfo.methodModifiers[index]) ? null : instance, index, args);
            if (access.classInfo.returnTypes[index] == Void.class) return 0;
        } else result = access.newInstance(args);
        luaState.pushJavaObject(result);
        return 1;
    }

    public static Invoker get(final Class clz, final String attr, final String prefix) {
        String key = clz.getCanonicalName() + "." + attr;
        Invoker invoker = (Invoker) ClassAccess.readCache(clz, attr);
        if (invoker == null) {
            ClassAccess access = ClassAccess.access(clz);
            String type = access.getNameType((prefix == null ? "" : prefix) + attr);
            if (type == null) return null;
            invoker = new Invoker(access, key, attr, type);
            ClassAccess.writeCache(clz, attr, invoker);
        }
        return invoker;
    }

    @Override
    public String toString() {
        return name + (type.equals(ClassAccess.FIELD) ? "" : "(...)");
    }
}
