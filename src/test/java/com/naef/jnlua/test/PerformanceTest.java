package com.naef.jnlua.test;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.test.fixture.TestObject;
import org.junit.Test;

/**
 * Created by Administrator on 2017/2/17 0017.
 */
public class PerformanceTest extends AbstractLuaTest {

    //Since Lua access Java via reflection, so compare direct java reflection with lua reflection
    //As a result, lua reflection is about 2-3 times slower than direct refletion

    /**
     * Tests the String.Replace
     */
    int rounds = 100000;

    @Test
    public void testStringReplace() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < 128; i++) sb.append((char) i);
        String str = sb.toString();
        String str1;

        System.out.println("Testing string replace\n=====================");
        long start = System.nanoTime();
        long rate;
        for (int i = 0; i < rounds; i++)
            str1 = str.replace(Character.toString((char) (i % 128 + 1)), Character.toString((char) (i % 128)));
        final long base = System.nanoTime() - start;
        System.out.println(String.format("Java Call(Direct): %.3f ms (1.00 x)", base / 1e6));

        ClassAccess access = ClassAccess.access(String.class);
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++)
            str1 = (String) access.invoke(str, "replace", Character.toString((char) (i % 128 + 1)), Character.toString((char) (i % 128)));
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Java Call(Reflect): %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));

        str1 = "local chr,replace,rounds,str,str1=string.char,string.gsub,rounds,str;local function escape(str) return str:gsub('[%(%)%.%%%+%-%*%?%[%^%$%]]', '%%%1') end;for i = 0,rounds do str1=replace(str,escape(chr(i%128+1)),chr(i%128));end;\n";
        LuaState lua = new LuaState();
        lua.pushGlobal("String", String.class);
        lua.pushGlobal("rounds", rounds);
        lua.pushGlobal("str", str);
        lua.load(str1, "test");
        start = System.nanoTime();
        lua.call();
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Lua(Direct): %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));

        str1 = "local chr,replace,rounds,str,str1=string.char,String.replace,rounds,str;for i = 0,rounds do str1=replace(str,chr(i%128+1),chr(i%128));end;";
        lua.pushGlobal("String", String.class);
        lua.pushGlobal("rounds", rounds);
        lua.pushGlobal("str", str);
        lua.load(str1, "test");
        start = System.nanoTime();
        lua.call();
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Lua -> Java(1): %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));

        str1 = "local String,chr,rounds,str,str1=String,string.char,rounds,str;for i = 0,rounds do str1=String.replace(str,chr(i%128+1),chr(i%128));end;";
        lua.pushGlobal("String", String.class);
        lua.pushGlobal("rounds", rounds);
        lua.pushGlobal("str", str);
        lua.load(str1, "test");
        start = System.nanoTime();
        lua.call();
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Lua -> Java(2): %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));

        str1 = "local replace,rounds,str,str1=String.replace,rounds,str;rp=function(c) return replace(str,c,c) end;";
        lua.pushGlobal("String", String.class);
        lua.pushGlobal("rounds", rounds);
        lua.pushGlobal("str", str);
        lua.load(str1, "test");
        lua.call();
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            lua.getGlobal("rp");
            lua.call(Character.toString((char) (i % 128)));
        }
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Java -> Lua: %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));
    }

    @Test
    public void testField() {
        TestObject obj = new TestObject();

        System.out.println("\nTesting TestObject Field\n=====================");
        long start = System.nanoTime();
        long rate;
        for (int i = 0; i < rounds; i++)
            obj.longField = i;
        final long base = System.nanoTime() - start;
        System.out.println(String.format("Java Call(Direct): %.3f ms (1.00 x)", base / 1e6));

        ClassAccess access = ClassAccess.access(TestObject.class);
        start = System.nanoTime();
        final int index = access.indexOfField("longField");
        for (int i = 0; i < rounds; i++)
            access.set(obj, index, i);
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Java Call(Reflect): %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));

        LuaState lua = new LuaState();

        String str = "local obj,rounds=0,rounds;for i=1,rounds do obj=i end";
        lua.pushGlobal("rounds", rounds);
        lua.load(str, "test2");
        start = System.nanoTime();
        lua.call();
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Lua (Native): %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));

        str = "local obj,rounds=obj,rounds;for i=1,rounds do obj.longField=i end";
        lua.pushGlobal("rounds", rounds);
        lua.pushGlobal("obj", obj);
        lua.load(str, "test1");
        start = System.nanoTime();
        lua.call();
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Lua -> Java(write): %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));

        str = "local obj,rounds,count=obj,rounds,0;for i=1,rounds do count=count+i+obj.longField end";
        lua.pushGlobal("rounds", rounds);
        lua.pushGlobal("obj", obj);
        lua.load(str, "test1");
        start = System.nanoTime();
        lua.call();
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Lua -> Java(read): %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));

        str = "local obj,rounds,count=obj,rounds,0;for i=1,rounds do obj.longField=obj.longField+i end";
        lua.pushGlobal("rounds", rounds);
        lua.pushGlobal("obj", obj);
        lua.load(str, "test1");
        start = System.nanoTime();
        lua.call();
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Lua -> Java(read+write): %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));

        str = "local obj=0; rp=function(c) obj=obj+c end;";
        lua.load(str, "test3");
        lua.call();
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            lua.getGlobal("rp");
            lua.call(i);
        }
        rate = (System.nanoTime() - start);
        System.out.println(String.format("Java -> Lua: %.3f ms (%.2f x) ", rate / 1e6, rate * 1.0 / base));
    }
}
