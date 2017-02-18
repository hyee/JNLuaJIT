package com.naef.jnlua;

import java.util.Collection;
import java.util.Map;

/**
 * Created by Administrator on 2017/2/17 0017.
 */
public class LuaTable {
    public Object table;

    public LuaTable(Object[] table) {this.table = table;}

    public LuaTable(Collection table) {this.table = table == null ? null : table.toArray();}

    public LuaTable(Map table) {this.table = table;}
}
