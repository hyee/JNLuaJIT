/*
 * $Id: LuaStateTest.java 156 2012-10-05 22:57:25Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */

package com.naef.jnlua.test;

import com.naef.jnlua.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Contains unit tests for the Lua state.
 */
public class LuaStateTest extends AbstractLuaTest {
    // -- State
    private JavaFunction javaFunction;
    private Object object;

    // -- Fields tests

    /**
     * Tests the registry index.
     */
    @Test
    public void testRegistryIndex() {

        luaState.rawGet(LuaState.REGISTRYINDEX, LuaState.RIDX_MAINTHREAD);
        assertEquals(LuaType.THREAD, luaState.type(-1));
        luaState.pop(1);


        luaState.rawGet(LuaState.REGISTRYINDEX, LuaState.RIDX_GLOBALS);
        assertEquals(LuaType.TABLE, luaState.type(-1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the version.
     */
    @Test
    public void testVersion() {
        assertEquals("0.9", LuaState.VERSION);
    }

    /**
     * Tests the Lua version.
     */
    @Test
    public void testLuaVersion() {
        assertEquals("5.1", LuaState.LUA_VERSION);
    }

    // -- Property tests

    /**
     * Tests the classLoader property.
     */
    @Test
    public void testProperties() throws Exception {
        assertSame(Thread.currentThread().getContextClassLoader(),
                luaState.getClassLoader());
        luaState.setClassLoader(ClassLoader.getSystemClassLoader());
        assertSame(ClassLoader.getSystemClassLoader(),
                luaState.getClassLoader());
    }


    /**
     * Tests the getMetamethod method.
     */
    @Test
    public void testGetMetamethod() throws Exception {
        assertNotNull(luaState.getMetamethod(null, JavaReflector.Metamethod.TOSTRING));
    }


    // -- Life cycle tests
    /**
     * Tests the isOpen method.
     */


    /**
     * Tests the gc method.
     */
    @Test
    public void testGc() throws Exception {
        //assertEquals(1, luaState.gc(LuaState.GcAction.ISRUNNING, 0));
        assertEquals(0, luaState.gc(LuaState.GcAction.STOP, 0));
        //assertEquals(0, luaState.gc(LuaState.GcAction.ISRUNNING, 0));
        assertEquals(0, luaState.gc(LuaState.GcAction.RESTART, 0));
        //assertEquals(1, luaState.gc(LuaState.GcAction.ISRUNNING, 0));
        assertEquals(0, luaState.gc(LuaState.GcAction.COLLECT, 0));
        assertTrue((long) luaState.gc(LuaState.GcAction.COUNT, 0) * 1024
                + luaState.gc(LuaState.GcAction.COUNTB, 0) > 0);
        assertEquals(0, luaState.gc(LuaState.GcAction.STEP, 0));
        assertTrue(luaState.gc(LuaState.GcAction.SETPAUSE, 200) > 0);
        assertTrue(luaState.gc(LuaState.GcAction.SETSTEPMUL, 200) > 0);
        //assertEquals(0, luaState.gc(LuaState.GcAction.GEN, 0));
        //assertEquals(0, luaState.gc(LuaState.GcAction.INC, 0));
    }


    /**
     * Tests the registration methods.
     */
    @Test
    public void testRegistration() throws Exception {
        /*// openLib()
        testOpenLib(LuaState.Library.BASE, "coroutine");
        testOpenLib(LuaState.Library.TABLE, "table");
        testOpenLib(LuaState.Library.IO, "io");
        testOpenLib(LuaState.Library.OS, "os");
        testOpenLib(LuaState.Library.STRING, "string");
        testOpenLib(LuaState.Library.MATH, "math");
        testOpenLib(LuaState.Library.DEBUG, "debug");
        testOpenLib(LuaState.Library.PACKAGE, "package");
        testOpenLib(LuaState.Library.JAVA, "java");
        */

        // openLibs()
        LuaState newLuaState = new LuaState();

        newLuaState.getGlobal("table");
        assertEquals(LuaType.TABLE, newLuaState.type(-1));
        newLuaState.pop(1);
        newLuaState.close();

        // register(JavaFunction)
        JavaFunction javaFunction = new SimpleJavaFunction();
        luaState.register(javaFunction);
        luaState.getGlobal("test");
        assertEquals(LuaType.JAVAFUNCTION, luaState.type(-1));
        luaState.pop(1);

        // register(String, JavaFunction[])
        luaState.register("testlib", new JavaFunction[]{javaFunction});
        assertEquals(LuaType.TABLE, luaState.type(-1));
        luaState.pop(1);
        luaState.getGlobal("testlib");
        assertEquals(LuaType.TABLE, luaState.type(-1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    // -- Load and dump tests

    /**
     * Tests the load methods.
     */
    @Test
    public void testLoad() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                "a = {}".getBytes(LuaState.UTF8));
        // load(InputStream)
        luaState.load(inputStream, "=testLoad", "t");
        luaState.call(0, 0);
        luaState.getGlobal("a");
        assertEquals(LuaType.TABLE, luaState.type(-1));
        luaState.pop(1);

        // load(String)
        luaState.load("b = 2", "=testLoad");
        luaState.call(0, 0);
        luaState.getGlobal("b");
        assertEquals(LuaType.NUMBER, luaState.type(-1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the dump method.
     */
    @Test
    public void testDump() throws Exception {
        // dump()
        luaState.load("c = 3", "=testDump");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        luaState.dump(out);
        byte[] bytes = out.toByteArray();
        assertTrue(bytes.length > 4);
        assertEquals((byte) 27, bytes[0]);
        assertEquals((byte) 'L', bytes[1]);
        System.out.println(bytes[2]);
        assertEquals((byte) 'u', bytes[2]);
        assertEquals((byte) 'a', bytes[3]);
        luaState.pop(1);

        luaState.load(out.toString(), "test3");
        Object[] ret = luaState.call();
        assertEquals(3, ret[0]);
        assertEquals(4, ret.length);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the call method.
     */
    @Test
    public void testCall() throws Exception {
        // call()
        luaState.load("function add(a, b) return a + b end", "test");
        luaState.call(0, 0);
        luaState.getGlobal("add");
        luaState.pushInteger(1);
        luaState.pushInteger(1);
        luaState.call(2, 1);
        assertEquals(2, luaState.toInteger(-1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the global methods.
     */
    @Test
    public void testGlobal() throws Exception {
        // setGlobal()
        luaState.pushNumber(1);
        luaState.setGlobal("a");

        // getGlobal()
        luaState.getGlobal("a");
        assertEquals(LuaType.NUMBER, luaState.type(-1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    // -- Stack push tests

    /**
     * Tests the pushBoolean method.
     */
    @Test
    public void testPushBoolean() throws Exception {
        luaState.pushBoolean(true);
        assertEquals(LuaType.BOOLEAN, luaState.type(1));
        assertEquals(true, luaState.toBoolean(1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the pushByteArray method.
     */
    @Test
    public void testPushByteArray() throws Exception {
        luaState.pushByteArray(new byte[2]);
        assertEquals(LuaType.STRING, luaState.type(1));
        assertArrayEquals(new byte[]{0, 0}, luaState.toByteArray(1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the stack push methods.
     */
    @Test
    public void testPushInteger() throws Exception {
        luaState.pushInteger(1);
        assertEquals(LuaType.NUMBER, luaState.type(1));
        assertEquals(1, luaState.toInteger(1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the pushJavaFunction methods.
     */
    @Test
    public void testPushJavaFunction() throws Exception {
        JavaFunction javaFunction = new SimpleJavaFunction();
        luaState.pushJavaObject(javaFunction);
        assertEquals(LuaType.JAVAFUNCTION, luaState.type(1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the pushJavaObject method.
     */
    @Test
    public void testPushJavaObject() throws Exception {
        luaState.pushJavaObject(null);
        assertEquals(LuaType.NIL, luaState.type(1));
        assertNull(luaState.toJavaObject(1, Object.class));
        luaState.pop(1);

        luaState.pushJavaObject(Boolean.FALSE);
        assertEquals(LuaType.BOOLEAN, luaState.type(1));
        assertEquals(Boolean.FALSE, luaState.toJavaObject(1, Boolean.class));
        luaState.pop(1);

        luaState.pushJavaObject(Double.valueOf(1.0));
        assertEquals(LuaType.NUMBER, luaState.type(1));
        assertEquals(Double.valueOf(1.0), luaState.toJavaObject(1, Double.class));
        luaState.pop(1);

        luaState.pushJavaObject("test");
        assertEquals(LuaType.STRING, luaState.type(1));
        assertEquals("test", luaState.toJavaObject(1, String.class));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the pushJavaObjectRaw method.
     */
    @Test
    public void testPushJavaObjectRaw() throws Exception {
        Object obj = new Object();
        luaState.pushJavaObject(obj);
        assertEquals(LuaType.JAVAOBJECT, luaState.type(1));
        assertSame(obj, luaState.toJavaObjectRaw(1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the pushNil method.
     */
    @Test
    public void testPushNil() throws Exception {
        luaState.pushNil();
        assertEquals(LuaType.NIL, luaState.type(1));
        luaState.pop(1);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the pushNumber method.
     */
    @Test
    public void testPushNumber() throws Exception {
        luaState.pushNumber(1.0);
        assertEquals(LuaType.NUMBER, luaState.type(1));
        assertEquals(1.0, luaState.toNumber(1), 0.0);
        luaState.pop(1);
        luaState.pushStr2Num("1000.4");
        assertEquals(LuaType.NUMBER, luaState.type(-1));
        assertEquals(1000.4, luaState.toNumber(-1), 0.0);


        luaState.pop(1);
        luaState.pushInteger(4);
        System.out.println(luaState.toIntegerX(-1));
        assertEquals(4, luaState.toNumber(-1), 0.0);
        luaState.pop(1);
        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the pushString method.
     */
    @Test
    public void testPushString() throws Exception {
        luaState.pushString("test");
        assertEquals(LuaType.STRING, luaState.type(1));
        assertEquals("test", luaState.toString(1));
        luaState.pop(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= 127; i++) sb.append((char) i);
        for (int i = 8672; i <= 10174; i++) sb.append((char) i);
        String str = sb.toString();
        sb.setLength(0);
        luaState.pushString(str);
        String str1 = luaState.toString(-1);
        luaState.pop(1);
        assertEquals(str, str1);
    }

    // -- Stack type tests

    /**
     * Tests the isBoolean method.
     */
    @Test
    public void testIsBoolean() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // isBoolean()
        assertFalse(luaState.isBoolean(1));
        assertTrue(luaState.isBoolean(2));
        assertFalse(luaState.isBoolean(3));
        assertFalse(luaState.isBoolean(4));
        assertFalse(luaState.isBoolean(5));
        assertFalse(luaState.isBoolean(6));
        assertFalse(luaState.isBoolean(7));
        assertFalse(luaState.isBoolean(8));
        assertFalse(luaState.isBoolean(9));
        assertFalse(luaState.isBoolean(10));
        assertFalse(luaState.isBoolean(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests isCFunction method.
     */
    @Test
    public void testIsCFunctio() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertFalse(luaState.isCFunction(1));
        assertFalse(luaState.isCFunction(2));
        assertFalse(luaState.isCFunction(3));
        assertFalse(luaState.isCFunction(4));
        assertFalse(luaState.isCFunction(5));
        assertFalse(luaState.isCFunction(6));
        assertFalse(luaState.isCFunction(7));
        assertFalse(luaState.isCFunction(8));
        assertTrue(luaState.isCFunction(9));
        assertFalse(luaState.isCFunction(10));
        assertFalse(luaState.isCFunction(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isFunction method.
     */
    @Test
    public void testIsFunction() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertFalse(luaState.isFunction(1));
        assertFalse(luaState.isFunction(2));
        assertFalse(luaState.isFunction(3));
        assertFalse(luaState.isFunction(4));
        assertFalse(luaState.isFunction(5));
        assertFalse(luaState.isFunction(6));
        assertTrue(luaState.isFunction(7));
        assertFalse(luaState.isFunction(8));
        assertTrue(luaState.isFunction(9));
        assertTrue(luaState.isFunction(10));
        assertFalse(luaState.isFunction(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isJavaFunction method.
     */
    @Test
    public void testIsJavaFunction() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertFalse(luaState.isJavaFunction(1));
        assertFalse(luaState.isJavaFunction(2));
        assertFalse(luaState.isJavaFunction(3));
        assertFalse(luaState.isJavaFunction(4));
        assertFalse(luaState.isJavaFunction(5));
        assertFalse(luaState.isJavaFunction(6));
        assertTrue(luaState.isJavaFunction(7));
        assertFalse(luaState.isJavaFunction(8));
        assertFalse(luaState.isJavaFunction(9));
        assertFalse(luaState.isJavaFunction(10));
        assertFalse(luaState.isJavaFunction(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isJavaObject methods.
     */
    @Test
    public void testIsJavaObject() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertTrue(luaState.isJavaObject(1, Object.class));
        assertTrue(luaState.isJavaObject(2, Boolean.class));
        assertTrue(luaState.isJavaObject(3, Double.class));
        assertTrue(luaState.isJavaObject(4, String.class));
        assertTrue(luaState.isJavaObject(5, String.class));
        assertTrue(luaState.isJavaObject(6, Map.class));
        assertTrue(luaState.isJavaObject(7, JavaFunction.class));
        assertTrue(luaState.isJavaObject(8, Object.class));
        assertTrue(luaState.isJavaObject(9, LuaValueProxy.class));
        assertTrue(luaState.isJavaObject(10, LuaValueProxy.class));
        assertFalse(luaState.isJavaObject(11, LuaValueProxy.class));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isJavaObjectRaw method.
     */
    @Test
    public void testIsJavaObjectRaw() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertFalse(luaState.isJavaObjectRaw(1));
        assertFalse(luaState.isJavaObjectRaw(2));
        assertFalse(luaState.isJavaObjectRaw(3));
        assertFalse(luaState.isJavaObjectRaw(4));
        assertFalse(luaState.isJavaObjectRaw(5));
        assertFalse(luaState.isJavaObjectRaw(6));
        assertFalse(luaState.isJavaObjectRaw(7));
        assertTrue(luaState.isJavaObjectRaw(8));
        assertFalse(luaState.isJavaObjectRaw(9));
        assertFalse(luaState.isJavaObjectRaw(10));
        assertFalse(luaState.isJavaObjectRaw(11));

        // isJavaObject(int, Class)
        assertTrue(luaState.isJavaObject(1, Object.class));
        assertTrue(luaState.isJavaObject(2, Boolean.class));
        assertTrue(luaState.isJavaObject(3, Double.class));
        assertTrue(luaState.isJavaObject(4, String.class));
        assertTrue(luaState.isJavaObject(5, String.class));
        assertTrue(luaState.isJavaObject(6, Map.class));
        assertTrue(luaState.isJavaObject(7, JavaFunction.class));
        assertTrue(luaState.isJavaObject(8, Object.class));
        assertTrue(luaState.isJavaObject(9, LuaValueProxy.class));
        assertTrue(luaState.isJavaObject(10, LuaValueProxy.class));


        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isNil methods.
     */
    @Test
    public void testIsNil() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // isNil()
        assertTrue(luaState.isNil(1));
        assertFalse(luaState.isNil(2));
        assertFalse(luaState.isNil(3));
        assertFalse(luaState.isNil(4));
        assertFalse(luaState.isNil(5));
        assertFalse(luaState.isNil(6));
        assertFalse(luaState.isNil(7));
        assertFalse(luaState.isNil(8));
        assertFalse(luaState.isNil(9));
        assertFalse(luaState.isNil(10));
        assertFalse(luaState.isNil(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isNone method.
     */
    @Test
    public void testIsNone() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertFalse(luaState.isNone(1));
        assertFalse(luaState.isNone(2));
        assertFalse(luaState.isNone(3));
        assertFalse(luaState.isNone(4));
        assertFalse(luaState.isNone(5));
        assertFalse(luaState.isNone(6));
        assertFalse(luaState.isNone(7));
        assertFalse(luaState.isNone(8));
        assertFalse(luaState.isNone(9));
        assertFalse(luaState.isNone(10));
        assertTrue(luaState.isNone(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isNoneOrNil method.
     */
    @Test
    public void testIsNoneOrNil() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertTrue(luaState.isNoneOrNil(1));
        assertFalse(luaState.isNoneOrNil(2));
        assertFalse(luaState.isNoneOrNil(3));
        assertFalse(luaState.isNoneOrNil(4));
        assertFalse(luaState.isNoneOrNil(5));
        assertFalse(luaState.isNoneOrNil(6));
        assertFalse(luaState.isNoneOrNil(7));
        assertFalse(luaState.isNoneOrNil(8));
        assertFalse(luaState.isNoneOrNil(9));
        assertFalse(luaState.isNoneOrNil(10));
        assertTrue(luaState.isNoneOrNil(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isNumber method.
     */
    @Test
    public void testIsNumber() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertFalse(luaState.isNumber(1));
        assertFalse(luaState.isNumber(2));
        assertTrue(luaState.isNumber(3));
        assertFalse(luaState.isNumber(4));
        assertTrue(luaState.isNumber(5));
        assertFalse(luaState.isNumber(6));
        assertFalse(luaState.isNumber(7));
        assertFalse(luaState.isNumber(8));
        assertFalse(luaState.isNumber(9));
        assertFalse(luaState.isNumber(10));
        assertFalse(luaState.isNumber(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isString method.
     */
    @Test
    public void testIsString() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertFalse(luaState.isString(1));
        assertFalse(luaState.isString(2));
        assertTrue(luaState.isString(3));
        assertTrue(luaState.isString(4));
        assertTrue(luaState.isString(5));
        assertFalse(luaState.isString(6));
        assertFalse(luaState.isString(7));
        assertFalse(luaState.isString(8));
        assertFalse(luaState.isString(9));
        assertFalse(luaState.isString(10));
        assertFalse(luaState.isString(10));
        assertFalse(luaState.isString(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the isTable method.
     */
    @Test
    public void testIsTable() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertFalse(luaState.isTable(1));
        assertFalse(luaState.isTable(2));
        assertFalse(luaState.isTable(3));
        assertFalse(luaState.isTable(4));
        assertFalse(luaState.isTable(5));
        assertTrue(luaState.isTable(6));
        assertFalse(luaState.isTable(7));
        assertFalse(luaState.isTable(8));
        assertFalse(luaState.isTable(9));
        assertFalse(luaState.isTable(10));
        assertFalse(luaState.isTable(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the stack query methods.
     */
    @Test
    public void testStackQuery() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();
        luaState.pushInteger(-1);
        luaState.pushInteger(0);
        luaState.pushInteger(1);

        // equal()
        for (int i = 1; i <= 10; i++) {
            for (int j = 1; j <= 10; j++) {
                if (i == j) {
                    assertTrue(String.format("%d, %d", i, j), luaState.equal(i, j));
                } else {
                    assertFalse(String.format("%d, %d", i, j), luaState.equal(i, j));
                }
            }
        }
        assertFalse(luaState.equal(20, 21));

        // lessThan()
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(i < j, luaState.lessThan(i - 3, j - 3));
            }
        }
        assertFalse(luaState.lessThan(20, 21));

        // length()
        assertEquals(0, luaState.length(1));

        assertEquals(0, luaState.length(2));
        // assertEquals(0, luaState.length(3)); -> would change types
        assertEquals(4, luaState.length(4));
        assertEquals(1, luaState.length(5));
        assertEquals(1, luaState.length(6));
        assertEquals(0, luaState.length(7));
        assertTrue(luaState.length(8) == 4 || luaState.length(8) == 8);
        assertEquals(0, luaState.length(9));
        assertEquals(0, luaState.length(10));

        // rawEqual()
    }

    /**
     * Tests the rawEqual method.
     */
    @Test
    public void testRawEqual() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        for (int i = 1; i <= 10; i++) {
            for (int j = 1; j <= 10; j++) {
                if (i == j) {
                    assertTrue(String.format("%d, %d", i, j),
                            luaState.rawEqual(i, j));
                } else {
                    assertFalse(String.format("%d, %d", i, j),
                            luaState.rawEqual(i, j));
                }
            }
        }
        assertFalse(luaState.rawEqual(20, 21));
        assertFalse(luaState.rawEqual(1, 21));
        assertFalse(luaState.rawEqual(20, 1));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toBoolean method.
     */
    @Test
    public void testToBoolean() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertFalse(luaState.toBoolean(1));
        assertFalse(luaState.toBoolean(2));
        assertTrue(luaState.toBoolean(3));
        assertTrue(luaState.toBoolean(4));
        assertTrue(luaState.toBoolean(5));
        assertTrue(luaState.toBoolean(6));
        assertTrue(luaState.toBoolean(7));
        assertTrue(luaState.toBoolean(8));
        assertTrue(luaState.toBoolean(9));
        assertTrue(luaState.toBoolean(10));
        assertFalse(luaState.toBoolean(-100));
        assertFalse(luaState.toBoolean(100));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toByteArray method.
     */
    @Test
    public void testToByteArray() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertNull(luaState.toByteArray(1));
        assertNull(luaState.toByteArray(2));
        assertArrayEquals(new byte[]{'1'}, luaState.toByteArray(3));
        assertArrayEquals(new byte[]{'t', 'e', 's', 't'},
                luaState.toByteArray(4));
        assertArrayEquals(new byte[]{'1'}, luaState.toByteArray(5));
        assertNull(luaState.toString(6));
        assertNull(luaState.toString(7));
        assertNull(luaState.toString(8));
        assertNull(luaState.toString(9));
        assertNull(luaState.toString(10));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toInteger method.
     */
    @Test
    public void testToInteger() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertEquals(0, luaState.toInteger(1));
        assertEquals(0, luaState.toInteger(2));
        assertEquals(1, luaState.toInteger(3));
        assertEquals(0, luaState.toInteger(4));
        assertEquals(1, luaState.toInteger(5));
        assertEquals(0, luaState.toInteger(6));
        assertEquals(0, luaState.toInteger(7));
        assertEquals(0, luaState.toInteger(8));
        assertEquals(0, luaState.toInteger(9));
        assertEquals(0, luaState.toInteger(10));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toIntegerX method.
     */
    @Test
    public void testToIntegerX() throws Exception {
        // Setup stack
        makeStack();

        // Test
        assertNull(luaState.toIntegerX(1));
        assertNull(luaState.toIntegerX(2));
        assertEquals(Long.valueOf(1), luaState.toIntegerX(3));
        assertEquals(Double.valueOf(1), luaState.toNumberX(3));
        assertNull(luaState.toIntegerX(4));
        assertEquals(Long.valueOf(1), luaState.toIntegerX(5));
        assertNull(luaState.toIntegerX(6));
        assertNull(luaState.toIntegerX(7));
        assertNull(luaState.toIntegerX(8));
        assertNull(luaState.toIntegerX(9));
        assertNull(luaState.toIntegerX(10));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toJavaFunction method.
     */
    @Test
    public void testToJavaFunction() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertNull(luaState.toJavaFunction(1));
        assertNull(luaState.toJavaFunction(2));
        assertNull(luaState.toJavaFunction(3));
        assertNull(luaState.toJavaFunction(4));
        assertNull(luaState.toJavaFunction(5));
        assertNull(luaState.toJavaFunction(6));
        assertSame(javaFunction, luaState.toJavaFunction(7));
        assertNull(luaState.toJavaFunction(8));
        assertNull(luaState.toJavaFunction(9));
        assertNull(luaState.toJavaFunction(10));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toJavaObject method.
     */
    @Test
    public void testToJavaObject() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertNull(luaState.toJavaObject(1, Object.class));
        assertEquals(Boolean.FALSE, luaState.toJavaObject(2, Boolean.class));
        assertEquals(Double.valueOf(1.0),
                luaState.toJavaObject(3, Double.class));
        assertEquals("test", luaState.toJavaObject(4, String.class));
        assertEquals("1", luaState.toJavaObject(5, String.class));
        assertArrayEquals(new Double[]{1.0}, luaState.toJavaObject(6, Double[].class));
        assertSame(javaFunction, luaState.toJavaObject(7, JavaFunction.class));
        assertSame(object, luaState.toJavaObject(8, Object.class));
        assertTrue(luaState.toJavaObject(9, Object.class) instanceof LuaValueProxy);
        assertTrue(luaState.toJavaObject(10, Object.class) instanceof LuaValueProxy);

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toJavaObjectRaw method.
     */
    @Test
    public void testToJavaObjectRaw() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertNull(luaState.toJavaObjectRaw(1));
        assertNull(luaState.toJavaObjectRaw(2));
        assertNull(luaState.toJavaObjectRaw(3));
        assertNull(luaState.toJavaObjectRaw(4));
        assertNull(luaState.toJavaObjectRaw(5));
        assertNull(luaState.toJavaObjectRaw(6));
        assertNull(luaState.toJavaObjectRaw(7));
        assertSame(object, luaState.toJavaObjectRaw(8));
        assertNull(luaState.toJavaObjectRaw(9));
        assertNull(luaState.toJavaObjectRaw(10));

        // toJavaObject(int, Class)
        assertNull(luaState.toJavaObject(1, Object.class));
        assertEquals(Boolean.FALSE, luaState.toJavaObject(2, Boolean.class));
        assertEquals(Double.valueOf(1.0), luaState.toJavaObject(3, Double.class));
        assertEquals("test", luaState.toJavaObject(4, String.class));
        assertEquals("1", luaState.toJavaObject(5, String.class));
        assertArrayEquals(new Double[]{1.0}, luaState.toJavaObject(6, Double[].class));
        // assertSame(javaFunction, luaState.toJavaObject(7,
        // JavaFunction.class));
        // assertSame(object, luaState.toJavaObject(8, Object.class));
        assertTrue(luaState.toJavaObject(9, Object.class) instanceof LuaValueProxy);
        assertTrue(luaState.toJavaObject(10, Object.class) instanceof LuaValueProxy);
        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toNumber method.
     */
    @Test
    public void testToNumber() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertEquals(0.0, luaState.toNumber(1), 0.0);
        assertEquals(0.0, luaState.toNumber(2), 0.0);
        assertEquals(1.0, luaState.toNumber(3), 0.0);
        assertEquals(0.0, luaState.toNumber(4), 0.0);
        assertEquals(1.0, luaState.toNumber(5), 0.0);
        assertEquals(0.0, luaState.toNumber(6), 0.0);
        assertEquals(0.0, luaState.toNumber(7), 0.0);
        assertEquals(0.0, luaState.toNumber(8), 0.0);
        assertEquals(0.0, luaState.toNumber(9), 0.0);
        assertEquals(0.0, luaState.toNumber(10), 0.0);

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toNumberX method.
     */
    @Test
    public void testToNumberX() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertNull(luaState.toNumberX(1));
        assertNull(luaState.toNumberX(2));
        assertEquals(Double.valueOf(1.0), luaState.toNumberX(3));
        assertNull(luaState.toNumberX(4));
        assertEquals(Double.valueOf(1.0), luaState.toNumberX(5));
        assertNull(luaState.toNumberX(6));
        assertNull(luaState.toNumberX(7));
        assertNull(luaState.toNumberX(8));
        assertNull(luaState.toNumberX(9));
        assertNull(luaState.toNumberX(10));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toPointer method.
     */
    @Test
    public void testToPointer() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertEquals(0L, luaState.toPointer(1));
        assertEquals(0L, luaState.toPointer(2));
        assertEquals(0L, luaState.toPointer(3));
        assertEquals(0L, luaState.toPointer(4));
        assertEquals(0L, luaState.toPointer(5));
        assertTrue(luaState.toPointer(6) != 0L);
        assertTrue(luaState.toPointer(7) != 0L);
        assertTrue(luaState.toPointer(8) != 0L);
        assertTrue(luaState.toPointer(9) != 0L);
        assertTrue(luaState.toPointer(10) != 0L);

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the toString method.
     */
    @Test
    public void testToString() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertNull(luaState.toString(1));
        assertNull(luaState.toString(2));
        assertEquals("1", luaState.toString(3));
        assertEquals("test", luaState.toString(4));
        assertEquals("1", luaState.toString(5));
        assertNull(luaState.toString(6));
        assertNull(luaState.toString(7));
        assertNull(luaState.toString(8));
        assertNull(luaState.toString(9));
        assertNull(luaState.toString(10));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the type method.
     */
    @Test
    public void testType() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertEquals(LuaType.NIL, luaState.type(1));
        assertEquals(LuaType.BOOLEAN, luaState.type(2));
        assertEquals(LuaType.NUMBER, luaState.type(3));
        assertEquals(LuaType.STRING, luaState.type(4));
        assertEquals(LuaType.STRING, luaState.type(5));
        assertEquals(LuaType.TABLE, luaState.type(6));
        assertEquals(LuaType.JAVAFUNCTION, luaState.type(7));
        assertEquals(LuaType.JAVAOBJECT, luaState.type(8));
        assertEquals(LuaType.FUNCTION, luaState.type(9));
        assertEquals(LuaType.FUNCTION, luaState.type(10));
        assertNull(luaState.type(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the typeName method.
     */
    @Test
    public void testTypeName() throws Exception {
        // Setup stack
        luaState.openLibs();
        makeStack();

        // Test
        assertEquals("nil", luaState.typeName(1));
        assertEquals("boolean", luaState.typeName(2));
        assertEquals("number", luaState.typeName(3));
        assertEquals("string", luaState.typeName(4));
        assertEquals("string", luaState.typeName(5));
        assertEquals("table", luaState.typeName(6));
        assertEquals("javafunction", luaState.typeName(7));
        assertEquals("java.lang.Object", luaState.typeName(8));
        assertEquals("function", luaState.typeName(9));
        assertEquals("function", luaState.typeName(10));
        assertEquals("no value", luaState.typeName(11));

        // Finish
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }

    // -- Stack operation tests

    /**
     * Tests the absIndex method.
     */
    @Test
    public void testAbsIndex() throws Exception {
        luaState.pushInteger(0);
        assertEquals(1, luaState.absIndex(1));
        assertEquals(1, luaState.absIndex(-1));
        assertEquals(100, luaState.absIndex(100));
        assertEquals(-98, luaState.absIndex(-100));

        // Finish
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the stack operation methods.
     */
    @Test
    public void testStackOperation() throws Exception {
        // concat()
        luaState.pushString("a");
        luaState.pushString("b");
        luaState.concat(2);
        assertEquals("ab", luaState.toString(1));
        luaState.pop(1);

        // getTop()
        assertEquals(0, luaState.getTop());
        luaState.pushNumber(1);
        assertEquals(1, luaState.getTop());

        // insert()
        luaState.pushNumber(2);
        luaState.insert(1);
        assertArrayEquals(new Object[]{2.0, 1.0}, getStack());

        // pop()
        luaState.pop(1);
        assertArrayEquals(new Object[]{2.0}, getStack());

        // pushValue()
        luaState.pushValue(1);
        assertArrayEquals(new Object[]{2.0, 2.0}, getStack());

        // remove()
        luaState.remove(1);
        assertArrayEquals(new Object[]{2.0}, getStack());

        // replace()
        luaState.pushNumber(1);
        luaState.replace(1);
        assertArrayEquals(new Object[]{1.0}, getStack());

        // setTop()
        luaState.setTop(0);
        assertEquals(0, luaState.getTop());

        // types()
        luaState.pushBoolean(true);
        luaState.pushJavaObject(new Object());
        assertEquals(LuaType.BOOLEAN, luaState.type(1));
        assertEquals(LuaType.JAVAOBJECT, luaState.type(2));

        // Finish
        luaState.pop(2);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the table methods.
     */
    @Test
    public void testTable() throws Exception {
        // newTable()
        luaState.newTable();
        assertEquals(LuaType.TABLE, luaState.type(1));
        luaState.pop(1);

        // newTable(int, int)
        luaState.newTable(1, 1);
        assertEquals(LuaType.TABLE, luaState.type(1));

        // next()
        luaState.pushString("value");
        luaState.setField(1, "key");
        luaState.pushNil();
        assertTrue(luaState.next(1));
        assertEquals("key", luaState.toString(-2));
        assertEquals("value", luaState.toString(-1));
        luaState.pop(1);
        assertFalse(luaState.next(1));

        // rawSet|Get(int)
        luaState.pushInteger(1);
        luaState.pushNumber(10);
        luaState.rawSet(1);
        luaState.pushInteger(1);
        luaState.rawGet(1);
        assertEquals(10.0, luaState.toNumber(-1), 0.0);
        luaState.pop(1);

        // rawSet|Get(int, int)
        luaState.pushNumber(20);
        luaState.rawSet(1, 2);
        luaState.rawGet(1, 2);
        assertEquals(20.0, luaState.toNumber(-1), 0.0);
        luaState.pop(1);

        // set|getTable()
        luaState.pushString("key1");
        luaState.pushString("value1");
        luaState.setTable(1);
        luaState.pushString("key1");
        luaState.getTable(1);
        assertEquals("value1", luaState.toString(-1));
        luaState.pop(1);

        // set|getField()
        luaState.pushString("value2");
        luaState.setField(1, "key2");
        luaState.getField(1, "key2");
        assertEquals("value2", luaState.toString(-1));
        luaState.pop(1);

        // Finish
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    // -- Metatable tests

    /**
     * Tests the metatable methods.
     */
    @Test
    public void testMetatable() throws Exception {
        // setMetaTable()
        luaState.newTable();
        luaState.newTable();
        luaState.pushString("value");
        luaState.setField(2, "key");
        luaState.setMetatable(1);

        // getMetaTable()
        assertTrue(luaState.getMetatable(1));
        assertEquals(LuaType.TABLE, luaState.type(2));
        assertEquals(LuaType.TABLE, luaState.type(-1));
        luaState.getField(2, "key");
        assertEquals("value", luaState.toString(3));
        luaState.pop(3);

        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the environment table methods.
     */
    @Test
    public void testEnvironmentTable() throws Exception {
        // getMetaTable()
        luaState.load("function a() end", "test");
        luaState.call(0, 0);

        // getFEnv()
        luaState.getGlobal("a");
        luaState.getFEnv(1);
        assertEquals(LuaType.TABLE, luaState.type(-1));
        luaState.pop(1);

        // setFEnv()
        luaState.newTable();
        assertTrue(luaState.setFEnv(1));

        // Finish
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the thread methods.
     */
    @Test
    public void testThread() throws Exception {
        // Create thread
        //luaState.openLibs();
        luaState.register(new JavaFunction() {
            public int invoke(LuaState luaState) {
                luaState.pushInteger(luaState.toInteger(1));
                return luaState.yield(1);
            }

            public String getName() {
                return "yieldfunc";
            }
        });
        luaState.load("function run(n)\n" + "print(n);yieldfunc(n + 1)\n" + "coroutine.yield(n + 2)\n" + "return n + 3\n" + "end", "threadtest");
        luaState.call(0, 0);
        luaState.getGlobal("run");

        luaState.newThread();
        assertEquals(LuaType.THREAD, luaState.type(-1));

        // Start
        luaState.pushInteger(1);
        System.out.println(111);
        assertEquals(1, luaState.resume(1, 1));
        System.out.println(222);
        assertEquals(LuaState.YIELD, luaState.status(1));

        assertEquals(2, luaState.getTop());
        assertEquals(2, luaState.toInteger(-1));
        luaState.pop(1);

        // Resume
        assertEquals(1, luaState.resume(1, 0));
        assertEquals(LuaState.YIELD, luaState.status(1));
        assertEquals(2, luaState.getTop());
        assertEquals(3, luaState.toInteger(-1));
        luaState.pop(1);

        // Resume
        assertEquals(1, luaState.resume(1, 0));
        assertEquals(0, luaState.status(1));
        assertEquals(2, luaState.getTop());
        assertEquals(4, luaState.toInteger(-1));
        luaState.pop(1);

        // Cleanup
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the reference functions.
     */
    @Test
    public void testReference() {
        // Create object
        luaState.newTable();
        luaState.pushString("value");
        luaState.setField(1, "key");

        // Get reference
        int ref = luaState.ref(LuaState.REGISTRYINDEX);

        // Get table back via reference
        luaState.rawGet(LuaState.REGISTRYINDEX, ref);
        luaState.getField(1, "key");
        assertEquals("value", luaState.toString(2));
        luaState.pop(2);

        // Release reference
        luaState.unref(LuaState.REGISTRYINDEX, ref);

        // Finish
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the argument check methods.
     */
    @Test
    public void testArgumentChecks() {
        // Simple checks
        luaState.openLibs();
        makeStack();
        assertFalse(luaState.checkBoolean(2));
        assertTrue(luaState.checkBoolean(1, true));
        assertArrayEquals(new byte[]{'t', 'e', 's', 't'}, luaState.checkByteArray(4));
        assertArrayEquals(new byte[1], luaState.checkByteArray(1, new byte[1]));
        assertEquals(1, luaState.checkInteger(3));
        assertEquals(1, luaState.checkInteger(1, 1));
        assertEquals(1.0, luaState.checkNumber(3), 0.0);
        assertEquals(1.0, luaState.checkNumber(1, 1.0), 0.0);
        assertEquals("test", luaState.checkString(4));
        assertEquals("test", luaState.checkString(1, "test"));
        luaState.checkType(3, LuaType.NUMBER);
        luaState.checkArg(3, true, "");

        // Exception check 1
        LuaRuntimeException luaRuntimeException = null;
        try {
            luaState.checkArg(3, false, "msg");
        } catch (LuaRuntimeException e) {
            luaRuntimeException = e;
        }
        assertNotNull(luaRuntimeException);
        assertEquals("bad argument #3 (msg)", luaRuntimeException.getMessage());

        // Exception check 2
        luaRuntimeException = null;
        try {
            luaState.checkNumber(4, 1.0);
        } catch (LuaRuntimeException e) {
            luaRuntimeException = e;
        }
        assertNotNull(luaRuntimeException);
        assertEquals("bad argument #4 (expected number, got string)", luaRuntimeException.getMessage());

        // Cleanup
        luaState.pop(10);
        assertEquals(0, luaState.getTop());
    }
    // -- Argument check tests

    /**
     * Tests the checkArg method.
     */
    @Test
    public void testCheckArg() {
        luaState.checkArg(3, true, "msg");

        // Cleanup
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the checkByteArray methods.
     */
    @Test
    public void testCheckByteArray() {
        luaState.pushString("test");
        assertArrayEquals(new byte[]{'t', 'e', 's', 't'},
                luaState.checkByteArray(1));
        assertArrayEquals(new byte[1], luaState.checkByteArray(2, new byte[1]));

        // Cleanup
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }


    /**
     * Tests the checkInteger methods.
     */
    @Test
    public void testCheckInteger() {
        luaState.pushInteger(1);
        assertEquals(1, luaState.checkInteger(1));
        assertEquals(2, luaState.checkInteger(2, 2));

        // Cleanup
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the checkJavaObject method.
     */
    @Test
    public void testCheckJavaObject() {
        luaState.pushInteger(1);
        assertEquals(Integer.valueOf(1),
                luaState.checkJavaObject(1, Integer.class));
        assertEquals(Integer.valueOf(2),
                luaState.checkJavaObject(2, Integer.class, Integer.valueOf(2)));

        // Cleanup
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the checkNumber methods.
     */
    @Test
    public void testCheckNumber() {
        luaState.pushNumber(1.0);
        assertEquals(1.0, luaState.checkNumber(1), 0.0);
        assertEquals(2.0, luaState.checkNumber(2, 2.0), 0.0);

        // Cleanup
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the checkOption methods.
     */
    @Test
    public void testCheckOption() {
        luaState.pushString("a");
        assertEquals(0, luaState.checkOption(1, new String[]{"a", "b"}));
        assertEquals(1, luaState.checkOption(2, new String[]{"a", "b"}, "b"));

        // Cleanup
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the checkType method.
     */
    @Test
    public void testCheckType() {
        // Simple checks
        luaState.pushNumber(1.0);
        luaState.checkType(1, LuaType.NUMBER);

        // Cleanup
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    /**
     * Tests the check exception message.
     */
    @Test
    public void testCheckMessage() {
        // Message
        LuaRuntimeException luaRuntimeException = null;
        try {
            luaState.checkArg(3, false, "msg");
        } catch (LuaRuntimeException e) {
            luaRuntimeException = e;
        }
        assertNotNull(luaRuntimeException);
        assertEquals("bad argument #3 (msg)", luaRuntimeException.getMessage());

        // Function name
        luaState.register(new JavaFunction() {
            @Override
            public int invoke(LuaState luaState) {
                luaState.checkArg(3, false, "msg");
                return 0;
            }

            @Override
            public String getName() {
                return "f";
            }
        });
        luaState.load("f()", "=testCheckMessageFunction");
        try {
            luaState.call(0, 0);
        } catch (LuaRuntimeException e) {
            luaRuntimeException = e;
        }
        assertNotNull(luaRuntimeException);
        System.out.println(luaRuntimeException.getCause());
        assertEquals(
                "testCheckMessageFunction:1: com.naef.jnlua.LuaRuntimeException: bad argument #3 to 'f' (msg)",
                luaRuntimeException.getCause());

        // Method name
        luaRuntimeException = null;
        luaState.load("t = { m = f } t:m()", "=testCheckMessageMethod");
        try {
            luaState.call(0, 0);
        } catch (LuaRuntimeException e) {
            luaRuntimeException = e;
        }
        assertNotNull(luaRuntimeException);
        assertEquals(
                "testCheckMessageMethod:1: com.naef.jnlua.LuaRuntimeException: bad argument #2 to 'm' (msg)",
                luaRuntimeException.getCause());
    }
    // -- Proxy tests

    /**
     * Tests the getProxy methods.
     */
    @Test
    public void testGetProxy() throws Exception {
        // getProxy(int)
        luaState.pushNumber(1.0);
        LuaValueProxy luaProxy = luaState.getProxy(-1);
        luaState.pop(1);
        luaProxy.pushValue();
        assertEquals(1.0, luaState.toNumber(-1), 0.0);
        luaState.pop(1);

        // Proxy garbage collection
        for (int i = 0; i < 20000; i++) {
            luaState.pushInteger(i);
            luaState.getProxy(-1);
            luaState.pop(1);
        }
        System.gc();

        // getProxy(int, Class)
        luaState.setTop(0);
        luaState.load("return { run = function () hasRun = true end }",
                "=testGetProxy");
        luaState.call(0, 1);
        Runnable runnable = luaState.getProxy(-1, Runnable.class);
        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();
        luaState.getGlobal("hasRun");
        assertTrue(luaState.toBoolean(-1));
        luaState.pop(1);

        // getProxy(int, Class[])
        luaState.pushBoolean(false);
        luaState.setGlobal("hasRun");
        runnable = (Runnable) luaState.getProxy(-1,
                new Class<?>[]{Runnable.class});
        thread = new Thread(runnable);
        thread.start();
        thread.join();
        luaState.getGlobal("hasRun");
        assertTrue(luaState.toBoolean(-1));
        luaState.pop(1);

        // Finish
        luaState.pop(1);
        assertEquals(0, luaState.getTop());
    }

    // -- Private methods

    /**
     * Tests the opening of a library.
     */
    private void testOpenLib(LuaState.Library library, String tableName) {
        luaState.getGlobal(tableName);
        assertEquals(LuaType.NIL, luaState.type(-1));
        luaState.pop(1);
        luaState.openLib(library);
        assertEquals(LuaType.TABLE, luaState.type(-1));
        luaState.getGlobal(tableName);
        assertEquals(LuaType.TABLE, luaState.type(-1));
        assertTrue(luaState.rawEqual(-1, -2));
        luaState.pop(2);
    }

    /**
     * Returns the current stack as Java objects.
     */
    private Object[] getStack() {
        List<Object> objects = new ArrayList<Object>();
        for (int i = 1; i <= luaState.getTop(); i++) {
            switch (luaState.type(i)) {
                case NIL:
                    objects.add(null);
                    break;

                case BOOLEAN:
                    objects.add(Boolean.valueOf(luaState.toBoolean(i)));
                    break;

                case NUMBER:
                    objects.add(Double.valueOf(luaState.toNumber(i)));
                    break;

                case STRING:
                    objects.add(luaState.toString(i));
                    break;
            }
        }
        return objects.toArray(new Object[objects.size()]);
    }

    /**
     * Creates a stack with all types.
     */
    private void makeStack() {
        luaState.pushNil(); // 1
        luaState.pushBoolean(false); // 2
        luaState.pushNumber(1.0); // 3
        luaState.pushString("test"); // 4
        luaState.pushString("1"); // 5
        luaState.newTable(); // 6
        luaState.pushNumber(1.0);
        luaState.rawSet(6, 1);
        javaFunction = new SimpleJavaFunction();
        luaState.pushJavaObject(javaFunction); // 7
        object = new Object();
        luaState.pushJavaObject(object); // 8
        luaState.getGlobal("print"); // 9
        luaState.load("function a() end", "test");
        luaState.call(0, 0);
        luaState.getGlobal("a"); // 10
    }

    // -- Private classes

    /**
     * A simple Lua function.
     */
    private static class SimpleJavaFunction extends JavaFunction {
        @Override
        public int invoke(LuaState luaState) {
            return 0;
        }

        @Override
        public String getName() {
            return "test";
        }
    }
}
