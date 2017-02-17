package com.naef.jnlua.test;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.naef.jnlua.LuaState;
import junit.framework.TestCase;

/**
 * Created by Administrator on 2017/2/17 0017.
 */
public class PerformanceTest extends TestCase {

    //Since Lua access Java via reflection, so compare direct java reflection with lua reflection
    //As a result, lua reflection is about 2-3 times slower than direct refletion
    public void testPerformance1() {
        long start = System.nanoTime();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < 128; i++) sb.append((char) i);
        String str = sb.toString();
        String str1;
        ClassAccess access = ClassAccess.access(String.class);
        int index = access.indexOfMethod(null, "replace", String.class, String.class);
        int rounds = 1000000;
        for (int i = 0; i < rounds; i++)
            str1 = (String) access.invokeWithIndex(str, index, Character.toString((char) (i % 128 + 1)), Character.toString((char) (i % 128)));
        System.out.println(String.format("Java Call: %.3f ms ", (System.nanoTime() - start) / 1e6));

        str1 = "local replace,rounds,tab=String.replace,rounds,{}\n" + "for i = 1,127 do \n" + "    tab[i]=string.char(i);\n" + "end\n" + "local str=table.concat(tab,\"\")\n" + "local str1\n" + "for i = 0,rounds do\n" + "    str1=replace(str,string.char(i%128+1),string.char(i%128));\n" + "end;\n";
        LuaState lua = new LuaState();
        lua.pushGlobal("String", String.class);
        lua.pushGlobal("rounds", rounds);
        lua.load(str1, "test");
        start = System.nanoTime();
        lua.call();
        System.out.println(String.format("Lua  Call: %.3f ms ", (System.nanoTime() - start) / 1e6));
    }
}
