package com.naef.jnlua.reflect;

import com.naef.jnlua.LuaRuntimeException;
import com.naef.jnlua.LuaState;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;

/**
 * Provides property access.
 */
public class PropertyAccessor implements Accessor {
    // -- State
    private Class<?> clazz;
    private PropertyDescriptor propertyDescriptor;

    // -- Construction

    /**
     * Creates a new instance.
     */
    public PropertyAccessor(Class<?> clazz, PropertyDescriptor propertyDescriptor) {
        this.clazz = clazz;
        this.propertyDescriptor = propertyDescriptor;
    }

    // -- Accessor methods
    @Override
    public void read(LuaState luaState, Object object) {
        if (propertyDescriptor.getReadMethod() == null) {
            throw new LuaRuntimeException(String.format("attempt to read class %s with accessor '%s' (a write-only property)", clazz.getCanonicalName(), propertyDescriptor.getName()));
        }
        try {
            luaState.pushJavaObject(propertyDescriptor.getReadMethod().invoke(object, EMPTY_ARGUMENTS));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getTargetException());
        }
    }

    @Override
    public void write(LuaState luaState, Object object) {
        if (propertyDescriptor.getWriteMethod() == null) {
            throw new LuaRuntimeException(String.format("attempt to write class %s with acessor '%s' (a read-only property)", clazz.getCanonicalName(), propertyDescriptor.getName()));
        }
        try {
            Object value = luaState.checkJavaObject(-1, propertyDescriptor.getPropertyType());
            propertyDescriptor.getWriteMethod().invoke(object, value);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getTargetException());
        }
        luaState.pop(1);
    }

    @Override
    public boolean isNotStatic() {
        return true;
    }

    @Override
    public boolean isStatic() {
        return false;
    }
}
