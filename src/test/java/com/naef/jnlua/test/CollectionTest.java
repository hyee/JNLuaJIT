/*
 * $Id: CollectionTest.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */

package com.naef.jnlua.test;

import com.naef.jnlua.LuaState;
import com.naef.jnlua.LuaTable;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Contains unit tests for collections backed by Lua tables.
 */
public class CollectionTest extends AbstractLuaTest {
    // ---- Test cases

    /**
     * Tests the map.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMap() throws Exception {
        // Get a map backed by Lua
        System.out.println(1);
        luaState.newTable();
        Map<Object, Object> map = luaState.toJavaObject(-1, Map.class);

        // isEmpty(), size()
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());

        // put()
        map.put("t", "test");
        assertFalse(map.isEmpty());
        assertEquals(1, map.size());
        luaState.getField(-1, "t");
        assertEquals("test", luaState.toString(-1));
        luaState.pop(1);

        // containsKey()
        assertTrue(map.containsKey("t"));

        // containsValue()
        assertTrue(map.containsValue("test"));

        // get()
        assertEquals("test", map.get("t"));

        // putAll()
        Map<String, Object> map2 = new HashMap<String, Object>();
        map2.put("v", "test2");
        map.putAll(map2);
        assertEquals("test2", map.get("v"));
        luaState.getField(-1, "v");
        assertEquals("test2", luaState.toString(-1));
        luaState.pop(1);

        // remove()
        map.remove("v");
        assertNull(map.get("v"));
        luaState.getField(-1, "v");
        assertTrue(luaState.isNil(-1));
        luaState.pop(1);

        // entrySet()
        int count = 0;
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                count++;
            }
        }
        assertEquals(map.size(), count);

        // values()
        boolean found = false;
        for (Object object : map.values()) {
            if (object.equals("test")) {
                found = true;
                break;
            }
        }
        assertTrue(found);

        // keySet()
        assertTrue(map.containsKey("t"));
        Iterator<Object> iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().equals("t")) {
                iterator.remove();
            }
        }
        assertFalse(map.containsKey("t"));

        // clear()
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        // Finish
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the list.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testList() throws Exception {
        // Get a list backed by Lua
        luaState.newTable();
        List<Object> list = luaState.toJavaObject(-1, List.class);

        // isEmpty(), size()
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());

        // add()
        list.add("test");
        assertFalse(list.isEmpty());
        assertEquals(1, list.size());
        luaState.rawGet(-1, 1);
        assertEquals("test", luaState.toString(-1));
        luaState.pop(1);

        // contains()
        assertTrue(list.contains("test"));

        // get()
        assertEquals("test", list.get(0));

        // addAll()
        List<Object> list2 = new ArrayList<Object>();
        list2.add("test2");
        list.addAll(0, list2);
        assertEquals("test2", list.get(0));
        luaState.rawGet(-1, 1);
        assertEquals("test2", luaState.toString(-1));
        luaState.pop(1);

        // remove()
        list.remove(0);
        assertEquals(1, list.size());
        assertEquals(1, luaState.length(-1));
        luaState.rawGet(-1, 1);
        assertEquals("test", luaState.toString(-1));
        luaState.pop(1);

        // iterator()
        int count = 0;
        for (Object object : list) {
            assertEquals("test", object);
            count++;
        }
        assertEquals(list.size(), count);

        // clear()
        list.clear();
        assertEquals(0, list.size());
        assertTrue(list.isEmpty());

        // Finish
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    @Test
    public void testArray() {
        LuaState lua = new LuaState();

        Long[][] longs = new Long[][]{{1L, 2L, 3L}, {4L, null, 6L}};
        lua.load("return #obj,#obj[1],obj[1][3]", "test");
        lua.pushGlobal("obj", longs);
        Object[] ret = lua.call();

        LuaTable ary = new LuaTable(longs);
        lua.load("return #obj,#obj[1],obj[1][3]", "test");
        lua.pushGlobal("obj", ary);
        ret = lua.call();
        assertTrue(Arrays.equals(ret, new Integer[]{2, 3, 3}));

        Map map0 = new HashMap();
        map0.put("x", "x0");
        map0.put("y", "y0");

        HashMap map = new HashMap();
        map.put("a", 1);
        map.put("b", longs);
        map.put(longs, "c");
        map.put("d", map0);

        ary = new LuaTable(map);
        lua.load("return obj.a,type(obj.b),obj.b[1][2],obj.d.x,type(obj.b[1])", "test");
        lua.pushGlobal("obj", ary);
        ret = lua.call();
        assertEquals(Arrays.toString(ret), Arrays.toString(new Object[]{1, "table", 2, "x0", "table"}));
    }
}
