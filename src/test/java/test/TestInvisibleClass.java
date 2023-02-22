package test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class TestInvisibleClass {


    public static void main(String[] args) throws Throwable {

        Object inner = new Outer().getInner();
        Method method = inner.getClass().getDeclaredMethod("m");
        method.setAccessible(true);
        MethodHandle handle = MethodHandles.lookup().unreflect(method);
        handle.invoke(inner);
    }
}
