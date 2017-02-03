package com.naef.jnlua.reflect;

import com.naef.jnlua.LuaValueProxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/**
 * Invocable proxy.
 */
public class InvocableProxy implements Invocable {
    // -- Static
    private static final Class<?>[] PARAMETER_TYPES = new Class<?>[]{LuaValueProxy.class};
    // -- State
    private Class<?> interfaze;

    /**
     * Creates a new instance.
     */
    public InvocableProxy(Class<?> interfaze) {
        this.interfaze = interfaze;
    }

    @Override
    public String getWhat() {
        return "proxy";
    }

    @Override
    public Class<?> getDeclaringClass() {
        return interfaze;
    }

    @Override
    public int getModifiers() {
        return interfaze.getModifiers() | Modifier.STATIC;
    }

    @Override
    public String getName() {
        return "new";
    }

    @Override
    public Class<?> getReturnType() {
        return interfaze;
    }

    @Override
    public boolean isRawReturn() {
        return true;
    }

    @Override
    public int getParameterCount() {
        return 1;
    }

    @Override
    public Class<?>[] getParameterTypes() {
        return PARAMETER_TYPES;
    }

    @Override
    public Class<?> getParameterType(int index) {
        return PARAMETER_TYPES[0];
    }

    @Override
    public boolean isVarArgs() {
        return false;
    }

    @Override
    public Object invoke(Object obj, Object... args) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        LuaValueProxy luaValueProxy = (LuaValueProxy) args[0];
        luaValueProxy.pushValue();
        Object proxy = luaValueProxy.getLuaState().getProxy(-1, interfaze);
        luaValueProxy.getLuaState().pop(1);
        return proxy;
    }

    @Override
    public String toString() {
        return interfaze.toString();
    }
}

