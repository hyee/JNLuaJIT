/*
 * $Id: AbstractTableMap.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua.util;

import com.naef.jnlua.LuaState;
import com.naef.jnlua.LuaType;
import com.naef.jnlua.LuaValueProxy;

import java.util.*;

/**
 * Abstract map implementation backed by a Lua table.
 */
public class AbstractTableMap<K, V> extends AbstractMap<K, V> implements LuaValueProxy {
    // -- State
    private Set<Map.Entry<K, V>> entrySet;
    protected LuaState luaState;
    final LuaValueProxy luaValueProxy;
    final Class<K> keyClass;
    final Class<V> valueClass;

    // -- Construction
    public AbstractTableMap() {
        luaValueProxy = this;
        keyClass = (Class<K>) Object.class;
        valueClass = (Class<V>) Object.class;
        luaState = this.getLuaState();
    }

    /**
     * Creates a new instance.
     */
    public AbstractTableMap(LuaState luaState, int index, Class<K> keyClass, Class<V> valueClass) {
        this.luaState = luaState;
        this.luaValueProxy = luaState.getProxy(index);
        this.keyClass = keyClass;
        this.valueClass = valueClass;
    }

    public AbstractMap<K, V> toJavaObject() {
        HashMap newMap = new HashMap(size());
        for (Map.Entry<K, V> entry : entrySet()) {
            Object o = entry.getValue();
            if (o instanceof AbstractTableMap)
                newMap.put(entry.getKey(), ((AbstractTableMap) o).toJavaObject());
            else if (o instanceof AbstractTableList)
                newMap.put(entry.getKey(), ((AbstractTableList) o).toJavaObject());
            else
                newMap.put(entry.getKey(), o);
        }
        return newMap;
    }

    @Override
    public int getRef() {
        return luaValueProxy.getRef();
    }

    @Override
    public LuaState getLuaState() {
        return luaState;
    }

    @Override
    public void pushValue() {
        luaValueProxy.pushValue();
    }

    // -- Map methods
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new EntrySet();
        }
        return entrySet;
    }

    @Override
    public boolean isEmpty() {
        return entrySet().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        get(key);
        return luaState.keyLuaTypes[0] != LuaType.NIL;
    }

    @Override
    public V get(Object key) {
        return (V) luaState.tableGet(getRef(), LuaState.PAIR_INDEX_IS_REF, key, valueClass);
    }

    @Override
    public V put(K key, V value) {
        return (V) luaState.tablePush(getRef(), LuaState.PAIR_INDEX_IS_REF | LuaState.PAIR_RETURN_OLD_VALUE, key, value, valueClass);
    }

    @Override
    public V remove(Object key) {
        return (V) luaState.tablePush(getRef(), LuaState.PAIR_INDEX_IS_REF | LuaState.PAIR_RETURN_OLD_VALUE, key, null, valueClass);
    }

    // -- Protected methods

    /**
     * Checks a key for validity. If the key is not valid, the method throws an
     * appropriate runtime exception. The method is invoked for all input keys.
     * <p/>
     * <p>
     * This implementation checks that the key is not <code>null</code>. Lua
     * does not allow <code>nil</code> as a table key. Subclasses may implement
     * more restrictive checks.
     * </p>
     *
     * @param key the key
     * @throws NullPointerException if the key is <code>null</code>
     */
    protected void checkKey(Object key) {
        if (key == null) {
            throw new NullPointerException("key must not be null");
        }
    }

    /**
     * Indicates if this table map filters keys from the Lua table. If the
     * method returns <code>true</code>, the table map invokes
     * {@link #acceptKey(int)} on each key retrieved from the underlying table
     * to determine whether the key is accepted or rejected.
     * <p/>
     * <p>
     * This implementation returns <code>false</code>. Subclasses may override
     * the method alongside {@link #acceptKey(int)} to implement key filtering.
     * </p>
     *
     * @return whether this table map filters keys from the Lua table
     */
    protected boolean filterKeys() {
        return false;
    }

    /**
     * Accepts or rejects a key from the Lua table. Only table keys that are
     * accepted are processed. The method allows subclasses to filter the Lua
     * table. The method is called only if {@link #filterKeys()} returns
     * <code>true</code>.
     * <p/>
     * <p>
     * This implementation returns <code>true</code> regardless of the input,
     * thus accepting all keys. Subclasses may override the method alongside
     * {@link #filterKeys()} to implement key filtering.
     * </p>
     *
     * @param key the the candidate key
     * @return whether the key is accepted
     */

    protected boolean acceptKey(Object key) {
        return true;
    }

    /**
     * Converts the key at the specified stack index to a Java object. If this
     * table maps performs key filtering, the method is invoked only for keys it
     * has accepted.
     *
     * @param index the stack index containing the key
     * @return the Java object representing the key
     * @see #filterKeys()
     * @see #acceptKey(int)
     */
    protected K convertKey(int index) {
        return luaState.toJavaObject(index, keyClass);
    }

    // -- Nested types

    /**
     * Lua table entry set.
     */
    private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        // -- Set methods
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new EntryIterator();
        }


        public int size(int top) {
            int count = 0;
            pushValue();
            if (filterKeys()) {
                K key = null;
                Object[] nextCache;
                while (true) {
                    nextCache = luaState.tableNext(getRef(), 0, key, valueClass);
                    if (nextCache[0] == null) break;
                    if (acceptKey(nextCache[0])) {
                        ++count;
                        if (top > 0 && count >= top) {
                            luaState.pop(1);
                            return count;
                        }
                    }
                }
            } else {
                count = luaState.tableSize(-1);
            }
            luaState.pop(1);
            return count;
        }

        @Override
        public boolean isEmpty() {
            return size(1) == 0;
        }

        @Override
        public int size() {
            return size(0);
        }

        @Override
        public boolean contains(Object object) {
            checkKey(object);
            if (!(object instanceof AbstractTableMap.Entry)) {
                return false;
            }
            @SuppressWarnings("unchecked") Entry luaTableEntry = (Entry) object;
            if (luaTableEntry.getLuaState() != luaState) {
                return false;
            }
            return containsKey(luaTableEntry.key);
        }

        @Override
        public boolean remove(Object object) {
            if (!(object instanceof AbstractTableMap.Entry)) {
                return false;
            }
            @SuppressWarnings("unchecked") Entry luaTableEntry = (Entry) object;
            if (luaTableEntry.getLuaState() != luaState) {
                return false;
            }
            luaState.tablePush(getRef(), LuaState.PAIR_INDEX_IS_REF | LuaState.PAIR_RETURN_OLD_VALUE, object, null, valueClass);
            return luaState.keyLuaTypes[0] != LuaType.NIL;
        }
    }

    /**
     * Lua table iterator.
     */
    private class EntryIterator implements Iterator<Map.Entry<K, V>> {
        // -- State
        private K key;
        private K cacheKey = null;
        Object[] nextCache = new Object[2];

        // -- Iterator methods
        @Override
        public boolean hasNext() {
            if (key == null || !key.equals(cacheKey)) {
                K tmp;
                while (true) {
                    nextCache = luaState.tableNext(getRef(), LuaState.PAIR_INDEX_IS_REF, key, valueClass);
                    tmp = (K) nextCache[0];
                    if (tmp == null || !filterKeys() || acceptKey(tmp)) break;
                }
            }
            cacheKey = key;
            return nextCache[0] != null;
        }

        @Override
        public Map.Entry<K, V> next() {
            if (!hasNext()) throw new NoSuchElementException();
            key = (K) nextCache[0];
            cacheKey = null;
            return new Entry(key, (V) nextCache[1]);
        }

        @Override
        public void remove() {
            luaState.tablePush(getRef(), LuaState.PAIR_INDEX_IS_REF, key, null, valueClass);
        }
    }

    /**
     * Bindings entry.
     */
    private class Entry implements Map.Entry<K, V> {
        // -- State
        private final K key;
        private V value;
        // -- Construction

        /**
         * Creates a new instance.
         */
        public Entry(K key) {
            this.key = key;
            this.value = null;
        }

        public Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        // -- Map.Entry methods
        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            if (value == null) value = get(key);
            return value;
        }

        @Override
        public V setValue(V value) {
            this.value = value;
            return put(key, value);
        }

        // -- Object methods
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AbstractTableMap.Entry)) {
                return false;
            }
            @SuppressWarnings("unchecked") Entry other = (Entry) obj;
            return luaState == other.getLuaState() && key.equals(other.key);
        }

        @Override
        public int hashCode() {
            return luaState.hashCode() * 65599 + key.hashCode();
        }

        @Override
        public String toString() {
            return key.toString();
        }

        // -- Private methods

        /**
         * Returns the Lua script engine.
         */
        private LuaState getLuaState() {
            return AbstractTableMap.this.getLuaState();
        }
    }
}