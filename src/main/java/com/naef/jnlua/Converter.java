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
final class Converter {
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

    private static final byte[] BOOLEAN_TRUE_BYTES = "1".getBytes();
    private static final byte[] BOOLEAN_FALSE_BYTES = "0".getBytes();

    public final static Object processNumber(Number num) {
        if (num == null) return null;
        final Class clazz = num.getClass();
        if (num instanceof BigInteger) {
            final BigInteger bi = (BigInteger) num;
            final long l = bi.longValue();
            return bi.equals(BigInteger.valueOf(l)) ? (Object) l : bi.toString();
        } else if (num instanceof BigDecimal) {
            final BigDecimal bd = ((BigDecimal) num);
            final long l = bd.longValue();
            if (bd.compareTo(BigDecimal.valueOf(l)) == 0) {
                return l;
            }
            final double d = bd.doubleValue();
            if (!Double.isFinite(d) || bd.compareTo(BigDecimal.valueOf(d)) == 0) {
                return d;
            }
            return bd.stripTrailingZeros().toPlainString();
        } else if (clazz == Short.class || clazz == Integer.class || clazz == Long.class || clazz == Byte.class) {
            return num.longValue();
        } else {
            final double d = widen(num);
            //NaN/Infinity: new BigDecimal(num.toString()) throws an unexplained NumberFormatException
            if (!Double.isFinite(d)) {
                return d;
            }
            final BigDecimal bd = new BigDecimal(num.toString());
            if (bd.compareTo(BigDecimal.valueOf(d)) == 0) {
                return d;
            } else {
                return bd.stripTrailingZeros().toPlainString();
            }
        }
    }

    /**
     * Widens a Number to double without exposing a Float's binary error: 0.1f widens to
     * 0.10000000149011612, which LuaJIT then prints as 0.10000000149012, so re-derive it from
     * the shortest decimal form instead. Same rule {@link #processNumber} applies.
     */
    private static double widen(Number num) {
        return num.getClass() == Float.class ? Double.parseDouble(num.toString()) : num.doubleValue();
    }

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
        STRING_DISTANCE_MAP.put(char[].class, 1);
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
        // char[] converter: Lua String �� Java char[]
        LuaValueConverter<char[]> charArrayConverter = (luaState, index) -> {
            String str = luaState.toString(index);
            return str != null ? str.toCharArray() : null;
        };
        LUA_VALUE_CONVERTERS.put(char[].class, charArrayConverter);
    }

    static {
        final JavaObjectConverter<Boolean> booleanConverter = (luaState, booleanValue) -> luaState.pushBoolean(booleanValue.booleanValue());
        JAVA_OBJECT_CONVERTERS.put(Boolean.class, booleanConverter);
        JAVA_OBJECT_CONVERTERS.put(Boolean.TYPE, booleanConverter);
        final JavaObjectConverter<Number> doubleConverter = (luaState, number) -> {
            final Object num = processNumber(number);
            if (num == null) {
                luaState.pushNil();
            } else if (num instanceof Long) {
                final long longValue = (Long) num;
                if (longNeedsString(longValue)) {
                    luaState.pushString(num.toString());
                } else {
                    luaState.pushNumber(longValue);
                }
            } else if (num instanceof Double) {
                luaState.pushNumber((Double) num);
            } else {
                luaState.pushString((String) num);
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
        final JavaObjectConverter<Character> characterConverter = (luaState, character) -> luaState.pushInteger(character.charValue());
        JAVA_OBJECT_CONVERTERS.put(Character.class, characterConverter);
        JAVA_OBJECT_CONVERTERS.put(Character.TYPE, characterConverter);
        final JavaObjectConverter<String> stringConverter = (luaState, s) -> {
            luaState.pushString(s);
        };
        JAVA_OBJECT_CONVERTERS.put(String.class, stringConverter);
        final JavaObjectConverter<LuaTable> arrayConverter = new JavaObjectConverter<LuaTable>() {
            void toLua(LuaState luaState, Object o) {
                if (o instanceof Object[]) {
                    convertArray(luaState, (Object[]) o);
                } else if (o instanceof List) {
                    convertArray(luaState, ((List<?>) o).toArray());
                } else if (o instanceof Map) {
                    convertMap(luaState, (Map<?, ?>) o);
                } else {
                    luaState.getConverter().convertJavaObject(luaState, o);
                }
            }

            void convertArray(LuaState luaState, Object[] obj) {
                // Preferred: pack the whole array into one buffer the native side replays element by
                // element. That is the only form that carries a type per element, so it covers the
                // mixed arrays (and it needs one array access instead of one per element).
                if (obj != null && obj.length > 0) {
                    final byte[] packed = packArray(obj);
                    if (packed != null) {
                        luaState.tablePushPackedArray(packed);
                        return;
                    }
                    // Fallback for arrays the packer refuses: retype a uniformly typed one so the
                    // native expansion (one element type per nesting level) can be used - a mixed or
                    // unsupported one gets every element degraded and used to crash the JVM, which is
                    // the "BUG on query performance_schema.accounts" this call was commented out for.
                    final Object typed = retypeUniformArray(obj);
                    if (typed != null) {
                        luaState.tablePushArray((Object[]) typed);
                        return;
                    }
                }

                final int len = obj.length;
                luaState.newTable(len, 0);
                for (int i = 0; i < len; i++) {
                    toLua(luaState, obj[i]);
                    luaState.rawSet(-2, i + 1);
                }
            }

            void convertMap(LuaState luaState, Map<?, ?> obj) {
                // Same preference as convertArray: pack the whole map into one buffer the native side
                // replays in a single call, which replaces one JNI push per key and per value plus one
                // protected setTable per entry with one array access and one pcall for the whole map.
                final byte[] packed = packMap(obj);
                if (packed != null) {
                    luaState.tablePushPackedArray(packed);
                    return;
                }

                final int len = obj.keySet().size();
                luaState.newTable(0, len);
                for (Object key : obj.keySet()) {
                    toLua(luaState, key);
                    toLua(luaState, obj.get(key));
                    luaState.setTable(-3);
                }
            }

            @Override
            public void convert(LuaState luaState, LuaTable obj) {
                if (obj.table == null) luaState.pushNil();
                else if (obj.table instanceof Object[]) convertArray(luaState, (Object[]) obj.table);
                else convertMap(luaState, (Map<?, ?>) obj.table);
            }
        };

        JAVA_OBJECT_CONVERTERS.put(LuaTable.class, arrayConverter);

        if (!RAW_BYTE_ARRAY) {
            final JavaObjectConverter<byte[]> byteArrayConverter = LuaState::pushByteArray;
            JAVA_OBJECT_CONVERTERS.put(byte[].class, byteArrayConverter);
        }

        // char[] converter: Java char[] �� Lua String
        final JavaObjectConverter<char[]> charArrayConverter = (luaState, charArray) -> {
            if (charArray == null) {
                luaState.pushNil();
            } else {
                luaState.pushString(new String(charArray));
            }
        };
        JAVA_OBJECT_CONVERTERS.put(char[].class, charArrayConverter);
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
            case FUNCTION:
                distance = FUNCTION_DISTANCE_MAP.get(formalType);
                if (distance != null) {
                    return distance;
                }
                break;
            case LIGHTUSERDATA:
                if (formalType == Object.class) {
                    return 2;
                }
                break;
            case USERDATA:
                if (formalType == Object.class) {
                    return 2;
                }
                break;
            case THREAD:
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
                        TypedJavaObject<?> typedJavaObject = (TypedJavaObject<?>) object;
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
                        return (T) (Long)l;
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
                // BUG FIX: When formalType is Object.class, return a proxy instead of AbstractTableMap
                // This preserves backward compatibility with JSR223 ScriptEngine API
                // where scriptEngine.get("tableName") should return a LuaValueProxy that can be
                // passed back to Lua methods like invokeMethod()
                if (formalType == Map.class) {
                    return (T) new AbstractTableMap(luaState, index, subClass.length > 1 && subClass[0] != null ? subClass[0] : Object.class, subClass.length > 1 && subClass[1] != null ? subClass[1] : Object.class);
                } else if (formalType == List.class) {
                    return (T)  new AbstractTableList(luaState, index, subClass.length > 0 && subClass[0] != null ? subClass[0] : Object.class);
                } else if (formalType.isArray()) {
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
                } else if (formalType == Object.class) {
                    // Return a simple LuaValueProxy without requiring additional interfaces
                    return (T) luaState.getProxy(index);
                } else if (Modifier.isInterface(formalType.getModifiers())) {
                    return luaState.getProxy(index, formalType);
                }
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
            case LIGHTUSERDATA:
                if (formalType == Object.class) {
                    return (T) Long.valueOf(luaState.toPointer(index));
                }
                break;
            case USERDATA:
                break;
            case THREAD:
                if (formalType == Object.class) {
                    // For now, treat as a general object since LuaState doesn't have toThread method
                    return (T) luaState.toJavaObjectRaw(index);
                }
                break;
            case JAVAOBJECT:
                Object object = luaState.toJavaObjectRaw(index);
                if (object != null) {
                    if (object instanceof TypedJavaObject) {
                        TypedJavaObject<?> typedJavaObject = (TypedJavaObject<?>) object;
                        if (typedJavaObject.isStrong()) {
                            if (formalType.isAssignableFrom(typedJavaObject.getClass())) {
                                return (T) typedJavaObject;
                            }
                        }
                        return (T) ((TypedJavaObject<?>) object).getObject();
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
            LuaState proxyState = luaValueProxy.getLuaState();
            // CRITICAL SAFETY: Check if proxy's LuaState is still valid (not released/closed)
            // When proxy is cached (e.g., in JavaFunction iterator), the underlying LuaState may have been closed
            if (proxyState == null) {
                // Proxy is stale (LuaState closed) - push nil to avoid NPE
                luaState.pushNil();
                return;
            }
            // BUG FIX: Use == instead of equals() for identity comparison
            // LuaState does not override equals(), so equals() is equivalent to ==
            // However, we should use == directly for clarity and avoid potential issues
            LuaState.checkArg(proxyState == luaState, "Lua value proxy is from a different Lua state");
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

    /**
     * Materialises the value that build_args handed over as a registry ref.
     */
    private Object loadRef(LuaState L, byte[] refBytes, LuaType type, Class<?> returnClass) {
        final int ref = ((refBytes[0] & 0xFF) << 24) |
                ((refBytes[1] & 0xFF) << 16) |
                ((refBytes[2] & 0xFF) << 8) |
                (refBytes[3] & 0xFF);
        L.rawGet(LuaState.REGISTRYINDEX, ref);
        try {
            return convertLuaValue(L, L.getTop(), type, returnClass);
        } finally {
            L.unref(LuaState.REGISTRYINDEX, ref);
            L.pop(1);
        }
    }

    public final boolean getLuaValues(LuaState L, boolean skipLoadTable, Object[] args, byte[] argTypes, Object[] params, LuaType[] types, Class<?> returnClass) {
        boolean hasTable = false;
        for (int i = 0; i < types.length; i++) {
            types[i] = LuaType.get(argTypes[i]);
            switch (types[i]) {
                case TABLE:
                    params[i] = args[i];
                    hasTable = true;
                    if (!skipLoadTable && (args[i] instanceof byte[])) {
                        // ZERO-COPY: Decode ref from byte[4] (big-endian int32)
                        params[i] = loadRef(L, (byte[]) args[i], types[i], returnClass);
                    }
                    break;
                case FUNCTION:
                case USERDATA:
                    if (args[i] instanceof byte[]) {
                        // tableGet/tableNext: the native call popped the value and left a ref, so
                        // the stack holds nothing at i + 1 and getProxy would throw "illegal index".
                        params[i] = loadRef(L, (byte[]) args[i], types[i], returnClass);
                    } else {
                        // JavaFunction callback: the arguments are still on the stack at 1..n.
                        params[i] = convertLuaValue(L, i + 1, types[i], returnClass);
                    }
                    break;
                case JAVAOBJECT:
                    params[i] = args[i];
                    if (params[i] instanceof TypedJavaObject) {
                        if (!((TypedJavaObject<?>) params[i]).isStrong())
                            params[i] = ((TypedJavaObject<?>) params[i]).getObject();
                    }
                    break;
                case BOOLEAN:
                    params[i] = ((byte[]) args[i])[0] == '1';
                    break;
                case NUMBER:
                    // ZERO-COPY: All numbers now stored as byte[8] (IEEE 754 double)
                    if (args[i] instanceof byte[]) {
                        byte[] numBytes = (byte[]) args[i];
                        long bits = ((long) (numBytes[0] & 0xFF) << 56) |
                                ((long) (numBytes[1] & 0xFF) << 48) |
                                ((long) (numBytes[2] & 0xFF) << 40) |
                                ((long) (numBytes[3] & 0xFF) << 32) |
                                ((long) (numBytes[4] & 0xFF) << 24) |
                                ((long) (numBytes[5] & 0xFF) << 16) |
                                ((long) (numBytes[6] & 0xFF) << 8) |
                                (long) (numBytes[7] & 0xFF);
                        double d = Double.longBitsToDouble(bits);
                        //< not <=: Long.MAX_VALUE promotes to 2^63, where (long) d saturates one below d
                        if (d >= Long.MIN_VALUE && d < Long.MAX_VALUE && Math.floor(d) == d) {
                            params[i] = (long) d;
                        } else {
                            params[i] = d;
                        }
                    } else {
                        // Should not happen, but handle gracefully
                        params[i] = args[i];
                    }
                    break;
                default:
                    if (args[i] instanceof byte[]) {
                        params[i] = new String(((byte[]) args[i]), LuaState.UTF8);
                    } else params[i] = args[i];
                    break;
            }
        }
        return hasTable;
    }

    /**
     * Converts Java objects to Lua types with unified storage optimization.
     * Primitive types (BOOLEAN, STRING, NUMBER) are serialized to byte[] and stored directly in args[].
     * This allows JNI zero-copy access using GetPrimitiveArrayCritical.
     *
     * @param L         LuaState instance
     * @param args      Object array (keyPair or paramArgs) - unified storage for all types
     * @param types     Type array (keyTypes or paramTypes)
     * @param range     Number of elements to convert
     * @param checkNull Whether to check null for first element (table key)
     */
    public final void toLuaType(LuaState L, Object[] args, byte[] types, int range, boolean checkNull) {
        for (int i = 0; i < range; i++) {
            final Object arg = args[i];
            if (arg == null) {
                if (i == 0 && checkNull) throw new NullPointerException("Lua table key must not be null");
                types[i] = LuaType.NIL.id;
                continue;
            }

            int type = 0;
            final Class<?> clazz = arg.getClass();
            if (clazz == Boolean.class) {
                // OPTIMIZED: Serialize to byte[] for zero-copy JNI access
                type = LuaType.BOOLEAN.id;
                args[i] = ((Boolean) arg) ? BOOLEAN_TRUE_BYTES : BOOLEAN_FALSE_BYTES;
            } else if (clazz == String.class) {
                type = LuaType.STRING.id;
                args[i] = ((String) arg).getBytes(LuaState.UTF8);
            } else if (clazz == byte[].class) {
                args[i] = (byte[]) arg;
                type = LuaType.STRING.id;
            } else if (clazz == char[].class) {
                args[i] = new String((char[]) arg).getBytes(LuaState.UTF8);
                type = LuaType.STRING.id;
            } else if (arg instanceof Number) {
                args[i] = processNumber((Number) arg);
                if (args[i] instanceof Number) {
                    type = LuaType.NUMBER.id;
                    // OPTIMIZED: Serialize NUMBER as 8-byte double to byte[]
                    long bits = Double.doubleToRawLongBits(((Number) args[i]).doubleValue());
                    args[i] = new byte[]{
                            (byte) (bits >>> 56),
                            (byte) (bits >>> 48),
                            (byte) (bits >>> 40),
                            (byte) (bits >>> 32),
                            (byte) (bits >>> 24),
                            (byte) (bits >>> 16),
                            (byte) (bits >>> 8),
                            (byte) bits
                    };
                } else {
                    type = LuaType.STRING.id;
                    args[i] = args[i].toString().getBytes(LuaState.UTF8);
                }
            } else if (JavaFunction.class.isAssignableFrom(clazz)) {
                type = LuaType.JAVAFUNCTION.id;
            } else if (clazz == LuaTable.class) {
                type = LuaType.TABLE.id;
            } else {
                type = LuaType.JAVAOBJECT.id;
            }
            types[i] = (byte) type;
        }
    }

    private Object[] resetStringArray(Object[] ary, boolean reuse, int types, boolean isBoolean) {
        Object[] newAry = reuse ? ary : new Object[ary.length];
        for (int i = 0; i < ary.length; i++) {
            if (ary[i] == null) {
                newAry[i] = null;
            } else if (types >= 16) {
                newAry[i] = resetStringArray((Object[]) ary[i], reuse, types - 16, isBoolean);
            } else if (isBoolean)
                newAry[i] = (((Boolean) ary[i]) ? BOOLEAN_TRUE_BYTES : BOOLEAN_FALSE_BYTES);
            else
                newAry[i] = (ary[i].toString()).getBytes(LuaState.UTF8);
        }
        return newAry;
    }

    private Object[] resetNumberArray(Object[] ary, boolean reuse, int types) {
        Object[] newAry = reuse ? ary : new Object[ary.length];
        for (int i = 0; i < ary.length; i++) {
            if (ary[i] == null) {
                newAry[i] = null;
            } else if (types >= 16) {
                newAry[i] = resetNumberArray((Object[]) ary[i], reuse, types - 16);
            } else {
                // Serialize Number to byte[8] for zero-copy JNI access
                double dval = widen((Number) ary[i]);
                long bits = Double.doubleToRawLongBits(dval);
                newAry[i] = new byte[]{
                        (byte) (bits >>> 56),
                        (byte) (bits >>> 48),
                        (byte) (bits >>> 40),
                        (byte) (bits >>> 32),
                        (byte) (bits >>> 24),
                        (byte) (bits >>> 16),
                        (byte) (bits >>> 8),
                        (byte) bits
                };
            }
        }
        return newAry;
    }

    /**
     * Returns {@code arr} when its declared type already carries an element type the C-side array
     * expansion can use, a retyped copy when every (possibly nested) element shares one such type,
     * and {@code null} when no single type describes the array - the caller then has to convert
     * element by element.
     */
    private static Object retypeUniformArray(Object arr) {
        final Class<?> arrayClass = arr.getClass();
        if (!arrayClass.isArray() || arrayClass.getComponentType().isPrimitive())
            return null;
        if (staticallyTypedForFastPath(arrayClass))
            return arr;
        final Object[] a = (Object[]) arr;
        final int n = a.length;
        if (n == 0)
            return null;
        Class<?> elementClass = null;
        for (int i = 0; i < n; i++) {
            final Object o = a[i];
            if (o == null)
                continue;
            final Class<?> c = o.getClass();
            if (elementClass == null)
                elementClass = c;
            else if (elementClass != c)
                return null;
        }
        if (elementClass == null)
            return null;
        if (!elementClass.isArray()) {
            if (!isFastPathLeaf(elementClass))
                return null;
            final Object copy = Array.newInstance(elementClass, n);
            System.arraycopy(a, 0, copy, 0, n);
            return copy;
        }
        Object copy = null;
        Class<?> componentType = null;
        for (int i = 0; i < n; i++) {
            final Object o = a[i];
            if (o == null) {
                if (copy != null)
                    Array.set(copy, i, null);
                continue;
            }
            final Object retyped = retypeUniformArray(o);
            if (retyped == null)
                return null;
            if (copy == null) {
                componentType = retyped.getClass();
                copy = Array.newInstance(componentType, n);
            } else if (retyped.getClass() != componentType) {
                return null;
            }
            Array.set(copy, i, retyped);
        }
        return copy;
    }

    private static boolean staticallyTypedForFastPath(Class<?> arrayClass) {
        Class<?> c = arrayClass;
        while (c.isArray())
            c = c.getComponentType();
        return c != Object.class && isFastPathLeaf(c);
    }

    /**
     * Element types the C-side array expansion handles verbatim. Everything else (BigDecimal and
     * friends, arbitrary objects, primitive arrays) would be narrowed to a double or wrapped as a
     * Java object instead of converted, so those arrays keep going through the per-element loop.
     */
    private static boolean isFastPathLeaf(Class<?> c) {
        return c == String.class || c == Boolean.class || c == Byte.class || c == Short.class
                || c == Integer.class || c == Long.class || c == Float.class || c == Double.class;
    }

    /* ---- Packed array wire format ----
     * One buffer carries a whole (possibly nested) array or map: every element is one tag plus its
     * payload, so the C side replays it with a single array access and no per-element JNI, and - unlike
     * the one-type-per-level format - it can carry a DIFFERENT type per element (the "mixed" case,
     * e.g. a result row holding both strings and numbers, used to be forced onto the slow loop).
     * The tags are mirrored in jnlua.c. Raw Java objects cannot be inlined, so a structure holding one
     * is declined by packArray()/packMap() and converted element by element instead. */
    static final byte PACK_NIL = 0;
    static final byte PACK_BOOLEAN = 1;
    static final byte PACK_NUMBER = 3;
    static final byte PACK_STRING = 4;
    static final byte PACK_ARRAY = 16;
    static final byte PACK_MAP = 32;
    /** Value of {@code keyTypes[1]} telling the native side the value is a packed structure. */
    static final byte PACKED_ARRAY_TYPE = 15;

    private static final long PACK_MAX_INTEGER = 1L << 53;

    /* Mirrors PACK_MAX_DEPTH in jnlua.c. Packing declines (returns null, so the caller falls back to the
     * per-element loop) once nesting reaches this depth, so the native replay is never handed a buffer
     * deep enough to overflow its own recursion. Far beyond any real structure; the fallback keeps deep
     * ones correct, just slower. */
    private static final int PACK_MAX_DEPTH = 1000;

    /* A Long whose magnitude reaches 2^53 no longer round-trips through a double exactly, so it must
     * cross as its decimal string. Shared by the slow per-element converter (doubleConverter) and the
     * packed replay (packElement) so the two paths can never disagree - the digest equivalence of the
     * "long at 2^53" and "long MAX/MIN" shapes depends on it. */
    private static boolean longNeedsString(long v) {
        return v >= PACK_MAX_INTEGER || v <= -PACK_MAX_INTEGER;
    }

    private static final class PackedBuffer {
        byte[] buf = new byte[1024];
        int len;

        void need(int n) {
            if (len + n <= buf.length) return;
            int cap = buf.length * 2;
            while (cap < len + n) cap *= 2;
            buf = java.util.Arrays.copyOf(buf, cap);
        }

        void u8(int v) {
            need(1);
            buf[len++] = (byte) v;
        }

        void i32(int v) {
            need(4);
            setI32(len, v);
            len += 4;
        }

        /* Writes the same big-endian int32 at an absolute offset, so a reserved slot (a map's pair
         * count, patched once the real number of entries is known) reuses i32's encoding. */
        void setI32(int at, int v) {
            buf[at] = (byte) (v >>> 24);
            buf[at + 1] = (byte) (v >>> 16);
            buf[at + 2] = (byte) (v >>> 8);
            buf[at + 3] = (byte) v;
        }

        void i64(long v) {
            i32((int) (v >>> 32));
            i32((int) v);
        }

        void raw(byte[] b) {
            need(b.length);
            System.arraycopy(b, 0, buf, len, b.length);
            len += b.length;
        }

        void string(String s) {
            final byte[] b = s.getBytes(LuaState.UTF8);
            u8(PACK_STRING);
            i32(b.length);
            raw(b);
        }
    }

    /**
     * Packs an array for {@code LuaState.tablePushPackedArray}. Returns {@code null} when some element
     * is not a value the native replay can reproduce exactly - a raw Java object (the Java-side
     * conversion path for those is not reachable from C), a List/LuaTable/proxy (which turn into Lua
     * values rather than into raw objects), or a primitive array - in which case the caller falls back
     * to the per-element conversion.
     */
    static byte[] packArray(Object[] arr) {
        final PackedBuffer p = new PackedBuffer();
        try {
            if (!packElement(p, arr, 0)) return null;
        } catch (Throwable t) {
            return null;
        }
        return java.util.Arrays.copyOf(p.buf, p.len);
    }

    /** Packs a map for {@code LuaState.tablePushPackedArray}; same contract as {@link #packArray}. */
    static byte[] packMap(Map<?, ?> map) {
        final PackedBuffer p = new PackedBuffer();
        try {
            if (!packMapEntries(p, map, 0)) return null;
        } catch (Throwable t) {
            return null;
        }
        return java.util.Arrays.copyOf(p.buf, p.len);
    }

    /**
     * Emits {@code PACK_MAP} and then the entries as alternating key/value elements. The pair count is
     * patched in after the loop rather than taken from {@code map.size()}, so it can never disagree
     * with what was actually written (a map whose iterator yields more or fewer entries than its size
     * reports would otherwise make the native replay read past the buffer). A null key is declined
     * deliberately: the replay would surface the Lua-level "table index is nil", while the per-entry
     * path rejects it earlier with IllegalArgumentException("illegal type") - see jcall_settable's
     * checknil - and keeping that error is worth losing the fast path on a map Lua cannot hold anyway.
     * A NaN key (Double/Float) is declined for the same reason: lua_rawset would raise "table index is
     * NaN" inside the replay, so the caller falls back to the per-entry path, which raises it cleanly.
     * Nesting deeper than {@link #PACK_MAX_DEPTH} is also declined, so the native recursion stays bounded.
     */
    private static boolean packMapEntries(PackedBuffer p, Map<?, ?> map, int depth) {
        if (depth >= PACK_MAX_DEPTH) return false;
        p.u8(PACK_MAP);
        final int countAt = p.len;
        p.i32(0);
        int n = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            final Object k = e.getKey();
            if (k == null || isNaNKey(k)) return false;
            if (!packElement(p, k, depth + 1)) return false;
            if (!packElement(p, e.getValue(), depth + 1)) return false;
            n++;
        }
        p.setI32(countAt, n);
        return true;
    }

    /** True when {@code k} is a Double or Float NaN, which Lua rejects as a table key. */
    private static boolean isNaNKey(Object k) {
        return (k instanceof Double && ((Double) k).isNaN())
            || (k instanceof Float && ((Float) k).isNaN());
    }

    private static boolean packElement(PackedBuffer p, Object o, int depth) {
        if (depth >= PACK_MAX_DEPTH) return false;
        if (o == null) {
            p.u8(PACK_NIL);
            return true;
        }
        if (o instanceof Boolean) {
            p.u8(PACK_BOOLEAN);
            p.u8(((Boolean) o) ? 1 : 0);
            return true;
        }
        if (o instanceof String) {
            p.string((String) o);
            return true;
        }
        if (o instanceof Number) {
            // Same rule as the JAVA_OBJECT_CONVERTERS number converter: |value| >= 2^53 must stay a
            // string to keep all its digits, and a Float is widened from its shortest decimal form.
            final Object num = processNumber((Number) o);
            if (num == null) {
                p.u8(PACK_NIL);
            } else if (num instanceof Long) {
                final long v = (Long) num;
                if (longNeedsString(v)) p.string(num.toString());
                else {
                    p.u8(PACK_NUMBER);
                    p.i64(Double.doubleToRawLongBits((double) v));
                }
            } else if (num instanceof Double) {
                p.u8(PACK_NUMBER);
                p.i64(Double.doubleToRawLongBits((Double) num));
            } else {
                p.string((String) num);
            }
            return true;
        }
        if (o instanceof Character) {
            p.u8(PACK_NUMBER);
            p.i64(Double.doubleToRawLongBits(((Character) o).charValue()));
            return true;
        }
        if (o instanceof Map) {
            return packMapEntries(p, (Map<?, ?>) o, depth + 1);
        }
        final Class<?> c = o.getClass();
        if (c.isArray()) {
            if (c.getComponentType().isPrimitive()) return false;   /* byte[]/int[]: not reproducible */
            final Object[] a = (Object[]) o;
            p.u8(PACK_ARRAY);
            p.i32(a.length);
            for (int i = 0; i < a.length; i++) {
                if (!packElement(p, a[i], depth + 1)) return false;
            }
            return true;
        }
        return false;
    }

    protected final void toLuaTable(LuaState L, int index) {
        if (L.keyPair[index] == null) {
            L.keyTypes[index] = LuaType.NIL.id;
            return;
        }
        Class<?> clz = L.keyPair[index].getClass();
        byte baseType = 0;
        L.keyTypes[index] = 0;
        while (clz.isArray()) {
            clz = clz.getComponentType();
            baseType += 16;
        }
        final boolean reuse = clz == Object.class;
        while (baseType > 0) {
            if (Number.class.isAssignableFrom(clz)) {
                baseType += LuaType.NUMBER.id;
                // CRITICAL: Serialize Number array elements to byte[8] for zero-copy JNI access
                L.keyPair[index] = resetNumberArray((Object[]) L.keyPair[index], reuse, baseType - 16);
            } else if (clz == Boolean.class) {
                baseType += LuaType.BOOLEAN.id;
                L.keyPair[index] = resetStringArray((Object[]) L.keyPair[index], reuse, baseType - 16, true);
            } else if (clz == String.class) {
                baseType += LuaType.STRING.id;
                L.keyPair[index] = resetStringArray((Object[]) L.keyPair[index], reuse, baseType - 16, false);
            } else if (JavaFunction.class.isAssignableFrom(clz)) {
                baseType += LuaType.JAVAFUNCTION.id;
            } else if (clz == LuaTable.class) {
                baseType += LuaType.TABLE.id;
            } else if (clz == Object.class) {
                int type = baseType;
                Object obj = L.keyPair[index];
                int founds = 0;
                while (type >= 16) {
                    type -= 16;
                    for (Object o : (Object[]) obj) {
                        if (o != null) {
                            obj = o;
                            if (type >= 16) {
                                break;
                            } else {
                                if (founds == 0) {
                                    clz = obj.getClass();
                                    ++founds;
                                } else if (clz != obj.getClass()) {
                                    clz = Object.class;
                                    break;
                                }
                            }
                        }
                    }
                    // CRITICAL: If all elements are null in this dimension, cannot infer type
                    // Break to prevent infinite loop and default to JAVAOBJECT
                    if (founds == 0) {
                        break;
                    }
                }
                if (clz != Object.class) continue;
                baseType += LuaType.JAVAOBJECT.id;
            } else {
                baseType += LuaType.JAVAOBJECT.id;
            }
            L.keyTypes[index] = baseType;
            break;
        }
    }
}
