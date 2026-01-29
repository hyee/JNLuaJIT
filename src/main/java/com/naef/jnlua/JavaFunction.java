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

    //Whether the inherited class maintains Lua table on its own.
    protected boolean isMaintainTable;
    //Used to defined whether the input arguments includes Lua table
    public boolean isTableArgs = false;
    private final StringBuilder sb = new StringBuilder();
    private byte[] nameBytes;
    protected Object[] params = new Object[0];
    protected LuaType[] types = new LuaType[0];
    protected boolean hasTable = false;
    protected final String formatter = "[JVM] %s%s%s";
    protected long timer;
    protected LuaState lua;

    public void setName(String... pieces) {
        sb.setLength(0);
        for (String s : pieces) sb.append(s);
        nameBytes = null;
    }

    /**
     * ====================================================================
     * [Performance] Lazy-initialized UTF-8 Name Cache
     * ====================================================================
     * Returns the UTF-8 byte array representation of the function name.
     * 
     * Optimization:
     * - Caches the byte array after first call (nameBytes field)
     * - Invalidated when setName() is called (nameBytes set to null)
     * - Avoids repeated String.getBytes(UTF8) calls during JNI operations
     * 
     * Performance Impact:
     * - JNI boundary prefers byte[] over String (eliminates encoding overhead)
     * - Typical function called multiple times -> single encoding operation
     * - Complements LuaState.getCanonicalName() optimization
     * 
     * Design:
     * - Simple lazy initialization (not thread-safe, but safe for typical usage)
     * - Returns empty byte array (not null) if name is null (prevents NPE in JNI)
     * 
     * @return UTF-8 encoded function name, or empty array if name is null
     */
    public byte[] getNameBytes() {
        if (nameBytes == null) {
            String name = getName();
            nameBytes = name != null ? name.getBytes(LuaState.UTF8) : new byte[0];
        }
        return nameBytes;
    }

    public int invoke(LuaState luaState) {
        isTableArgs = hasTable;
        luaState.paramTypes[0] = -128;
        call(luaState, params);
        return luaState.paramTypes[0] == -128 ? -128 : -64;
    }

    protected void log(String s1, String s2) {
        String name = sb.toString();
        if (name.equals("")) name = getName();
        if (name == null) return;
        if (name.equals("")) name = this.toString();
        LuaState.println(String.format(formatter, name, s1, s2));
    }

    private final int JNI_call(final LuaState luaState, final long luaThread, final int argCount) {
        if ((LuaState.trace & 6) == 2) timer += System.nanoTime() - timer;
        lua = luaState;
        luaState.yield = false;
        final long orgThread = luaState.luaThread;
        luaState.setExecThread(luaThread);
        hasTable = false;
        int result = -9;
        try {
            if (types == null || types.length != argCount) {
                params = new Object[argCount];
                types = new LuaType[argCount];
            }
            hasTable = luaState.converter.getLuaValues(luaState, isMaintainTable, luaState.paramArgs, luaState.paramTypes, params, types, Object.class);
            result = invoke(luaState);
            luaState.paramTypes[32] = (byte) (luaState.yield ? 1 : 0);
            return result;
        } finally {
            luaState.setExecThread(orgThread);
            if ((LuaState.trace & 6) == 2) {
                timer += System.nanoTime() - timer * 2;
                if (timer >= 1000) log(" => ", (timer / 1000L) + " us");
                timer *= 0;
            } else if ((LuaState.trace & 5) == 1) {
                log(" => ", result == -9 ? "error" : "done");
            }
        }
    }

    public void call(LuaState luaState, Object[] args) {
    }

    public String getName() {
        return sb.toString();
    }

}
