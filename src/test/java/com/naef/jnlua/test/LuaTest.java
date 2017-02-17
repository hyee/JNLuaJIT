/*
 * $Id: LuaTest.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */

package com.naef.jnlua.test;

import org.junit.Test;

import java.io.InputStream;

/**
 * Lua-based unit tests.
 */
public class LuaTest extends AbstractLuaTest {
    // -- Test cases

    /**
     * Tests the Java module.
     */
    @Test
    public void testJavaModule() throws Exception {
        runLuaTest("com/naef/jnlua/test/JavaModule.lua", "JavaModule");
    }

    /**
     * Tests reflection.
     */
    @Test
    public void testReflection() throws Exception {
        runLuaTest("com/naef/jnlua/test/Reflection.lua", "Reflection");
    }

    // -- Private methods

    /**
     * Runs a Lua-based test.
     */
    private void runLuaTest(String source, String moduleName) throws Exception {
        // Open libraries
        luaState.openLibs();

        // Load
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(source);
        luaState.load(inputStream, moduleName, "t");
        luaState.pushString(moduleName);
        luaState.call(1, 0);
        // Run all module functions beginning with "test"
        luaState.getGlobal(moduleName);
        luaState.pushNil();
        while (luaState.next(1)) {
            String key = luaState.toString(-2);
            if (key.startsWith("test") && luaState.isFunction(-1)) {
                System.out.println("Testing function " + key);
                luaState.call(0, 0);
            } else {
                luaState.pop(1);
            }
        }
    }
}