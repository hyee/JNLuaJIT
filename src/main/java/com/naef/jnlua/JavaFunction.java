/*
 * $Id: JavaFunction.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

/**
 * Provides a Lua function implemented in Java.
 */
public class JavaFunction {
    /**
     * Invokes this Java function. The function arguments are on the stack. The
     * method returns the number of values on the stack which constitute the
     * return values of this function.
     * <p/>
     * <p>
     * Java functions should indicate application errors by returning
     * appropriate error codes to the caller. Programming errors should be
     * indicated by throwing a runtime exception.
     * </p>
     *
     * @param luaState the Lua state this function has been invoked on
     * @return the number of return values
     */
    public boolean isTableArgs = false;
    private final StringBuilder sb = new StringBuilder(64);
    public Object[] params = new Object[0];

    public void setName(String... pieces) {
        sb.setLength(0);
        for (String s : pieces) sb.append(s);
    }

    public int invoke(LuaState luaState) {
        int top = luaState.getTop();
        isTableArgs = false;
        final Object[] args = new Object[top];
        for (int i = 1; i <= top; i++) {
            if (!isTableArgs && luaState.type(i) == LuaType.TABLE) isTableArgs = true;
            args[i - 1] = luaState.toJavaObject(i, Object.class);
        }
        call(luaState, args);
        return luaState.getTop() - top;
    }

    private final int JNI_call(LuaState luaState, Object... args) {
        long execThread = luaState.execThread;
        //final long luaThread = luaState.luaThread;
        if (execThread > -1) luaState.setExecThread(-1);

        try {
            params = args;
            return invoke(luaState);
        } /*catch (Throwable e) {
            luaState.pushJavaObjectRaw(new LuaError(luaState.where(1), e));
            return -1;
        }*/ finally {
            if (execThread > -1) luaState.luaThread += execThread - luaState.luaThread;
        }
    }

    public void call(LuaState luaState, Object[] args) {
    }

    public String getName() {
        return sb.toString();
    }

    public String toString() {
        return "JavaFunction(" + sb + ")";
    }
}
