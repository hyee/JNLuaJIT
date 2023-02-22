package test;


import com.esotericsoftware.reflectasm.HandleWrapper;
import com.esotericsoftware.reflectasm.Handles;
import me.earth.handlewrapper.util.TestClass;

import java.lang.invoke.MethodHandle;

// $FF: synthetic class
public final class TestClass_setObject extends HandleWrapper {
    private static final MethodHandle HANDLE = Handles.getHandle(1);

    public TestClass_setObject() {
    }

    @Override
    public final Object invoke(Object var1, Object... var2) throws Throwable {
        return HANDLE.invokeExact((TestClass) var1, (String) var2[0], (Integer) var2[1]);
    }
}
