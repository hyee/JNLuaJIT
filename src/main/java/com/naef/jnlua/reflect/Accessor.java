package com.naef.jnlua.reflect;

import com.naef.jnlua.LuaState;

/**
 * Provides access to class or object members.
 */
public interface Accessor {
    static final Object JAVA_FUNCTION_TYPE = new Object();
    static final Object[] EMPTY_ARGUMENTS = new Object[0];
    /**
     * Reads the object member.
     */
    void read(LuaState luaState, Object object);

    /**
     * Writes the object member.
     */
    void write(LuaState luaState, Object object);

    /**
     * Returns whether this accessor is applicable in a non-static context.
     */
    boolean isNotStatic();

    /**
     * Returns whether this accessor is applicable in a static context.
     */
    boolean isStatic();

   static  Class<?> getObjectClass(Object object) {
        return object instanceof Class<?> ? (Class<?>) object : object.getClass();
    }
}
