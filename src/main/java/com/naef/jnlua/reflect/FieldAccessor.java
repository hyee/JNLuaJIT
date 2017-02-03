package com.naef.jnlua.reflect;
import com.naef.jnlua.LuaState;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Provides field access.
 */
public class FieldAccessor implements Accessor {
    // -- State
    private Field field;

    // -- Construction

    /**
     * Creates a new instance.
     */
    public FieldAccessor(Field field) {
        this.field = field;
    }

    // -- Accessor methods
    @Override
    public void read(LuaState luaState, Object object) {
        try {
            Class<?> objectClass = Accessor.getObjectClass(object);
            if (objectClass == object) {
                object = null;
            }
            luaState.pushJavaObject(field.get(object));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(LuaState luaState, Object object) {
        try {
            Class<?> objectClass = Accessor.getObjectClass(object);
            if (objectClass == object) {
                object = null;
            }
            Object value = luaState.checkJavaObject(-1, field.getType());
            field.set(object, value);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isNotStatic() {
        return !Modifier.isStatic(field.getModifiers());
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(field.getModifiers());
    }
}
