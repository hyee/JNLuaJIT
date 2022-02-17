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

    // -- List methods
    @Override
    public void add(int index, Object element) {
        int size = size();
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }
        pushValue();
        luaState.tableMove(-1, index + 1, index + 2, size - index);
        luaState.pushJavaObject(element);
        luaState.rawSet(-2, index + 1);
        luaState.pop(1);
    }

    @Override
    public T get(int index) {
        int size = size();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }
        pushValue();
        luaState.rawGet(-1, index + 1);
        try {
            return luaState.toJavaObject(-1, clz);
        } finally {
            luaState.pop(2);
        }
    }

    @Override
    public T remove(int index) {
        int size = size();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }
        T oldValue = get(index);
        pushValue();
        luaState.tableMove(-1, index + 2, index + 1, size - index - 1);
        luaState.pushNil();
        luaState.rawSet(-2, size);
        luaState.pop(1);
        return oldValue;
    }

    @Override
    public T set(int index, Object element) {
        int size = size();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }
        return (T) luaState.pairPush(luaValueProxy.getRef(), true, index + 1, element);
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
