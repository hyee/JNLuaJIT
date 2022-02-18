/*
 * $Id: AbstractTableList.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua.util;

import com.naef.jnlua.LuaState;
import com.naef.jnlua.LuaValueProxy;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.RandomAccess;

/**
 * Abstract list implementation backed by a Lua table.
 */
public class AbstractTableList<T> extends AbstractList<T> implements RandomAccess, LuaValueProxy {
    // -- Construction
    private final LuaState luaState;
    final LuaValueProxy luaValueProxy;
    final Class<T> clz;

    /**
     * Creates a new instance.
     */
    public AbstractTableList(LuaState luaState, int index, Class<T> clz) {
        this.luaState = luaState;
        this.luaValueProxy = luaState.getProxy(index);
        this.clz = clz;
    }

    public AbstractList toJavaObject() {
        ArrayList array = new ArrayList<>(size());
        for (Object o : this) {
            if (o instanceof AbstractTableMap)
                array.add(((AbstractTableMap) o).toJavaObject());
            else if (o instanceof AbstractTableList)
                array.add(((AbstractTableList) o).toJavaObject());
            else
                array.add(o);
        }
        return array;
    }

    @Override
    public LuaState getLuaState() {
        return luaState;
    }

    @Override
    public void pushValue() {
        luaValueProxy.pushValue();
    }

    @Override
    public int getRef() {
        return luaValueProxy.getRef();
    }

    // -- List methods
    @Override
    public void add(int index, Object element) {
        luaState.tablePush(getRef(), LuaState.PAIR_INDEX_IS_REF | LuaState.PAIR_INSERT_MODE, index + 1, element, clz);
    }

    @Override
    public T get(int index) {
        return (T) luaState.tableGet(getRef(), LuaState.PAIR_INDEX_IS_REF, index + 1, clz);
    }

    @Override
    public T remove(int index) {
        return (T) luaState.tablePush(getRef(), LuaState.PAIR_INDEX_IS_REF | LuaState.PAIR_RETURN_OLD_VALUE | LuaState.PAIR_INSERT_MODE, index + 1, null, clz);
    }

    @Override
    public T set(int index, Object element) {
        return (T) luaState.tablePush(getRef(), LuaState.PAIR_INDEX_IS_REF | LuaState.PAIR_RETURN_OLD_VALUE, index + 1, element, clz);
    }

    @Override
    public int size() {
        pushValue();
        try {
            return luaState.length(-1);
        } finally {
            luaState.pop(1);
        }
    }
}
