package com.naef.jnlua.reflect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Invocable method.
 */
public class InvocableMethod implements Invocable {
    private Method method;
    private Class<?>[] parameterTypes;

    /**
     * Creates a new instance.
     */
    public InvocableMethod(Method method) {
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
    }

    @Override
    public String getWhat() {
        return "method";
    }

    @Override
    public Class<?> getDeclaringClass() {
        return method.getDeclaringClass();
    }

    @Override
    public int getModifiers() {
        return method.getModifiers();
    }

    @Override
    public String getName() {
        return method.getName();
    }

    @Override
    public Class<?> getReturnType() {
        return method.getReturnType();
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
        if (method.isVarArgs() && index >= parameterTypes.length - 1) {
            return parameterTypes[parameterTypes.length - 1].getComponentType();
        } else {
            return parameterTypes[index];
        }
    }

    @Override
    public boolean isVarArgs() {
        return method.isVarArgs();
    }

    @Override
    public Object invoke(Object obj, Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return method.invoke(obj, args);
    }

    @Override
    public String toString() {
        return method.toString();
    }
}

