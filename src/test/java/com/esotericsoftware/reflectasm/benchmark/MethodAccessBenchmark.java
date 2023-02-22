package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.esotericsoftware.reflectasm.HandleWrapper;

import java.lang.reflect.Method;

public class MethodAccessBenchmark extends Benchmark {
    public static String[] result;

    public MethodAccessBenchmark() throws Throwable {
        //ClassAccess.IS_STRICT_CONVERT=true;
        final int count = Benchmark.testRounds;
        final int rounds = Benchmark.testCount;
        Object[] dontCompileMeAway = new Object[count];
        Object[] args = new Object[0];

        ClassAccess<SomeClass> access = ClassAccess.access(SomeClass.class);
        SomeClass someObject = new SomeClass();
        int index = access.indexOfMethod("getName");
        HandleWrapper handle = access.getHandleWithIndex(index, ClassAccess.METHOD);

        Method method = SomeClass.class.getMethod("getName");
        // method.setAccessible(true); // Improves reflection a bit.

        for (int i = 0; i < rounds / 3; i++) {
            for (int ii = 0; ii < count; ii++) {
                dontCompileMeAway[ii] = access.accessor.invokeWithIndex(someObject, index);
                dontCompileMeAway[ii] = method.invoke(someObject);
                dontCompileMeAway[ii] = handle.invoke(someObject);
            }
        }
        warmup = false;
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = access.accessor.invokeWithIndex(someObject, index);
        }
        end("Method Call - ReflectASM");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = handle.invoke(someObject);
        }
        end("Method Call - DirectMethodHandle");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = method.invoke(someObject);
        }
        end("Method Call - Reflection");
        start();
        for (int i = 0; i < rounds; i++) {
            for (int ii = 0; ii < count; ii++)
                dontCompileMeAway[ii] = someObject.getName();
        }
        end("Method Call - Direct");
        result = chart("Method Call");
    }

    public static void main(String[] args) throws Throwable {
        new MethodAccessBenchmark();
    }

    static public class SomeClass {
        private final String name = "something";

        public String getName() {
            return name;
        }
    }
}
