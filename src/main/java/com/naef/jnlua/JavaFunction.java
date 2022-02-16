/*
 * $Id: JavaFunction.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

import java.nio.charset.StandardCharsets;

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
    protected Object[] params = new Object[0];
    protected LuaType[] types = new LuaType[0];
    protected boolean hasTable = false;
    protected final String formatter = "[JVM] %s%s%s";
    protected long timer;
    protected LuaState lua;

    public void setName(String... pieces) {
        sb.setLength(0);
        for (String s : pieces) sb.append(s);
    }

    public int invoke(LuaState luaState) {
        /*
        int top = luaState.getTop();
        isTableArgs = false;
        final Object[] args = new Object[top];
        for (int i = 1; i <= top; i++) {
            if (!isTableArgs && luaState.type(i) == LuaType.TABLE) isTableArgs = true;
            args[i - 1] = luaState.toJavaObject(i, Object.class);
        }
        call(luaState, args);
        return luaState.getTop() - top;*/

        isTableArgs = hasTable;
        call(luaState, params);
        return -128;
    }

    private final int JNI_call(final LuaState luaState, final long luaThread) {
        return JNI_call(luaState, luaThread, new byte[0], new Object[0]);
    }

    private final int JNI_call(final LuaState luaState, final long luaThread, final byte[] argTypes, final Object[] args) {
        if ((LuaState.trace & 2) == 2) timer += System.nanoTime() - timer;
        lua = luaState;
        luaState.yield = false;
        final long orgThread = luaState.luaThread;
        luaState.setExecThread(luaThread);
        hasTable = false;
        try {
            params = new Object[argTypes.length];
            types = new LuaType[argTypes.length];
            for (int i = 0; i < argTypes.length; i++) {
                types[i] = LuaType.get(argTypes[i]);
                switch (types[i]) {
                    case TABLE:
                        hasTable = true;
                        if (isMaintainTable) break;
                    case FUNCTION:
                    case USERDATA:
                        params[i] = luaState.converter.convertLuaValue(luaState, i + 1, types[i], Object.class);
                        break;
                    case JAVAOBJECT:
                        params[i] = args[i];
                        if (params[i] instanceof TypedJavaObject) {
                            if (!((TypedJavaObject) params[i]).isStrong())
                                params[i] = ((TypedJavaObject) params[i]).getObject();
                        }
                        break;
                    case BOOLEAN:
                        params[i] = ((byte[]) args[i])[0] == '1';
                        break;
                    default:
                        if (args[i] instanceof byte[]) {
                            params[i] = new String(((byte[]) args[i]), StandardCharsets.UTF_8);
                            if (types[i] == LuaType.NUMBER) {
                                final Double d = Double.valueOf((String) params[i]);
                                final long l = d.longValue();
                                final double dv = d.doubleValue();
                                if (dv == l) {
                                    final int s = d.intValue();
                                    if (s == dv) params[i] = s;
                                    else params[i] = l;
                                } else params[i] = d;
                            }
                        } else params[i] = args[i];
                        break;
                }
            }
            return invoke(luaState);
        } finally {
            luaState.setExecThread(orgThread);
            if (LuaState.trace > 0 && (LuaState.trace & 4) == 0) {
                String name = sb.toString();
                if (name.equals("")) name = getName();
                if (name != null) {
                    if (name.equals("")) name = this.toString();
                    if ((LuaState.trace & 2) == 2) {
                        timer += System.nanoTime() - timer * 2;
                        if (timer >= 1000)
                            LuaState.println(String.format(formatter, name, " finished in ", (timer / 1000L) + " us"));
                        timer *= 0;
                    } else {
                        LuaState.println(String.format(formatter, name, "", ""));
                    }
                }
            }
        }
    }

    final protected void checkType(int index, LuaType type) {
        if (types.length >= index && types[index - 1] == type) return;
        throw lua.getArgTypeException(index, type);
    }

    public void call(LuaState luaState, Object[] args) {
    }

    public String getName() {
        return sb.toString();
    }

}
