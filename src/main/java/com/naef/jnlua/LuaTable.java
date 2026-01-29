package com.naef.jnlua;

import java.util.Collection;
import java.util.Map;

public class LuaTable {
    public Object table;

    public void setTable(Object table) {
        if (table == null) this.table = null;
        else if (table instanceof Collection && !(table instanceof Map))
            this.table = ((Collection<?>) table).toArray();
        else this.table = table;
    }

    public LuaTable(Object[] table) {
        this.table = table;
    }

    public LuaTable(Collection<?> table) {
        this.table = table == null ? null : table.toArray();
    }

    public LuaTable(Map<?, ?> table) {
        this.table = table;
    }
}
