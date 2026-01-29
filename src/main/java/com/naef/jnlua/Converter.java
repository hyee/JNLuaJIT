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
public final class Converter {
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
    private static final Map<Class<?>, Byte> CLASS_TO_LUA_TYPE = new HashMap<>();

    static {
        BOOLEAN_DISTANCE_MAP.put(Boolean.class, 1);
        BOOLEAN_DISTANCE_MAP.put(Boolean.TYPE, 1);
        BOOLEAN_DISTANCE_MAP.put(Object.class, 2);
        
        CLASS_TO_LUA_TYPE.put(Boolean.class, LuaType.BOOLEAN.id);
        CLASS_TO_LUA_TYPE.put(String.class, LuaType.STRING.id);
        CLASS_TO_LUA_TYPE.put(byte[].class, LuaType.STRING.id);
        CLASS_TO_LUA_TYPE.put(LuaTable.class, LuaType.TABLE.id);
        CLASS_TO_LUA_TYPE.put(Byte.class, LuaType.NUMBER.id);
        CLASS_TO_LUA_TYPE.put(Short.class, LuaType.NUMBER.id);
        CLASS_TO_LUA_TYPE.put(Integer.class, LuaType.NUMBER.id);
        CLASS_TO_LUA_TYPE.put(Long.class, LuaType.NUMBER.id);
        CLASS_TO_LUA_TYPE.put(Float.class, LuaType.NUMBER.id);
        CLASS_TO_LUA_TYPE.put(Double.class, LuaType.NUMBER.id);
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
                    if (number instanceof BigInteger) {
                        final BigInteger bi = (BigInteger) number;
                        try{
                            luaState.pushInteger(bi.longValueExact());
                        } catch (ArithmeticException e) {
                            luaState.pushString(bi.toString());
                        }
                    } else {
                        final BigDecimal decimal = ((BigDecimal) number).stripTrailingZeros();
                        try {
                            luaState.pushInteger(decimal.longValueExact());
                        } catch (ArithmeticException e) {
                            luaState.pushString(decimal.toPlainString());
                            d = decimal.doubleValue();
                            final String str = Double.toString(d);
                            if(decimal.compareTo(new BigDecimal(str)) == 0) {
                                luaState.pushNumber(d);
                            } else {
                                luaState.pushString(decimal.toPlainString());
                            }
                        }
                    }
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
                } else if (o instanceof List) {
                    convertArray(luaState, ((List<?>) o).toArray());
                } else if (o instanceof Map) convertMap(luaState, (Map) o);
                else luaState.getConverter().convertJavaObject(luaState, o);
            }

            final void convertArray(LuaState luaState, Object[] obj) {
                //BUG on query performance_schema.accounts
                //luaState.tablePushArray(obj);

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
                if (formalType == Map.class || formalType == Object.class) {
                    return (T) new AbstractTableMap(luaState, index, subClass.length > 1 && subClass[0] != null ? subClass[0] : Object.class, subClass.length > 1 && subClass[1] != null ? subClass[1] : Object.class);
                } else if (formalType == List.class) {
                    return (T) new AbstractTableList(luaState, index, subClass.length > 1 && subClass[0] != null ? subClass[0] : Object.class);
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

    private final static AbstractTableMap defaultMap=new AbstractTableMap<>();
    public final boolean getLuaValues(LuaState L, boolean skipLoadTable, Object[] args, byte[] argTypes, Object[] params, LuaType[] types, Class returnClass) {
        boolean hasTable = false;
        for (int i = 0; i < types.length; i++) {
            types[i] = LuaType.get(argTypes[i]);
            switch (types[i]) {
                case TABLE:
                    params[i] = args[i];
                    hasTable = true;
                    if (!skipLoadTable && (args[i] instanceof Double)) {
                        final int ref = ((Double) args[i]).intValue();
                        L.rawGet(LuaState.GLOBALSINDEX, ref);
                        params[i] = convertLuaValue(L, L.getTop(), types[i], returnClass);
                        L.unref(LuaState.GLOBALSINDEX, ref);
                        L.pop(1);
                    } else {
                        params[i] = defaultMap;
                        //System.out.println(args[i]==null?"null":args[i].getClass());
                        //problem: if params[i] is null then unable to detect arg types
                    }
                    break;
                case FUNCTION:
                case USERDATA:
                    params[i] = convertLuaValue(L, i + 1, types[i], returnClass);
                    break;
                case JAVAOBJECT:
                    params[i] = args[i];
                    if (params[i] instanceof TypedJavaObject) {
                        if (!((TypedJavaObject) params[i]).isStrong())
                            params[i] = ((TypedJavaObject) params[i]).getObject();
                    }
                    break;
                case BOOLEAN:
                    params[i] = ((byte[]) args[i])[0] == '1';
                    break;
                default:
                    if (args[i] instanceof byte[]) {
                        params[i] = new String(((byte[]) args[i]), LuaState.UTF8);
                        if (types[i] == LuaType.NUMBER) {
                            final Double d = Double.valueOf((String) params[i]);
                            final long l = d.longValue();
                            final double dv = d.doubleValue();
                            if (dv == l) {
                                final int s = d.intValue();
                                if (s == dv) params[i] = s;
                                else params[i] = l;
                            } else params[i] = d;
                        }
                    } else params[i] = args[i];
                    break;
            }
        }
        return hasTable;
    }

    public final void toLuaType(LuaState L, Object[] args, byte[] types, int range, boolean checkNull) {
        for (int i = 0; i < range; i++) {
            final Object arg = args[i];
            if (arg == null) {
                if (i == 0 && checkNull) throw new NullPointerException("Lua table key must not be null");
                types[i] = LuaType.NIL.id;
                continue;
            }
            
            final Class<?> o = arg.getClass();
            Byte typeId = CLASS_TO_LUA_TYPE.get(o);
            if (typeId != null) {
                int type = typeId.intValue();
                if (type == LuaType.BOOLEAN.id) {
                    args[i] = ((Boolean) arg) ? BOOLEAN_TRUE_BYTES : BOOLEAN_FALSE_BYTES;
                } else if (type == LuaType.STRING.id && o == String.class) {
                    args[i] = ((String) arg).getBytes(LuaState.UTF8);
                }
                types[i] = (byte) type;
                continue;
            }

            int type;
            if (arg instanceof Number) {
                if(o==BigInteger.class) {
                    try {
                        args[i] = ((BigInteger) arg).longValue();
                        type = LuaType.NUMBER.id;
                    } catch (Exception e) {
                        args[i] = arg.toString();
                        type = LuaType.STRING.id;
                    }
                } else if(o==BigDecimal.class) {
                    final BigDecimal bd = ((BigDecimal) args[i]).stripTrailingZeros();
                    try {
                        args[i] = bd.longValueExact();
                        type = LuaType.NUMBER.id;
                    } catch (Exception e) {
                        final double d = bd.doubleValue();
                        final String str = Double.toString(d);
                        if (bd.compareTo(new BigDecimal(str)) == 0) {
                            args[i] = d;
                            type = LuaType.NUMBER.id;
                        } else {
                            args[i] = bd.toPlainString();
                            type = LuaType.STRING.id;
                        }
                    }
                } else if(o==Double.class||o==Float.class) {
                    final BigDecimal bd =BigDecimal.valueOf((Double) args[i]);
                    if (bd.compareTo(new BigDecimal(args[i].toString())) == 0) {
                        type = LuaType.NUMBER.id;
                    } else {
                        args[i] = bd.toPlainString();
                        type = LuaType.STRING.id;
                    }
                } else {
                    type = LuaType.NUMBER.id;
                }
            } else if (JavaFunction.class.isAssignableFrom(o)) {
                type = LuaType.JAVAFUNCTION.id;
            } else if (o == LuaTable.class) {
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

    public final void toLuaTable(LuaState L, int index) {
        if (L.keyPair[index] == null) {
            L.keyTypes[index] = LuaType.NIL.id;
            return;
        }
        Class clz = L.keyPair[index].getClass();
        byte baseType = 0;
        L.keyTypes[index] = 0;
        while (clz.isArray()) {
            clz = clz.getComponentType();
            baseType += 16;
        }
        final boolean reuse = clz == Object.class;
        while (baseType > 0) {
            if (Number.class.isAssignableFrom(clz))
                baseType += LuaType.NUMBER.id;
            else if (clz == Boolean.class) {
                baseType += LuaType.BOOLEAN.id;
                L.keyPair[index] = resetStringArray((Object[]) L.keyPair[index], reuse, baseType - 16, true);
            } else if (clz == String.class || clz == Boolean.class) {
                baseType += LuaType.STRING.id;
                L.keyPair[index] = resetStringArray((Object[]) L.keyPair[index], reuse, baseType - 16, false);
            } else if (JavaFunction.class.isAssignableFrom(clz))
                baseType += LuaType.JAVAFUNCTION.id;
            else if (clz == LuaTable.class)
                baseType += LuaType.TABLE.id;
            else if (clz == Object.class) {
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
                }
                if (clz != Object.class) continue;
                baseType += LuaType.JAVAOBJECT.id;
            } else
                baseType += LuaType.JAVAOBJECT.id;
            L.keyTypes[index] = baseType;
            break;
        }
    }
}
