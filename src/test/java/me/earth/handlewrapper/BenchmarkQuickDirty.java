package me.earth.handlewrapper;


import com.esotericsoftware.reflectasm.HandleWrapper;
import com.esotericsoftware.reflectasm.WrapperFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

// Change these values to fit your benchmark, I didn't want to wait 10 minutes everytime

@SuppressWarnings({"unused", "FieldMayBeFinal", "FieldCanBeLocal", "CommentedOutCode"})
public class BenchmarkQuickDirty {
    private static final BenchmarkQuickDirty INSTANCE = new BenchmarkQuickDirty();
    private static final MethodHandle STATIC;
    private static MethodHandle nonFinal;
    private final MethodHandle nonStatic;
    private HandleWrapper wrapper;

    static {
        try {
            Method method = BenchmarkQuickDirty.class.getDeclaredMethod("getX");
            method.setAccessible(true);
            STATIC = MethodHandles.lookup().unreflect(method);
            nonFinal = STATIC;
        } catch (NoSuchMethodException | IllegalAccessException t) {
            throw new IllegalStateException(t);
        }
    }

    public BenchmarkQuickDirty() {
        try {
            Method method = BenchmarkQuickDirty.class.getDeclaredMethod("getX");
            method.setAccessible(true);
            MethodHandle handle = MethodHandles.lookup().unreflect(method);
            nonStatic = handle;
            wrapper = WrapperFactory.wrap(handle, method);

        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public int getX() {
        return 5;
    }

    public int getY() {
        return 5;
    }

    public int benchmarkDirect() {
        return INSTANCE.getX();
    }


    public int benchmarkStaticFinalMethodHandle() throws Throwable {
        return (int) STATIC.invokeExact(INSTANCE);
    }


    public int benchmarkWrapper() throws Throwable {
        return (int) wrapper.invoke(INSTANCE);
    }


    public int benchmarkNonFinalHandle() throws Throwable {
        return (int) nonFinal.invoke(INSTANCE);
    }


    public int benchmarkNonStaticHandle() throws Throwable {
        return (int) nonStatic.invoke(INSTANCE);
    }

}
