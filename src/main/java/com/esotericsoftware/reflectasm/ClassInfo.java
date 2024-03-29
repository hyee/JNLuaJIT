package com.esotericsoftware.reflectasm;

/**
 * Created by Will on 2017/2/2.
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class ClassInfo<ANY> {
    public int id;
    public int bucket;
    public String[] fieldNames;
    public Class[] fieldTypes;
    public Integer[] fieldModifiers;
    public String[] methodNames;
    public Class[][] methodParamTypes;
    public Class[] returnTypes;
    public Integer[] methodModifiers;
    public Integer[] constructorModifiers;
    public Class[][] constructorParamTypes;
    public boolean isNonStaticMemberClass;
    public Class<ANY> baseClass;
    public int methodCount;
    public int fieldCount;
    public int constructorCount;
    public Map<String, Integer[]> attrIndex;
    public String[][] methodDescs;
    public String[][] fieldDescs;
    public String[] constructorDescs;
    public Method[] methods;
    public Field[] fields;
    public Constructor[] constructors;
}
