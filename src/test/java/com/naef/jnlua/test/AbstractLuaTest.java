/*
 * $Id: AbstractLuaTest.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */

package com.naef.jnlua.test;

import com.naef.jnlua.LuaState;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;

import java.io.InputStream;

/**
 * Abstract base class for JNLua unit tests.
 */
public abstract class AbstractLuaTest {
    // ---- State
    protected LuaState luaState=null;

    // ---- Setup

    /**
     * Performs setup.
     */
    @Before
    public void setup() throws Exception {
        if(luaState!=null && luaState.isOpen())
            luaState.setTop(0);
        else {
            luaState = new LuaState();
        }
    }

    /**
     * Performs teardown.
     */
    @After
    public void teardown() throws Throwable {
        if (luaState != null) {
            try {
                //luaState.close();
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    // -- Protected method
    /**
     * Runs a Lua-based test.
     */
    protected void runTest(String source, String moduleName) throws Exception {
        // Open libraries
        luaState.openLibs();

        // Load
        InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(source);
        luaState.load(inputStream, "=" + moduleName, "t");
        luaState.pushString(moduleName);
        luaState.call(1, 0);

        // Run all module functions beginning with "test"
        luaState.getGlobal(moduleName);
        luaState.pushNil();
        while (luaState.next(1)) {
            String key = luaState.toString(-2);
            if (key.startsWith("test") && luaState.isFunction(-1)) {
                luaState.call(0, 0);
            } else {
                luaState.pop(1);
            }
        }
    }
}
