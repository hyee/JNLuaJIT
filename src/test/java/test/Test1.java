package test;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.esotericsoftware.reflectasm.ClassInfo;

public class Test1 {
    private static final ClassInfo classInfo = ClassAccess.buildIndex(1);
    String f2;

    static String m1(String a) {
        return a;
    }

    String m2(String a) {
        return a;
    }


}
