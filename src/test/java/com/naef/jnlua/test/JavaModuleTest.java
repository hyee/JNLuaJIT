/*
 * $Id: JavaModuleTest.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */

package com.naef.jnlua.test;

import com.naef.jnlua.JavaModule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains unit tests for the Java module.
 */
public class JavaModuleTest extends AbstractLuaTest {
    // ---- Test cases
    @Test
    public void testToTable() {
        // Map
        Map<Object, Object> map = new HashMap<Object, Object>();
        luaState.pushJavaObject(JavaModule.getInstance().toTable(map));
        luaState.setGlobal("map");
        luaState.load("map.x = 1", "testToTable");
        luaState.call(0, 0);
        assertEquals(1, map.get("x"));

        // List
        List<Object> list = new ArrayList<Object>();
        luaState.pushJavaObject(JavaModule.getInstance().toTable(list));
        luaState.setGlobal("list");
        luaState.load("list[1] = 1", "testToList");
        luaState.call(0, 0);
        assertEquals(1, list.get(0));
    }
}
