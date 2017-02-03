package com.naef.jnlua.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/**
 * Invocable constructor.
 */
public class InvocableConstructor implements Invocable {
    // -- State
    private Constructor<?> constructor;
    private Class<?>[] parameterTypes;

    /**
     * Creates a new instance.
     */
    public InvocableConstructor(Constructor<?> constructor) {
        this.constructor = constructor;
        this.parameterTypes = constructor.getParameterTypes();
    }

    @Override
    public String getWhat() {
        return "constructor";
    }

    @Override
    public Class<?> getDeclaringClass() {
        return constructor.getDeclaringClass();
    }

    @Override
    public int getModifiers() {
        return constructor.getModifiers() | Modifier.STATIC;
    }

    @Override
    public String getName() {
        return "new";
    }

    @Override
    public Class<?> getReturnType() {
        return constructor.getDeclaringClass();
    }

    @Override
    public boolean isRawReturn() {
        return false;
    }

    @Override
    public int getParameterCount() {
        return parameterTypes.length;
    }

    @Override
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    @Override
    public Class<?> getParameterType(int index) {
        if (constructor.isVarArgs() && index >= parameterTypes.length - 1) {
            return parameterTypes[parameterTypes.length - 1].getComponentType();
        } else {
            return parameterTypes[index];
        }
    }

    @Override
    public boolean isVarArgs() {
        return constructor.isVarArgs();
    }

    @Override
    public Object invoke(Object obj, Object... args) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return constructor.newInstance(args);
    }

    @Override
    public String toString() {
        return constructor.toString();
    }
}