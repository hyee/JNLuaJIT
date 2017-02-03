package com.naef.jnlua.reflect;

import com.naef.jnlua.*;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Provides invocable access.
 */
public class InvocableAccessor implements Accessor, JavaFunction {
    // -- State
    private Class<?> clazz;
    private List<Invocable> invocables;
    private Map<LuaCallSignature, Invocable> invocableDispatches = new HashMap<LuaCallSignature, Invocable>();
    private ReadWriteLock invocableDispatchLock = new ReentrantReadWriteLock();

    /**
     * Lua call signature.
     */
    private static class LuaCallSignature {
        // -- State
        private Class<?> clazz;
        private String invocableName;
        private Object[] types;
        private int hashCode;

        // -- Construction

        /**
         * Creates a new instance.
         */
        public LuaCallSignature(Class<?> clazz, String invocableName, Object[] types) {
            this.clazz = clazz;
            this.invocableName = invocableName;
            this.types = types;
            hashCode = clazz.hashCode();
            hashCode = hashCode * 65599 + invocableName.hashCode();
            for (int i = 0; i < types.length; i++) {
                hashCode = hashCode * 65599 + types[i].hashCode();
            }
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof LuaCallSignature)) {
                return false;
            }
            LuaCallSignature other = (LuaCallSignature) obj;
            if (clazz != other.clazz || !invocableName.equals(other.invocableName) || types.length != other.types.length) {
                return false;
            }
            for (int i = 0; i < types.length; i++) {
                if (types[i] != other.types[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return clazz.getCanonicalName() + ": " + invocableName + "(" + Arrays.asList(types) + ")";
        }
    }

    // -- Construction

    /**
     * Creates a new instance.
     */
    public InvocableAccessor(Class<?> clazz, Collection<Invocable> invocables) {
        this.clazz = clazz;
        this.invocables = new ArrayList<Invocable>(invocables);
    }

    // -- Properties

    /**
     * Returns the name of the invocable.
     */
    public String getName() {
        return invocables.get(0).getName();
    }

    /**
     * Returns what this invocable accessor is for.
     */
    public String getWhat() {
        return invocables.get(0).getWhat();
    }

    // -- Accessor methods
    @Override
    public void read(LuaState luaState, Object object) {
        Class<?> objectClass = Accessor.getObjectClass(object);
        if (objectClass == object) {
            object = null;
        }
        luaState.pushJavaFunction(this);
    }

    @Override
    public void write(LuaState luaState, Object object) {
        Class<?> objectClass = Accessor.getObjectClass(object);
        throw new LuaRuntimeException(String.format("attempt to write class %s with accessor '%s' (a %s)", objectClass.getCanonicalName(), getName(), getWhat()));
    }

    @Override
    public boolean isNotStatic() {
        for (Invocable invocable : invocables) {
            if (!Modifier.isStatic(invocable.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isStatic() {
        for (Invocable invocable : invocables) {
            if (Modifier.isStatic(invocable.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    // -- JavaFunction methods
    @Override
    public int invoke(LuaState luaState) {
        // Argument sanity checks
        Object object = luaState.checkJavaObject(1, Object.class);
        Class<?> objectClass = Accessor.getObjectClass(object);
        luaState.checkArg(1, clazz.isAssignableFrom(objectClass), String.format("class %s is not a subclass of %s", objectClass.getCanonicalName(), clazz.getCanonicalName()));
        if (objectClass == object) {
            object = null;
        }

        // Invocable dispatch
        LuaCallSignature luaCallSignature = getLuaCallSignature(luaState);
        Invocable invocable;
        invocableDispatchLock.readLock().lock();
        try {
            invocable = invocableDispatches.get(luaCallSignature);
        } finally {
            invocableDispatchLock.readLock().unlock();
        }
        if (invocable == null) {
            invocable = dispatchInvocable(luaState, object == null);
            invocableDispatchLock.writeLock().lock();
            try {
                if (!invocableDispatches.containsKey(luaCallSignature)) {
                    invocableDispatches.put(luaCallSignature, invocable);
                } else {
                    invocable = invocableDispatches.get(luaCallSignature);
                }
            } finally {
                invocableDispatchLock.writeLock().unlock();
            }
        }

        // Prepare arguments
        int argCount = luaState.getTop() - 1;
        int parameterCount = invocable.getParameterCount();
        Object[] arguments = new Object[parameterCount];
        if (invocable.isVarArgs()) {
            for (int i = 0; i < parameterCount - 1; i++) {
                arguments[i] = luaState.toJavaObject(i + 2, invocable.getParameterType(i));
            }
            arguments[parameterCount - 1] = Array.newInstance(invocable.getParameterType(parameterCount - 1), argCount - (parameterCount - 1));
            for (int i = parameterCount - 1; i < argCount; i++) {
                Array.set(arguments[parameterCount - 1], i - (parameterCount - 1), luaState.toJavaObject(i + 2, invocable.getParameterType(i)));
            }
        } else {
            for (int i = 0; i < parameterCount; i++) {
                arguments[i] = luaState.toJavaObject(i + 2, invocable.getParameterType(i));
            }
        }

        // Invoke
        Object result;
        try {
            result = invocable.invoke(object, arguments);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getTargetException());
        }

        // Return
        if (invocable.getReturnType() != Void.TYPE) {
            if (invocable.isRawReturn()) {
                luaState.pushJavaObjectRaw(result);
            } else {
                luaState.pushJavaObject(result);
            }
            return 1;
        } else {
            return 0;
        }
    }

    // -- Private methods

    /**
     * Creates a Lua call signature.
     */
    private LuaCallSignature getLuaCallSignature(LuaState luaState) {
        int argCount = luaState.getTop() - 1;
        Object[] types = new Object[argCount];
        for (int i = 0; i < argCount; i++) {
            LuaType type = luaState.type(i + 2);
            switch (type) {
                case FUNCTION:
                    types[i] = luaState.isJavaFunction(i + 2) ? JAVA_FUNCTION_TYPE : LuaType.FUNCTION;
                    break;
                case USERDATA:
                    if (luaState.isJavaObjectRaw(i + 2)) {
                        Object object = luaState.toJavaObjectRaw(i + 2);
                        if (object instanceof TypedJavaObject) {
                            types[i] = ((TypedJavaObject) object).getType();
                        } else {
                            types[i] = object.getClass();
                        }
                    } else {
                        types[i] = LuaType.USERDATA;
                    }
                    break;
                default:
                    types[i] = type;
            }
        }
        return new LuaCallSignature(clazz, getName(), types);
    }

    /**
     * Dispatches an invocable.
     */
    private Invocable dispatchInvocable(LuaState luaState, boolean staticDispatch) {
        // Begin with all candidates
        Set<Invocable> candidates = new HashSet<Invocable>(invocables);

        // Eliminate methods with an invalid static modifier
        for (Iterator<Invocable> i = candidates.iterator(); i.hasNext(); ) {
            Invocable invocable = i.next();
            if (Modifier.isStatic(invocable.getModifiers()) != staticDispatch) {
                i.remove();
            }
        }

        // Eliminate methods with an invalid parameter count
        int argCount = luaState.getTop() - 1;
        for (Iterator<Invocable> i = candidates.iterator(); i.hasNext(); ) {
            Invocable invocable = i.next();
            if (invocable.isVarArgs()) {
                if (argCount < invocable.getParameterCount() - 1) {
                    i.remove();
                }
            } else {
                if (argCount != invocable.getParameterCount()) {
                    i.remove();
                }
            }
        }

        // Eliminate methods that are not applicable
        Converter converter = luaState.getConverter();
        outer:
        for (Iterator<Invocable> i = candidates.iterator(); i.hasNext(); ) {
            Invocable invocable = i.next();
            for (int j = 0; j < argCount; j++) {
                int distance = converter.getTypeDistance(luaState, j + 2, invocable.getParameterType(j));
                if (distance == Integer.MAX_VALUE) {
                    i.remove();
                    continue outer;
                }
            }
        }

        // Eliminate variable arguments methods in the presence of fix
        // arguments methods
        boolean haveFixArgs = false;
        boolean haveVarArgs = false;
        for (Invocable invocable : candidates) {
            haveFixArgs = haveFixArgs || !invocable.isVarArgs();
            haveVarArgs = haveVarArgs || invocable.isVarArgs();
        }
        if (haveVarArgs && haveFixArgs) {
            for (Iterator<Invocable> i = candidates.iterator(); i.hasNext(); ) {
                Invocable invocable = i.next();
                if (invocable.isVarArgs()) {
                    i.remove();
                }
            }
        }

        // Eliminate methods that are not closest
        outer:
        for (Iterator<Invocable> i = candidates.iterator(); i.hasNext(); ) {
            Invocable invocable = i.next();
            inner:
            for (Invocable other : candidates) {
                if (other == invocable) {
                    continue;
                }
                int parameterCount = Math.min(argCount, Math.max(invocable.getParameterCount(), other.getParameterCount()));
                boolean delta = false;
                for (int j = 0; j < parameterCount; j++) {
                    int distance = converter.getTypeDistance(luaState, j + 2, invocable.getParameterType(j));
                    int otherDistance = converter.getTypeDistance(luaState, j + 2, other.getParameterType(j));
                    if (otherDistance > distance) {
                        // Other is not closer
                        continue inner;
                    }
                    delta = delta || distance != otherDistance;
                }

                // If there is no delta, other is not closer
                if (!delta) {
                    continue;
                }

                // Other is closer
                i.remove();
                continue outer;
            }
        }

        // Eliminate methods that are not most precise
        outer:
        for (Iterator<Invocable> i = candidates.iterator(); i.hasNext(); ) {
            Invocable invocable = i.next();
            inner:
            for (Invocable other : candidates) {
                if (other == invocable) {
                    continue;
                }
                int parameterCount = Math.min(argCount, Math.max(invocable.getParameterCount(), other.getParameterCount()));
                boolean delta = false;
                for (int j = 0; j < parameterCount; j++) {
                    Class<?> type = invocable.getParameterType(j);
                    Class<?> otherType = other.getParameterType(j);
                    if (!type.isAssignableFrom(otherType)) {
                        // Other is not more specific
                        continue inner;
                    }
                    delta = delta || type != otherType;
                }

                // If there is no delta, other is not more specific
                if (!delta) {
                    continue;
                }

                // Other is more specific
                i.remove();
                continue outer;
            }
        }

        // Handle outcomes
        if (candidates.isEmpty()) {
            throw getSignatureMismatchException(luaState);
        }
        if (candidates.size() > 1) {
            throw getSignatureAmbivalenceException(luaState, candidates);
        }

        // Return
        return candidates.iterator().next();
    }

    /**
     * Returns a Lua runtime exception indicating that no matching invocable
     * has been found.
     */
    private LuaRuntimeException getSignatureMismatchException(LuaState luaState) {
        return new LuaRuntimeException(String.format("no %s of class %s matches '%s(%s)'", getWhat(), clazz.getCanonicalName(), getName(), getLuaSignatureString(luaState)));
    }

    /**
     * Returns a Lua runtime exception indicating that an invocable is
     * ambivalent.
     */
    private LuaRuntimeException getSignatureAmbivalenceException(LuaState luaState, Set<Invocable> candidates) {
        StringBuffer sb = new StringBuffer();
        sb.append(String.format("%s '%s(%s)' on class %s is ambivalent among ", getWhat(), getName(), getLuaSignatureString(luaState), clazz.getCanonicalName()));
        boolean first = true;
        for (Invocable invocable : candidates) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(String.format("'%s(%s)'", getName(), getJavaSignatureString(invocable.getParameterTypes())));
        }
        return new LuaRuntimeException(sb.toString());
    }

    /**
     * Returns a Lua value signature string for diagnostic messages.
     */
    private String getLuaSignatureString(LuaState luaState) {
        int argCount = luaState.getTop() - 1;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < argCount; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(luaState.typeName(i + 2));
        }
        return sb.toString();
    }

    /**
     * Returns a Java type signature string for diagnostic messages.
     */
    private String getJavaSignatureString(Class<?>[] types) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(types[i].getCanonicalName());
        }
        return sb.toString();
    }
}
