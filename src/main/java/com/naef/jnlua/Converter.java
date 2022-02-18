/*
 * $Id: DefaultConverter.java 161 2012-10-06 13:53:02Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

import com.naef.jnlua.util.AbstractTableList;
import com.naef.jnlua.util.AbstractTableMap;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of the <code>Converter</code> interface.
 */
public class Converter {
    // -- Static
    /**
     * Raw byte array.
     */
    private static final boolean RAW_BYTE_ARRAY = Boolean.parseBoolean(System.getProperty(Converter.class.getPackage().getName() + ".rawByteArray", "false"));
    /**
     * Static instance.
     */
    private static final Converter INSTANCE = new Converter();
    /**
     * Boolean distance map.
     */
    private static final Map<Class<?>, Integer> BOOLEAN_DISTANCE_MAP = new HashMap<>();
    /**
     * Number distance map.
     */
    private static final Map<Class<?>, Integer> NUMBER_DISTANCE_MAP = new HashMap<>();
    /**
     * String distance map.
     */
    private static final Map<Class<?>, Integer> STRING_DISTANCE_MAP = new HashMap<>();
    /**
     * Function distance map.
     */
    private static final Map<Class<?>, Integer> FUNCTION_DISTANCE_MAP = new HashMap<>();
    /**
     * Lua value converters.
     */
    private static final Map<Class<?>, LuaValueConverter<?>> LUA_VALUE_CONVERTERS = new HashMap<>();
    /**
     * Java object converters.
     */
    protected static final Map<Class<?>, JavaObjectConverter<?>> JAVA_OBJECT_CONVERTERS = new HashMap<>();

    static {
        BOOLEAN_DISTANCE_MAP.put(Boolean.class, 1);
        BOOLEAN_DISTANCE_MAP.put(Boolean.TYPE, 1);
        BOOLEAN_DISTANCE_MAP.put(Object.class, 2);
    }

    static {
        NUMBER_DISTANCE_MAP.put(Byte.class, 1);
        NUMBER_DISTANCE_MAP.put(Byte.TYPE, 1);
        NUMBER_DISTANCE_MAP.put(Short.class, 1);
        NUMBER_DISTANCE_MAP.put(Short.TYPE, 1);
        NUMBER_DISTANCE_MAP.put(Integer.class, 1);
        NUMBER_DISTANCE_MAP.put(Integer.TYPE, 1);
        NUMBER_DISTANCE_MAP.put(Long.class, 1);
        NUMBER_DISTANCE_MAP.put(Long.TYPE, 1);
        NUMBER_DISTANCE_MAP.put(Float.class, 1);
        NUMBER_DISTANCE_MAP.put(Float.TYPE, 1);
        NUMBER_DISTANCE_MAP.put(Double.class, 1);
        NUMBER_DISTANCE_MAP.put(Double.TYPE, 1);
        NUMBER_DISTANCE_MAP.put(BigInteger.class, 1);
        NUMBER_DISTANCE_MAP.put(BigDecimal.class, 1);
        NUMBER_DISTANCE_MAP.put(Character.class, 1);
        NUMBER_DISTANCE_MAP.put(Character.TYPE, 1);
        NUMBER_DISTANCE_MAP.put(Object.class, 2);
        NUMBER_DISTANCE_MAP.put(String.class, 3);
        if (!RAW_BYTE_ARRAY) {
            NUMBER_DISTANCE_MAP.put(byte[].class, 3);
        }
    }

    static {
        STRING_DISTANCE_MAP.put(String.class, 1);
        if (!RAW_BYTE_ARRAY) {
            STRING_DISTANCE_MAP.put(byte[].class, 1);
        }
        STRING_DISTANCE_MAP.put(Object.class, 2);
        STRING_DISTANCE_MAP.put(Byte.class, 3);
        STRING_DISTANCE_MAP.put(Byte.TYPE, 3);
        STRING_DISTANCE_MAP.put(Short.class, 3);
        STRING_DISTANCE_MAP.put(Short.TYPE, 3);
        STRING_DISTANCE_MAP.put(Integer.class, 3);
        STRING_DISTANCE_MAP.put(Integer.TYPE, 3);
        STRING_DISTANCE_MAP.put(Long.class, 3);
        STRING_DISTANCE_MAP.put(Long.TYPE, 3);
        STRING_DISTANCE_MAP.put(Float.class, 3);
        STRING_DISTANCE_MAP.put(Float.TYPE, 3);
        STRING_DISTANCE_MAP.put(Double.class, 3);
        STRING_DISTANCE_MAP.put(Double.TYPE, 3);
        STRING_DISTANCE_MAP.put(BigInteger.class, 3);
        STRING_DISTANCE_MAP.put(BigDecimal.class, 3);
        STRING_DISTANCE_MAP.put(Character.class, 3);
        STRING_DISTANCE_MAP.put(Character.TYPE, 3);
    }

    static {
        FUNCTION_DISTANCE_MAP.put(JavaFunction.class, 1);
        FUNCTION_DISTANCE_MAP.put(Object.class, 2);
    }

    static {
        LuaValueConverter<Boolean> booleanConverter = (luaState, index) -> (luaState.toBoolean(index));
        LUA_VALUE_CONVERTERS.put(Boolean.class, booleanConverter);
        LUA_VALUE_CONVERTERS.put(Boolean.TYPE, booleanConverter);

        LuaValueConverter<Byte> byteConverter = (luaState, index) -> ((byte) luaState.toInteger(index));
        LUA_VALUE_CONVERTERS.put(Byte.class, byteConverter);
        LUA_VALUE_CONVERTERS.put(Byte.TYPE, byteConverter);

        LuaValueConverter<Short> shortConverter = (luaState, index) -> ((short) luaState.toInteger(index));
        LUA_VALUE_CONVERTERS.put(Short.class, shortConverter);
        LUA_VALUE_CONVERTERS.put(Short.TYPE, shortConverter);

        LuaValueConverter<Integer> integerConverter = (luaState, index) -> ((int) luaState.toInteger(index));
        LUA_VALUE_CONVERTERS.put(Integer.class, integerConverter);
        LUA_VALUE_CONVERTERS.put(Integer.TYPE, integerConverter);

        LuaValueConverter<Long> longConverter = (luaState, index) -> ((long) luaState.toInteger(index));
        LUA_VALUE_CONVERTERS.put(Long.class, longConverter);
        LUA_VALUE_CONVERTERS.put(Long.TYPE, longConverter);

        LuaValueConverter<Float> floatConverter = (luaState, index) -> ((float) luaState.toNumber(index));
        LUA_VALUE_CONVERTERS.put(Float.class, floatConverter);
        LUA_VALUE_CONVERTERS.put(Float.TYPE, floatConverter);

        LuaValueConverter<Double> doubleConverter = (luaState, index) -> (luaState.toNumber(index));
        LUA_VALUE_CONVERTERS.put(Double.class, doubleConverter);
        LUA_VALUE_CONVERTERS.put(Double.TYPE, doubleConverter);

        LuaValueConverter<BigInteger> bigIntegerConverter = (luaState, index) -> new BigDecimal(luaState.toString(index)).setScale(0, BigDecimal.ROUND_HALF_EVEN).toBigInteger();
        LUA_VALUE_CONVERTERS.put(BigInteger.class, bigIntegerConverter);
        LuaValueConverter<BigDecimal> bigDecimalConverter = (luaState, index) -> new BigDecimal(luaState.toString(index));
        LUA_VALUE_CONVERTERS.put(BigDecimal.class, bigDecimalConverter);
        LuaValueConverter<Character> characterConverter = (luaState, index) -> ((char) luaState.toInteger(index));
        LUA_VALUE_CONVERTERS.put(Character.class, characterConverter);
        LUA_VALUE_CONVERTERS.put(Character.TYPE, characterConverter);
        LuaValueConverter<String> stringConverter = LuaState::toString;
        LUA_VALUE_CONVERTERS.put(String.class, stringConverter);
        if (!RAW_BYTE_ARRAY) {
            LuaValueConverter<byte[]> byteArrayConverter = LuaState::toByteArray;
            LUA_VALUE_CONVERTERS.put(byte[].class, byteArrayConverter);
        }
    }

    static {
        JavaObjectConverter<Boolean> booleanConverter = (luaState, booleanValue) -> luaState.pushBoolean(booleanValue.booleanValue());
        JAVA_OBJECT_CONVERTERS.put(Boolean.class, booleanConverter);
        JAVA_OBJECT_CONVERTERS.put(Boolean.TYPE, booleanConverter);
        JavaObjectConverter<Number> doubleConverter = (luaState, number) -> {
            double d = number.doubleValue();
            long i = number.longValue();
            switch (number.getClass().getSimpleName()) {
                case "Double":
                    if (d == i) luaState.pushInteger(i);
                    else luaState.pushNumber(d);
                    break;
                case "Float":
                    d = Double.valueOf(number.toString());
                    if (d == i) luaState.pushInteger(i);
                    else luaState.pushNumber(Double.valueOf(number.toString()));
                    break;
                case "BigInteger":
                case "BigDecimal":
                    String str1 = number.toString();
                    String str2 = BigDecimal.valueOf(d).toString();
                    if (str1.equals(str2) || str2.equals(str1 + ".0")) {
                        if (d == i) luaState.pushInteger(i);
                        else luaState.pushNumber(d);
                    } else luaState.pushString(str1);
                    break;
                default:
                    if (d == i) luaState.pushInteger(i);
                    else luaState.pushNumber(d);
            }
        };
        JAVA_OBJECT_CONVERTERS.put(Byte.class, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Byte.TYPE, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Short.class, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Short.TYPE, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Integer.class, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Integer.TYPE, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Long.class, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Long.TYPE, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Double.class, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Double.TYPE, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Float.class, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(Float.TYPE, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(BigInteger.class, doubleConverter);
        JAVA_OBJECT_CONVERTERS.put(BigDecimal.class, doubleConverter);
        JavaObjectConverter<Character> characterConverter = (luaState, character) -> luaState.pushInteger(character.charValue());
        JAVA_OBJECT_CONVERTERS.put(Character.class, characterConverter);
        JAVA_OBJECT_CONVERTERS.put(Character.TYPE, characterConverter);
        JavaObjectConverter<String> stringConverter = (luaState, s) -> {
            luaState.pushString(s);
        };
        JAVA_OBJECT_CONVERTERS.put(String.class, stringConverter);
        final JavaObjectConverter<LuaTable> arrayConverter = new JavaObjectConverter<LuaTable>() {
            final void toLua(LuaState luaState, Object o) {
                if (o instanceof Object[]) {
                    convertArray(luaState, (Object[]) o);
                } else if (o instanceof Map) convertMap(luaState, (Map) o);
                else luaState.getConverter().convertJavaObject(luaState, o);
            }

            final void convertArray(LuaState luaState, Object[] obj) {
                final int len = obj.length;
                luaState.newTable(len, 0);
                for (int i = 0; i < len; i++) {
                    toLua(luaState, obj[i]);
                    luaState.rawSet(-2, i + 1);
                }
            }

            final void convertMap(LuaState luaState, Map obj) {
                final int len = obj.keySet().size();
                luaState.newTable(0, len);
                for (Object key : obj.keySet()) {
                    toLua(luaState, key);
                    toLua(luaState, obj.get(key));
                    luaState.setTable(-3);
                }
            }

            @Override
            final public void convert(LuaState luaState, LuaTable obj) {
                if (obj.table == null) luaState.pushNil();
                else if (obj.table instanceof Object[]) convertArray(luaState, (Object[]) obj.table);
                else convertMap(luaState, (Map) obj.table);
            }
        };

        JAVA_OBJECT_CONVERTERS.put(LuaTable.class, arrayConverter);

        if (!RAW_BYTE_ARRAY) {
            JavaObjectConverter<byte[]> byteArrayConverter = LuaState::pushByteArray;
            JAVA_OBJECT_CONVERTERS.put(byte[].class, byteArrayConverter);
        }
    }

    // -- Static methods

    /**
     * Singleton.
     */
    private Converter() {
    }

    // -- Construction

    /**
     * Returns the instance of this class.
     *
     * @return the instance
     */
    public static Converter getInstance() {
        return INSTANCE;
    }

    // -- Java converter methods
    public int getTypeDistance(LuaState luaState, int index, Class<?> formalType) {
        // Handle none
        LuaType luaType = luaState.type(index);
        if (luaType == null) {
            return Integer.MAX_VALUE;
        }

        // Handle void
        if (formalType == Void.TYPE) {
            return Integer.MAX_VALUE;
        }

        // Handle Lua value proxy
        if (formalType == LuaValueProxy.class) {
            return 0;
        }

        // Handle Lua types
        switch (luaType) {
            case NIL:
                return 1;
            case BOOLEAN:
                Integer distance = BOOLEAN_DISTANCE_MAP.get(formalType);
                if (distance != null) {
                    return distance;
                }
                break;
            case NUMBER:
                distance = NUMBER_DISTANCE_MAP.get(formalType);
                if (distance != null) {
                    return distance;
                }
                break;
            case STRING:
                distance = STRING_DISTANCE_MAP.get(formalType);
                if (distance != null) {
                    return distance;
                }
                break;
            case TABLE:
                if (formalType == Map.class || formalType == List.class || formalType.isArray()) {
                    return 1;
                }
                if (formalType == Object.class) {
                    return 2;
                }
                break;
            case JAVAFUNCTION:
                distance = FUNCTION_DISTANCE_MAP.get(formalType);
                if (distance != null) {
                    return distance;
                }
                break;
            case JAVAOBJECT:
                Object object = luaState.toJavaObjectRaw(index);
                if (object != null) {
                    Class<?> type;
                    if (object instanceof TypedJavaObject) {
                        TypedJavaObject typedJavaObject = (TypedJavaObject) object;
                        if (typedJavaObject.isStrong()) {
                            if (formalType.isAssignableFrom(typedJavaObject.getClass())) {
                                return 1;
                            }
                        }
                        type = typedJavaObject.getType();
                    } else {
                        type = object.getClass();
                    }
                    if (formalType.isAssignableFrom(type)) {
                        return 1;
                    }
                }
                break;
        }

        // Handle object
        if (formalType == Object.class) {
            return Integer.MAX_VALUE - 1;
        }

        // Unsupported conversion
        return Integer.MAX_VALUE;
    }

    @SuppressWarnings("unchecked")
    public <T> T convertLuaValue(final LuaState luaState, final int index, final LuaType luaType, Class<T> formalType, Class<?>... subClass) {
        if (luaType == null) {
            throw new IllegalArgumentException("undefined index: " + index);
        }

        // Handle void
        if (formalType == Void.TYPE) {
            throw new ClassCastException(String.format("cannot convert %s to %s", luaState.typeName(index), formalType.getCanonicalName()));
        }

        // Handle Lua value proxy
        if (formalType == LuaValueProxy.class) {
            return (T) luaState.getProxy(index);
        }
        // Handle Lua types
        switch (luaType) {
            case NIL:
                return null;
            case BOOLEAN:
                LuaValueConverter<?> luaValueConverter;
                luaValueConverter = LUA_VALUE_CONVERTERS.get(formalType);
                if (luaValueConverter != null) {
                    return (T) luaValueConverter.convert(luaState, index);
                }
                if (formalType == Object.class) {
                    return (T) Boolean.valueOf(luaState.toBoolean(index));
                }
                break;
            case NUMBER:
                luaValueConverter = LUA_VALUE_CONVERTERS.get(formalType);
                if (luaValueConverter != null) {
                    return (T) luaValueConverter.convert(luaState, index);
                }
                if (formalType == Object.class) {
                    final double d = luaState.toNumber(index);
                    final long l = (long) d;
                    if (l == d) {
                        final int i = (int) l;
                        if (i == l) return (T) Integer.valueOf(i);
                        return (T) Long.valueOf(i);
                    } else return (T) Double.valueOf(d);
                }
                break;
            case STRING:
                luaValueConverter = LUA_VALUE_CONVERTERS.get(formalType);
                if (luaValueConverter != null) {
                    return (T) luaValueConverter.convert(luaState, index);
                }
                if (formalType == Object.class) {
                    return (T) luaState.toString(index);
                }
                break;
            case TABLE:
                if (formalType == Map.class || formalType == Object.class)
                    return (T) new AbstractTableMap(luaState, index, subClass.length > 1 && subClass[0] != null ? subClass[0] : Object.class, subClass.length > 1 && subClass[1] != null ? subClass[1] : Object.class);
                if (formalType == List.class)
                    return (T) new AbstractTableList(luaState, index, subClass.length > 1 && subClass[0] != null ? subClass[0] : Object.class);
                if (formalType.isArray()) {
                    int length = luaState.length(index);
                    Class<?> componentType = formalType.getComponentType();
                    Object array = Array.newInstance(formalType.getComponentType(), length);
                    for (int i = 0; i < length; i++) {
                        luaState.rawGet(index, i + 1);
                        try {
                            Array.set(array, i, convertLuaValue(luaState, -1, componentType));
                        } finally {
                            luaState.pop(1);
                        }
                    }
                    return (T) array;
                }
                if (Modifier.isInterface(formalType.getModifiers())) return luaState.getProxy(index, formalType);
                break;
            case JAVAFUNCTION:
                if (formalType == JavaFunction.class || formalType == Object.class) {
                    return (T) luaState.toJavaFunction(index);
                }
                break;
            case FUNCTION:
                if (formalType != null && Modifier.isInterface(formalType.getModifiers())) {
                    return luaState.getProxy(index, formalType);
                }
                break;
            case USERDATA:
                break;
            case JAVAOBJECT:
                Object object = luaState.toJavaObjectRaw(index);
                if (object != null) {
                    if (object instanceof TypedJavaObject) {
                        TypedJavaObject typedJavaObject = (TypedJavaObject) object;
                        if (typedJavaObject.isStrong()) {
                            if (formalType.isAssignableFrom(typedJavaObject.getClass())) {
                                return (T) typedJavaObject;
                            }
                        }
                        return (T) ((TypedJavaObject) object).getObject();
                    } else {
                        return (T) object;
                    }
                }
                break;
        }

        // Handle object
        if (formalType == Object.class) {
            return (T) luaState.getProxy(index);
        }

        // Unsupported conversion
        throw new ClassCastException(String.format("cannot convert %s to %s", luaState.typeName(index), formalType.getCanonicalName()));
    }

    public <T> T convertLuaValue(LuaState luaState, int index, Class<T> formalType, Class<?>... subClass) {
        // Handle none
        return convertLuaValue(luaState, index, luaState.type(index), formalType, subClass);
    }

    @SuppressWarnings("unchecked")
    public void convertJavaObject(LuaState luaState, Object object) {
        // Handle null
        if (object == null) {
            luaState.pushNil();
            return;
        }

        if (object instanceof JavaFunction) {
            luaState.pushJavaObject(object);
            return;
        }

        if (object instanceof LuaValueProxy) {
            LuaValueProxy luaValueProxy = (LuaValueProxy) object;
            LuaState.checkArg(luaValueProxy.getLuaState().equals(luaState), "Lua value proxy is from a different Lua state");
            luaValueProxy.pushValue();
            return;
        }

        // Handle known Java types
        JavaObjectConverter<Object> javaObjectConverter = (JavaObjectConverter<Object>) JAVA_OBJECT_CONVERTERS.get(object.getClass());
        if (javaObjectConverter != null) {
            javaObjectConverter.convert(luaState, object);
            return;
        }

        // Push as is
        luaState.pushJavaObjectRaw(object);
    }

    // -- Nested types

    /**
     * Converts Lua values.
     */
    private interface LuaValueConverter<T> {
        /**
         * Converts a Lua value to a Java object.
         */
        T convert(LuaState luaState, int index);
    }

    /**
     * Converts Java object.
     */
    protected interface JavaObjectConverter<T> {
        /**
         * Converts a Java object to a Lua value.
         */
        void convert(LuaState luaState, T object);
    }
}
