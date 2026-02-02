package com.naef.jnlua;

public class BufferWriter {
    LuaState luaState;
    protected final Object[] paramArgs = new Object[33];
    public BufferWriter(LuaState luaState) {
        this.luaState = luaState;
    }
}
