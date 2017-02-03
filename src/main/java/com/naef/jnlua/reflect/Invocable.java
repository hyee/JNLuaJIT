package com.naef.jnlua.reflect;

import java.lang.reflect.InvocationTargetException;

/**
 * Virtual superinterface for methods and constructors.
 */
public interface Invocable {
    /**
     * Returns what this invocable is, for use in diagnostic messages.
     */
    public String getWhat();

    /**
     * Returns the declaring class of this invocable.
     */
    public Class<?> getDeclaringClass();

    /**
     * Returns the modifiers of this invocable.
     *
     * @return
     */
    public int getModifiers();

    /**
     * Returns the name of this invocable.
     */
    public String getName();

    /**
     * Returns the return type of this invocable.
     */
    public Class<?> getReturnType();

    /**
     * Returns whether this invocable has a return value that must be pushed
     * raw.
     */
    public boolean isRawReturn();

    /**
     * Returns the number of parameters.
     */
    public int getParameterCount();

    /**
     * Returns the parameter types of this invocable.
     */
    public Class<?>[] getParameterTypes();

    /**
     * Returns a parameter type, flattening variable arguments.
     */
    public Class<?> getParameterType(int index);

    /**
     * Returns whether this invocable has a variable number of arguments.
     *
     * @return
     */
    public boolean isVarArgs();

    /**
     * Invokes this invocable.
     */
    public Object invoke(Object obj, Object... args) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException;
}

