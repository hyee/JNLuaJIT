/*
 * $Id: LuaStateErrorTest.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */

package com.naef.jnlua.test;

import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaRuntimeException;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.LuaValueProxy;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.assertEquals;

/**
 * Throws illegal arguments at the Lua state for error testing.
 */

public class LuaStateErrorTest extends AbstractLuaTest {
    // -- Static
    private static final int HIGH = 10;
    private static final int LOW = -10;
    private static final int EXTREMELY_HIGH = Integer.MAX_VALUE / 8;
    private static final int EXTREMELY_LOW = Integer.MIN_VALUE / 8;

    /**
     * setClassLodaer(ClassLoader) with null class loader.
     */
    @Test(expected = NullPointerException.class)
    public void setNullClassLoader() {
        luaState.setClassLoader(null);
    }

    /**
     * setJavaReflector(JavaReflector) with null Java reflector.
     */
    @Test(expected = NullPointerException.class)
    public void setNullJavaReflector() {
        luaState.setJavaReflector(null);
    }

    /**
     * getMetamethod(Object, Metamethod) with null metamethod.
     */
    @Test(expected = NullPointerException.class)
    public void testNullGetMetamethod() {
        luaState.getMetamethod(null, null);
    }

    /**
     * setConverter(Converter) with null converter.
     */
    @Test(expected = NullPointerException.class)
    public void setNullConverter() {
        luaState.setConverter(null);
    }

    /**
     * openLib(Library) with null library.
     */
    @Test(expected = NullPointerException.class)
    public void testNullOpenLib() {
        luaState.openLib(null);
    }


    /**
     * Tests invoking a method after the Lua state has been closed.
     */

    @Test(expected = IllegalStateException.class)
    public void testClosed() {
        luaState.close();
        luaState.pushInteger(1);
    }

    /**
     * Tests closing the Lua state while running.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalClose() {
        luaState.pushJavaObject(new JavaFunction() {
            @Override
            public int invoke(LuaState luaState) {
                luaState.close();
                return 0;
            }
        });
        luaState.call(0, 0);
    }

    /**
     * Off-index (low)
     */
    @Test(expected = IllegalArgumentException.class)
    public void testLowIndex() {
        luaState.toNumber(LOW);
    }

    /**
     * Off-index (extremely low)
     */
    @Test(expected = IllegalArgumentException.class)
    public void testExtremelyLowIndex() {
        luaState.toNumber(EXTREMELY_LOW);
    }

    /**
     * Off-index (high)
     */
    @Test(expected = IllegalArgumentException.class)
    public void testHighIndex() {
        luaState.toNumber(HIGH);
    }

    /**
     * Off-index (extremely high)
     */
    @Test(expected = IllegalArgumentException.class)
    public void testExtremelyHighIndex() {
        luaState.toNumber(EXTREMELY_HIGH);
    }

    /**
     * gc(GcAction, int) null action
     */
    @Test(expected = NullPointerException.class)
    public void testNullGc() {
        luaState.gc(null, 0);
    }

    /**
     * register(JavaFunction[]) with null function.
     */
    @Test(expected = NullPointerException.class)
    public void testNullFunctionRegister() {
        luaState.register(null);
    }

    /**
     * register(String, JavaFunction[]) with null string.
     */
    @Test(expected = NullPointerException.class)
    public void testNullNameRegister() {
        luaState.register(null, new JavaFunction[0]);
    }

    /**
     * register(String, JavaFunction[]) with null functions.
     */
    @Test(expected = NullPointerException.class)
    public void testNullFunctionsRegister() {
        luaState.register("", null);
    }

    /**
     * load(InputStream, String) with null input stream.
     */
    //@Test(expected = NullPointerException.class)
    public void testNullStreamLoad() throws Exception {
        luaState.load(null, "=testNullStreamLoad", "bt");
    }

    /**
     * load(InputStream, String) with null string.
     */
    @Test(expected = NullPointerException.class)
    public void testNullChunkLoad1() throws Exception {
        luaState.load(new ByteArrayInputStream(new byte[0]), null, "t");
    }

    /**
     * load(String, String) with null string 1.
     */
    @Test(expected = NullPointerException.class)
    public void testNullStringLoad() throws Exception {
        luaState.load(null, "");
    }

    /**
     * load(String, String) with null string 2.
     */
    @Test(expected = NullPointerException.class)
    public void testNullChunkLoad2() throws Exception {
        luaState.load("", null);
    }

    /**
     * load(InputStream, String) with input stream throwing IO exception.
     */
    /**
     * load(InputStream, String) with input stream throwing IO exception.
     */
    @Test(expected = IOException.class)
    public void testIoExceptionLoad() throws Exception {
        luaState.load(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException();
            }
        }, "=testIoExceptionLoad", "bt");
    }

    /**
     * dump(OutputStream) with null output stream.
     */
    @Test(expected = NullPointerException.class)
    public void testNullDump() throws Exception {
        luaState.load("return 0", "nullDump");
        luaState.dump(null);
    }

    /**
     * dump(OutputStream) with an output stream throwing a IO exception.
     */
    @Test(expected = IOException.class)
    public void testIoExceptionDump() throws Exception {
        luaState.load("return 0", "ioExceptionDump");
        luaState.dump(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException();
            }
        });
    }

    /**
     * dump(OutputStream) with insufficient arguments.
     */
    @Test(expected = IllegalStateException.class)
    public void testUnderflowDump() throws Exception {
        luaState.dump(new ByteArrayOutputStream());
    }

    /**
     * Call(int, int) with insufficient arguments.
     */
    @Test(expected = IllegalStateException.class)
    public void testUnderflowCall() {
        luaState.openLibs();
        luaState.getGlobal("print");
        luaState.call(1, 1);
    }

    /**
     * Call(int, int) with an extremely high number of returns.
     */
    @Test(expected = IllegalStateException.class)
    public void testOverflowCall() {
        luaState.openLibs();
        luaState.getGlobal("print");
        luaState.pushString("");
        luaState.call(1, Integer.MAX_VALUE);
    }

    /**
     * Call(int, int) with an illegal number of arguments.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalCall1() {
        luaState.openLibs();
        luaState.getGlobal("print");
        luaState.call(-1, 1);
    }

    /**
     * Call(int, int) with an illegal number of returns.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalCall2() {
        luaState.openLibs();
        luaState.getGlobal("print");
        luaState.pushString("");
        luaState.call(1, -2);
        assertEquals(0, luaState.getTop());
    }

    /**
     * getGlobal(String) with null.
     */
    @Test(expected = NullPointerException.class)
    public void testNullGetGlobal() {
        luaState.getGlobal(null);
    }

    /**
     * setGlobal(String) with null.
     */
    @Test(expected = NullPointerException.class)
    public void testNullSetGlobal() {
        luaState.pushNumber(0.0);
        luaState.setGlobal(null);
    }

    /**
     * setGlobal(String) with insufficient arguments.
     */
    @Test(expected = IllegalStateException.class)
    public void testUnderflowSetGlobal() {
        luaState.setGlobal("global");
    }

    /**
     * setGlobal(String) with insufficient arguments.
     */
    @Test(expected = IllegalStateException.class)
    public void testIllegalSetGlobal() {
        luaState.setGlobal("illegal");
    }

    /**
     * pushJavaFunction(JavaFunction) with null argument.
     */
    @Test(expected = NullPointerException.class)
    public void testNullPushJavaFunction() {
        luaState.pushJavaObject(null);
    }



    /**
     * pushNumber(Double) until stack overflow.
     */
    @Test(expected = IllegalStateException.class)
    public void testStackOverflow() {
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            luaState.pushNumber(0.0);
        }
    }


    /**
     * lessThan(int, int) with illegal types.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalLessThan() {
        luaState.pushNil();
        luaState.pushNumber(0.0);
        luaState.lessThan(1, 2);
    }

    /**
     * length(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalLength() {
        luaState.length(getIllegalIndex());
    }

    /**
     * rawEqual(int, int) with illegal indexes.
     */
    @Test
    public void testIllegalRawEqual() {
        luaState.rawEqual(getIllegalIndex(), getIllegalIndex());
    }

    /**
     * toInteger(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalToInteger() {
        luaState.toInteger(getIllegalIndex());
    }

    /**
     * toJavaFunction(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalToIJavaFunction() {
        luaState.toJavaFunction(getIllegalIndex());
    }

    /**
     * toJavaObject(int) with illegal index and LuaValueProxy type.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalToIJavaObject() {
        luaState.toJavaObject(getIllegalIndex(), LuaValueProxy.class);
    }

    /**
     * toJavaObjectRaw(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalToIJavaObjectRaw() {
        luaState.toJavaObjectRaw(getIllegalIndex());
    }

    /**
     * toNumber(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalToNumber() {
        luaState.toNumber(getIllegalIndex());
    }

    /**
     * toNumber(int) with maximum index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testMaxToNumber() {
        luaState.toNumber(Integer.MAX_VALUE);
    }

    /**
     * toNumber(int) with minimum index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testMinToNumbern() {
        luaState.toNumber(Integer.MIN_VALUE);
    }

    /**
     * toPointer(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalToPointer() {
        luaState.toPointer(getIllegalIndex());
    }

    /**
     * toString(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalToString() {
        luaState.toString(getIllegalIndex());
    }


    /**
     * concat(int) with insufficient arguments.
     */
    @Test(expected = IllegalStateException.class)
    public void testUnderflowConcat1() {
        luaState.concat(1);
    }

    /**
     * concat(int) with insufficient arguments.
     */
    @Test(expected = IllegalStateException.class)
    public void testUnderflowConcat2() {
        luaState.pushString("");
        luaState.pushString("");
        luaState.concat(3);
    }

    /**
     * concat(int) with an illegal number of arguments.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalConcat() {
        luaState.concat(-1);
    }

    /**
     * copy(int, int) with two illegal indexes.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalCopy1() {
        luaState.copy(getIllegalIndex(), getIllegalIndex());
    }

    /**
     * copy(int, int) with one illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalCopy2() {
        luaState.pushInteger(1);
        luaState.copy(1, getIllegalIndex());
    }

    /**
     * insert(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalInsert() {
        luaState.insert(getIllegalIndex());
    }

    /**
     * pop(int) with insufficient arguments.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUnderflowPop() {
        luaState.pop(1);
    }

    /**
     * pop(int) with an illegal number of arguments.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalPop() {
        luaState.pop(-1);
    }

    /**
     * pushValue(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalPushValue() {
        luaState.pushValue(getIllegalIndex());
    }

    /**
     * remove(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRemove() {
        luaState.remove(getIllegalIndex());
    }

    /**
     * replace(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalReplace() {
        luaState.replace(getIllegalIndex());
    }

    /**
     * setTop(int) with an illegal argument.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSetTop() {
        luaState.setTop(-1);
    }

    // -- Table tests

    /**
     * getTable(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalGetTable1() {
        luaState.pushString("");
        luaState.getTable(getIllegalIndex());
    }

    /**
     * getTable(int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalGetTable2() {
        luaState.pushNumber(0.0);
        luaState.pushString("");
        luaState.getTable(1);
    }

    /**
     * getField(int, String) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalGetField1() {
        luaState.getField(getIllegalIndex(), "");
    }

    /**
     * getField(int, String) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalGetField2() {
        luaState.pushNumber(0.0);
        luaState.getField(1, "");
    }


    /**
     * newTable(int, int) with negative record count.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalNewTable2() {
        luaState.newTable(0, -1);
    }

    /**
     * next(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalNext1() {
        luaState.pushNil();
        luaState.next(getIllegalIndex());
    }

    /**
     * next(int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalNext2() {
        luaState.pushNumber(0.0);
        luaState.pushNil();
        luaState.next(1);
    }

    /**
     * rawGet(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRawGet1() {
        luaState.rawGet(getIllegalIndex());
    }

    /**
     * rawGet(int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRawGet2() {
        luaState.pushNumber(0.0);
        luaState.pushString("");
        luaState.rawGet(1);
    }

    /**
     * rawGet(int, int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRawGet3() {
        luaState.rawGet(getIllegalIndex(), 1);
    }

    /**
     * rawGet(int, int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRawGet4() {
        luaState.pushNumber(0.0);
        luaState.rawGet(1, 1);
    }


    /**
     * rawSet(int) with insufficient arguments.
     */
    @Test(expected = IllegalStateException.class)
    public void testUnderflowRawSet() {
        luaState.newTable();
        luaState.rawSet(1);
    }

    /**
     * rawSet(int) with nil index.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testNilRawSet() {
        luaState.newTable();
        luaState.pushNil();
        luaState.pushString("value");
        luaState.rawSet(1);
    }

    /**
     * rawSet(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRawSet1() {
        luaState.pushString("key");
        luaState.pushString("value");
        luaState.rawSet(getIllegalIndex());
    }


    /**
     * rawSet(int, int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRawSet3() {
        luaState.pushNumber(0.0);
        luaState.pushString("value");
        luaState.rawSet(1, 1);
    }


    /**
     * setTable(int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSetTable() {
        luaState.pushNil();
        luaState.pushString("");
        luaState.pushString("");
        luaState.setTable(1);
    }

    /**
     * setTable(int) with nil index.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testNilSetTable() {
        luaState.newTable();
        luaState.pushNil();
        luaState.pushString("");
        luaState.setTable(1);
    }

    /**
     * setTable(int) with insufficient arguments.
     */
    @Test(expected = IllegalStateException.class)
    public void testUnderflowSetTable() {
        luaState.newTable();
        luaState.setTable(1);
    }

    /**
     * setField(int, String) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSetField() {
        luaState.pushNumber(0.0);
        luaState.pushString("");
        luaState.setField(1, "key");
    }

    /**
     * setField(int, String) with null key.
     */
    @Test(expected = NullPointerException.class)
    public void testNullSetField() {
        luaState.newTable();
        luaState.pushString("value");
        luaState.setField(1, null);
    }

    /**
     * rawSet(int, int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRawSet2() {
        luaState.pushNumber(0.0);
        luaState.pushString("value");
        luaState.rawSet(1, 1);
    }


    /**
     * next(int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalNext() {
        luaState.pushNumber(0.0);
        luaState.pushNil();
        luaState.next(1);
    }

    /**
     * setMetaTable(int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSetMetaTable() {
        luaState.newTable();
        luaState.pushNumber(0.0);
        luaState.setMetatable(1);
    }

    /**
     * setFEnv(int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSetFEnv() {
        luaState.openLibs();
        luaState.getGlobal("print");
        luaState.pushNumber(0.0);
        luaState.setFEnv(1);
    }

    /**
     * setField(int, String) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSetField1() {
        luaState.pushString("");
        luaState.setField(getIllegalIndex(), "key");
    }

    /**
     * setField(int, String) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSetField2() {
        luaState.pushNumber(0.0);
        luaState.pushString("");
        luaState.setField(1, "key");
    }

    // -- Metatable tests

    /**
     * setMetatable(int) with invalid table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSetMetatable() {
        luaState.newTable();
        luaState.pushNumber(0.0);
        luaState.setMetatable(1);
    }

    // -- Thread tests

    /**
     * resume(int, int) with insufficient arguments.
     */
    @Test(expected = IllegalStateException.class)
    public void testUnderflowResume() {
        luaState.openLibs();
        luaState.getGlobal("print");
        luaState.newThread();
        luaState.resume(1, 1);
    }


    /**
     * resume(int, int) with invalid thread.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalResume1() {
        luaState.pushNumber(0.0);
        luaState.resume(1, 0);
    }

    /**
     * resume(int, int) with an illegal number of returns.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalResume2() {
        luaState.openLibs();
        luaState.getGlobal("print");
        luaState.newThread();
        luaState.resume(1, -1);
    }


    /**
     * yield(int) with no running thread.
     */
    @Test(expected = IllegalStateException.class)
    public void testIllegalYield() {
        luaState.pushNumber(0.0);
        luaState.yield(0);
    }


    /**
     * ref(int) with illegal table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRef() {
        luaState.pushNumber(0.0);
        luaState.pushNumber(0.0);
        luaState.ref(1);
    }

    /**
     * unref(int, int) with illegal table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalUnref() {
        luaState.pushNumber(0.0);
        luaState.pushNumber(0.0);
        luaState.unref(1, 1);
    }

    /**
     * getProxy(int, Class<?>) with illegal table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalProxy() {
        luaState.pushNumber(0.0);
        luaState.getProxy(1, Runnable.class);
    }

    // -- Private methods

    /**
     * Returns an illegal index.
     */
    private int getIllegalIndex() {
        int multiplier = Math.random() >= 0.5 ? Integer.MAX_VALUE : 1000;
        int index;
        do {
            index = Math.round((float) ((Math.random() - 0.5) * multiplier));
        } while (index >= -15 && index <= 15);
        return index;
    }

    /**
     * status(int) with illegal thread.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalStatus() {
        luaState.pushNumber(0.0);
        luaState.status(1);
    }

    /**
     * yield(int) with no running thread.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalYield1() {
        luaState.register(new JavaFunction() {
            @Override
            public int invoke(LuaState luaState) {
                return luaState.yield(0);
            }

            @Override
            public String getName() {
                return "yieldfunc";
            }
        });
        luaState.load("return yieldfunc()", "=testIllegalYield1");
        luaState.call(0, 0);
    }

    /**
     * yield across C-call boundary.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalYield2() {
        JavaFunction yieldFunction = new JavaFunction() {
            @Override
            public int invoke(LuaState luaState) {
                luaState.load("return coroutine.yield()", "=testIllegalYield2");
                luaState.call(0, 0);
                return 0;
            }
        };
        luaState.pushJavaObject(yieldFunction);
        luaState.newThread();
        luaState.resume(1, 0);
    }

    /**
     * yield(int) with insufficient arguments.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testUnderflowYield() {
        luaState.register(new JavaFunction() {
            @Override
            public int invoke(LuaState luaState) {
                return luaState.yield(1);
            }

            @Override
            public String getName() {
                return "yieldfunc";
            }
        });
        luaState.load("yieldfunc()", "=testUnderflowYield");
        luaState.newThread();
        luaState.resume(1, 0);
    }

    // -- Reference tests

    /**
     * ref(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRef1() {
        luaState.pushNumber(0.0);
        luaState.ref(getIllegalIndex());
    }

    /**
     * ref(int) with illegal table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalRef2() {
        luaState.pushNumber(0.0);
        luaState.pushNumber(0.0);
        luaState.ref(1);
    }

    /**
     * unref(int, int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalUnref1() {
        luaState.newTable();
        luaState.pushNumber(0.0);
        int reference = luaState.ref(1);
        luaState.unref(getIllegalIndex(), reference);
    }

    /**
     * unref(int, int) with illegal table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalUnref2() {
        luaState.pushNumber(0.0);
        luaState.pushNumber(0.0);
        luaState.unref(1, 1);
    }

    // -- Optimization tests

    /**
     * tableSize(int) with illegal table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalTableSize1() {
        luaState.pushNumber(0.0);
        luaState.tableSize(1);
    }

    /**
     * tableSize(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalTableSize2() {
        luaState.tableSize(1);
    }

    /**
     * tableMove(int, int, int, int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalTableMove1() {
        luaState.tableMove(getIllegalIndex(), 1, 1, 0);
    }

    /**
     * tableMove(int, int, int, int) with illegal count.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalTableMove2() {
        luaState.newTable();
        luaState.tableMove(1, 1, 1, -1);
    }

    // -- Argument checking tests

    /**
     * checkArg(int, boolean, String) with false condition.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckArg() {
        luaState.pushBoolean(false);
        luaState.checkArg(1, false, "");
    }


    /**
     * checkInteger(int) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckInteger1() {
        luaState.pushBoolean(false);
        luaState.checkInteger(1);
    }

    /**
     * checkInteger(int, int) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckInteger2() {
        luaState.pushBoolean(false);
        luaState.checkInteger(1, 2);
    }

    /**
     * checkJavaObject(int) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckJavaObject1() {
        luaState.pushBoolean(false);
        luaState.checkJavaObject(1, Integer.class);
    }

    /**
     * checkJavaObject(int, int) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckJavaFunction2() {
        luaState.pushBoolean(false);
        luaState.checkJavaObject(1, Integer.class, Integer.valueOf(0));
    }

    /**
     * checkNumber(int) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckNumber1() {
        luaState.pushBoolean(false);
        luaState.checkNumber(1);
    }

    /**
     * checkNumber(int, double) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckNumber2() {
        luaState.pushBoolean(false);
        luaState.checkNumber(1, 2.0);
    }

    /**
     * checkOption(int, String[]) with null values.
     */
    @Test(expected = NullPointerException.class)
    public void testNullCheckOption1() {
        luaState.pushInteger(1);
        luaState.checkOption(1, null);
    }

    /**
     * checkOption(int, String[], String) with null values.
     */
    @Test(expected = NullPointerException.class)
    public void testNullCheckOption2() {
        luaState.pushInteger(1);
        luaState.checkOption(1, null, "");
    }

    /**
     * checkOption(int, String[]) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckOption1() {
        luaState.pushInteger(1);
        luaState.checkOption(1, new String[]{"test"});
    }

    /**
     * checkOption(int, String[], String) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckOption2() {
        luaState.pushInteger(1);
        luaState.checkOption(1, new String[]{"test"}, "test");
    }

    /**
     * checkOption(int, String[], String) with illegal default option.
     */
    @Test
    public void testIllegalCheckOption3() {
        luaState.checkOption(1, new String[]{"test"}, "");
    }

    /**
     * checkString(int) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckString1() {
        luaState.pushBoolean(false);
        luaState.checkString(1);
    }

    /**
     * checkString(int, String) with illegal argument.
     */
    @Test(expected = LuaRuntimeException.class)
    public void testIllegalCheckString2() {
        luaState.pushBoolean(false);
        luaState.checkString(1, "");
    }

    // -- Proxy tests

    /**
     * getProxy(int, Class[]) with null interface.
     */
    @Test(expected = NullPointerException.class)
    public void testNullGetProxy() {
        luaState.newTable();
        luaState.getProxy(1, new Class<?>[]{null});
    }

    /**
     * getProxy(int) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalGetProxy1() {
        luaState.getProxy(getIllegalIndex());
    }

    /**
     * getProxy(int, Class<?>) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalGetProxy2() {
        luaState.getProxy(getIllegalIndex(), Runnable.class);
    }

    /**
     * getProxy(int, Class<?>) with illegal table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalGetProxy3() {
        luaState.pushNumber(0.0);
        luaState.getProxy(1, Runnable.class);
    }

    /**
     * getProxy(int, Class<?>[]) with illegal index.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalGetProxy4() {
        luaState.getProxy(getIllegalIndex(), new Class<?>[]{Runnable.class});
    }

    /**
     * getProxy(int, Class<?>[]) with illegal table.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalGetProxy5() {
        luaState.pushNumber(0.0);
        luaState.getProxy(1, new Class<?>[]{Runnable.class});
    }


}
