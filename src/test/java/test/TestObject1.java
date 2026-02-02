package test;


import com.esotericsoftware.reflectasm.Accessor;
import com.esotericsoftware.reflectasm.ClassAccess;
import com.esotericsoftware.reflectasm.ClassInfo;

import java.lang.invoke.MethodHandle;

// $FF: synthetic class
public final class TestObject1 implements Accessor<test.TestObject> {
    static MethodHandle[][] methodHandles = new MethodHandle[3][8];
    static final ClassInfo<test.TestObject> classInfo = ClassAccess.buildIndex(1);

    public TestObject1() {
    }

    public MethodHandle[][] getMethodHandles() {
        return methodHandles;
    }

    public ClassInfo<test.TestObject> getInfo() {
        return classInfo;
    }

    @Override
    public final <T, V> T invokeWithIndex(test.TestObject var1, int var2, V... var3) {
        switch (var2) {
            case 0:
                return (T) test.TestObject.func1((String) var3[0]);
            case 1:
                throw new IllegalArgumentException("Method is private:");
            default:
                throw new IllegalArgumentException("Method not found: " + var2);
        }
    }

    @Override
    public final <T> T get(test.TestObject var1, int var2) {
        switch (var2) {
            case 0:
                return (T) test.TestObject.fs;
            case 1:
                return (T) var1.fd;
            case 2:
                throw new IllegalArgumentException("Field is private");
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    @Override
    public final <T, V> void set(test.TestObject var1, int var2, V var3) {
        switch (var2) {
            case 0:
                test.TestObject.fs = (String) var3;
                return;
            case 1:
                var1.fd = (Double) var3;
                return;
            case 2:
                throw new IllegalArgumentException("Field is private");
            default:
                throw new IllegalArgumentException("Field not found: " + var2);
        }
    }

    @Override
    public final <T> test.TestObject newInstanceWithIndex(int var1, T... var2) {
        switch (var1) {
            case 0:
                throw new IllegalArgumentException("Constructor is private");
            case 1:
                return new test.TestObject((Integer) var2[0], (Double) var2[1], (String) var2[2], (Long) var2[3]);
            default:
                throw new IllegalArgumentException("Constructor not found: " + var1);
        }
    }

    @Override
    public final test.TestObject newInstance() {
        return new test.TestObject();
    }
}
