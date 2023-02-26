package me.earth.handlewrapper;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.esotericsoftware.reflectasm.HandleWrapper;
import com.esotericsoftware.reflectasm.WrapperFactory;
import com.esotericsoftware.reflectasm.benchmark.MethodAccessBenchmark;
import me.earth.handlewrapper.util.TestClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.esotericsoftware.reflectasm.ClassAccess.METHOD;

public class TestCases {
    @Test
    public void testFieldHandle() throws Throwable {
        Field field = TestClass.class.getDeclaredField("i");
        field.setAccessible(true);
        MethodHandle getter = MethodHandles.lookup().unreflectGetter(field);
        MethodHandle setter = MethodHandles.lookup().unreflectSetter(field);

        HandleWrapper wrapGetter = WrapperFactory.wrapGetter(getter, field);
        HandleWrapper wrapSetter = WrapperFactory.wrapSetter(setter, field);

        TestClass testClass = new TestClass();
        Assertions.assertEquals(wrapGetter.invoke(testClass), 5);
        wrapSetter.invoke(testClass, 10);
        Assertions.assertEquals(wrapGetter.invoke(testClass), 10);
    }

    @Test
    public void testsetObject() throws Throwable {
        Method method = TestClass.class.getDeclaredMethod("setObject", String.class, int.class);
        method.setAccessible(true);
        HandleWrapper wrapper = WrapperFactory.wrap(MethodHandles.lookup().unreflect(method), method);
        wrapper.invoke(new TestClass(), "a", 10);
    }

    @Test
    public void testSetStaticMethod() throws Throwable {
        Method method = TestClass.class.getDeclaredMethod("setStaticState", int.class);
        method.setAccessible(true);
        Assertions.assertEquals(TestClass.getStaticState(), 5);
        HandleWrapper wrapper = WrapperFactory.wrap(MethodHandles.lookup().unreflect(method), method);
        wrapper.invoke(null, 10);

        Assertions.assertEquals(TestClass.getStaticState(), 10);
    }

    @Test
    public void testConstructorHandle() throws Throwable {
        Constructor<?> ctr = TestClass.class.getDeclaredConstructor(int.class);
        ctr.setAccessible(true);
        System.out.println("condstructor");
        HandleWrapper wrapper = WrapperFactory.wrapConstructor(MethodHandles.lookup().unreflectConstructor(ctr), ctr);
        TestClass testClass = (TestClass) wrapper.invoke(null, 600);

        Assertions.assertEquals(testClass.getI(), 600);
    }


    @Test
    public void testMethodPerformance() throws Throwable {
        Method method = MethodAccessBenchmark.SomeClass.class.getDeclaredMethod("getName");
        method.setAccessible(true);
        MethodHandle handle = MethodHandles.lookup().unreflect(method);
        ClassAccess access = ClassAccess.access(MethodAccessBenchmark.SomeClass.class);
        int index = access.indexOfMethod("getName");
        HandleWrapper wrapper = access.getHandleWithIndex(index, METHOD);
        String value;
        MethodAccessBenchmark.SomeClass dirty = new MethodAccessBenchmark.SomeClass();
        for (int i = 0; i < 10; i++) {
            value = dirty.getName();
            value = (String) wrapper.invoke(dirty);
        }

        String[] values = new String[10000];
        long ms = System.nanoTime();
        //for (int i = 0; i < 300 / 3; i++)
        for (int ii = 0; ii < 300000; ii++) {
            values[ii % 10000] = dirty.getName();
        }
        System.out.println("Direct: " + (System.nanoTime() - ms) / 1e6);


        ms = System.nanoTime();
        //for (int i = 0; i < 300 / 3; i++) {
        for (int ii = 0; ii < 300000; ii++) {
            values[ii % 10000] = (String) wrapper.invoke(dirty);
        }
        System.out.println("WrapperHandle: " + (System.nanoTime() - ms) / 1e6);


        ms = System.nanoTime();
        //for (int i = 0; i < 300 / 3; i++) {
        for (int ii = 0; ii < 300000; ii++) {
            values[ii % 10000] = (String) handle.invoke(dirty);
        }
        System.out.println("MethodHandle: " + (System.nanoTime() - ms) / 1e6);

        ms = System.nanoTime();
        //for (int i = 0; i < 300 / 3; i++) {
        for (int ii = 0; ii < 300000; ii++) {
            values[ii % 10000] = (String) method.invoke(dirty);
        }
        System.out.println("Reflection: " + (System.nanoTime() - ms) / 1e6);
    }
}
