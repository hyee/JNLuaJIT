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
     * Overload ranking read by {@link #getTypeDistance}: a Lua value is matched against a Java formal
     * type and the lowest rank wins. Only {@link LuaState#isJavaObject} consumes it.
     */
    private static final int RANK_EXACT = 1;                      // fits the formal type as it is
    private static final int RANK_WIDEN = 2;                      // fits after widening / boxing
    private static final int RANK_STRING = 3;                     // fits only through its string form
    private static final int RANK_OBJECT = Integer.MAX_VALUE - 1;  // only Object accepts it
    private static final int RANK_NONE = Integer.MAX_VALUE;        // no conversion at all
    /**
     * Number of Lua types; one rank slot per Lua type is reserved in every {@link TypeInfo}.
     */
    private static final int LUA_TYPES = LuaType.values().length;
    /**
     * Definition table of the {@link TypeInfo}s below, keyed by Java type. It is only read once per
     * class (from {@link #TYPES}), so the bridge itself never hashes a class at run time.
     */
    private static final Map<Class<?>, TypeInfo> REGISTRY = new HashMap<>();
    /**
     * What every Java type is: read per conversion, so it caches the definition per class instead of
     * hashing a Class on every call.
     */
    private static final ClassValue<TypeInfo> TYPES = new ClassValue<TypeInfo>() {
        @Override
        protected TypeInfo computeValue(Class<?> type) {
            TypeInfo info = REGISTRY.get(type);
            return info != null ? info : NONE;
        }
    };
    /**
     * The answer for a Java type nothing is registered for: no ranks, no converters.
     */
    private static final TypeInfo NONE = new TypeInfo();

    /**
     * Returns the definition of a Java type, or {@link #NONE}. A null type is tolerated because the
     * rank tables used to be HashMaps, which accept a null key.
     */
    private static TypeInfo of(Class<?> type) {
        return type == null ? NONE : TYPES.get(type);
    }

    /**
     * Registers one definition for every listed Java type; a boxed type and its primitive are always
     * registered together.
     */
    private static void register(TypeInfo info, Class<?>... types) {
        for (Class<?> type : types) {
            REGISTRY.put(type, info);
        }
    }

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

    // -- Type definitions
    // One definition per Java type: how a Lua argument of each type ranks against it, and the converters
    // in both directions. A type that really shares a converter or a rank set shares the constant that
    // holds it, so the ranks and the converters of a type can never drift apart.

    private static final JavaObjectConverter<Boolean> PUSH_BOOLEAN = (luaState, value) -> luaState.pushBoolean(value.booleanValue());
    // Lua has one number type, and processNumber() decides push-number versus push-string, so every
    // numeric Java type shares this one converter.
    private static final JavaObjectConverter<Number> PUSH_NUMBER = (luaState, number) -> {
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
    private static final JavaObjectConverter<Character> PUSH_CHARACTER = (luaState, value) -> luaState.pushInteger(value.charValue());
    private static final JavaObjectConverter<String> PUSH_STRING = (luaState, s) -> luaState.pushString(s);
    private static final JavaObjectConverter<char[]> PUSH_CHAR_ARRAY = (luaState, charArray) -> {
        if (charArray == null) {
            luaState.pushNil();
        } else {
            luaState.pushString(new String(charArray));
        }
    };

    /** A Lua boolean is exact for boolean/Boolean; Object is the only other taker. */
    private static final TypeInfo BOOLEAN = new TypeInfo().rank(LuaType.BOOLEAN, RANK_EXACT)
            .from((luaState, index) -> luaState.toBoolean(index)).to(PUSH_BOOLEAN);

    /** Object accepts every Lua value that is ranked at all. */
    private static final TypeInfo OBJECT = new TypeInfo().rank(LuaType.BOOLEAN, RANK_WIDEN).rank(LuaType.NUMBER, RANK_WIDEN)
            .rank(LuaType.STRING, RANK_WIDEN).rank(LuaType.FUNCTION, RANK_WIDEN).rank(LuaType.JAVAFUNCTION, RANK_WIDEN);

    /** A Lua function (and a Java function passed back in) is exact for JavaFunction. */
    private static final TypeInfo FUNCTION = new TypeInfo().rank(LuaType.FUNCTION, RANK_EXACT).rank(LuaType.JAVAFUNCTION, RANK_EXACT);

    private static final JavaObjectConverter<LuaTable> PUSH_TABLE = new JavaObjectConverter<LuaTable>() {
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
                final PackedRefs refs = new PackedRefs();
                final byte[] packed = packArray(obj, luaState, refs);
                if (packed != null) {
                    try {
                        luaState.tablePushPackedArray(packed);
                    } finally {
                        // The replay has read every reference by now; they only had to outlive it.
                        refs.release(luaState);
                    }
                    return;
                }
                refs.release(luaState);   // declined, possibly after referencing earlier leaves
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
            final PackedRefs refs = new PackedRefs();
            final byte[] packed = packMap(obj, luaState, refs);
            if (packed != null) {
                try {
                    luaState.tablePushPackedArray(packed);
                } finally {
                    refs.release(luaState);
                }
                return;
            }
            refs.release(luaState);   // declined, possibly after referencing earlier leaves

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

    /** A Java table/list/map. */
    private static final TypeInfo TABLE = new TypeInfo().to(PUSH_TABLE);

    /**
     * Builds a number definition: exact for a Lua number, and still reachable through a Lua string.
     */
    private static <T> TypeInfo number(LuaValueConverter<T> fromLua) {
        return new TypeInfo().rank(LuaType.NUMBER, RANK_EXACT).rank(LuaType.STRING, RANK_STRING)
                .from(fromLua).to(PUSH_NUMBER);
    }

    private static final TypeInfo BYTE = number((luaState, index) -> (byte) luaState.toInteger(index));
    private static final TypeInfo SHORT = number((luaState, index) -> (short) luaState.toInteger(index));
    private static final TypeInfo INTEGER = number((luaState, index) -> (int) luaState.toInteger(index));
    private static final TypeInfo LONG = number((luaState, index) -> (long) luaState.toInteger(index));
    private static final TypeInfo FLOAT = number((luaState, index) -> (float) luaState.toNumber(index));
    private static final TypeInfo DOUBLE = number((luaState, index) -> luaState.toNumber(index));
    // A Lua string carries more precision than a double, so BigInteger goes through BigDecimal and
    // rounds half-even at scale 0 instead of truncating.
    private static final TypeInfo BIG_INTEGER = number((luaState, index) -> new BigDecimal(luaState.toString(index)).setScale(0, BigDecimal.ROUND_HALF_EVEN).toBigInteger());
    private static final TypeInfo BIG_DECIMAL = number((luaState, index) -> new BigDecimal(luaState.toString(index)));
    /** A character rides the number ranks (a Lua number can be a code point) but pushes as an integer. */
    private static final TypeInfo CHARACTER = number((luaState, index) -> (char) luaState.toInteger(index)).to(PUSH_CHARACTER);

    /** A Lua string is exact for String/char[], and any Java number can still be parsed out of it. */
    private static final TypeInfo STRING = new TypeInfo().rank(LuaType.NUMBER, RANK_STRING).rank(LuaType.STRING, RANK_EXACT)
            .from(LuaState::toString).to(PUSH_STRING);
    private static final TypeInfo BYTE_ARRAY = new TypeInfo().rank(LuaType.NUMBER, RANK_STRING).rank(LuaType.STRING, RANK_EXACT)
            .from(LuaState::toByteArray).to(LuaState::pushByteArray);
    private static final TypeInfo CHAR_ARRAY = new TypeInfo().rank(LuaType.STRING, RANK_EXACT)
            .from((luaState, index) -> {
                String str = luaState.toString(index);
                return str != null ? str.toCharArray() : null;
            }).to(PUSH_CHAR_ARRAY);

    static {
        register(BOOLEAN, Boolean.class, Boolean.TYPE);
        register(BYTE, Byte.class, Byte.TYPE);
        register(SHORT, Short.class, Short.TYPE);
        register(INTEGER, Integer.class, Integer.TYPE);
        register(LONG, Long.class, Long.TYPE);
        register(FLOAT, Float.class, Float.TYPE);
        register(DOUBLE, Double.class, Double.TYPE);
        register(BIG_INTEGER, BigInteger.class);
        register(BIG_DECIMAL, BigDecimal.class);
        register(CHARACTER, Character.class, Character.TYPE);
        register(STRING, String.class);
        register(CHAR_ARRAY, char[].class);
        register(OBJECT, Object.class);
        register(FUNCTION, JavaFunction.class);
        register(TABLE, LuaTable.class);
        if (!RAW_BYTE_ARRAY) {
            register(BYTE_ARRAY, byte[].class);
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
        LuaType luaType = luaState.type(index);
        if (luaType == null) {
            return RANK_NONE;
        }
        if (formalType == Void.TYPE) {
            return RANK_NONE;
        }
        if (formalType == LuaValueProxy.class) {
            return 0;
        }

        // A Java type that is ranked for this Lua argument type wins; a type without a rank for it keeps
        // looking and may still be accepted as Object below.
        int rank = of(formalType).rank(luaType);
        if (rank != 0) {
            return rank;
        }

        switch (luaType) {
            case NIL:
                return RANK_EXACT;
            case TABLE:
                if (formalType == Map.class || formalType == List.class || formalType.isArray()) {
                    return RANK_EXACT;
                }
                if (formalType == Object.class) {
                    return RANK_WIDEN;
                }
                break;
            case LIGHTUSERDATA:
            case USERDATA:
            case THREAD:
                if (formalType == Object.class) {
                    return RANK_WIDEN;
                }
                break;
            case JAVAOBJECT:
                Object object = luaState.toJavaObjectRaw(index);
                if (object != null) {
                    Class<?> type;
                    if (object instanceof TypedJavaObject) {
                        TypedJavaObject<?> typedJavaObject = (TypedJavaObject<?>) object;
                        if (typedJavaObject.isStrong() && formalType.isAssignableFrom(typedJavaObject.getClass())) {
                            return RANK_EXACT;
                        }
                        type = typedJavaObject.getType();
                    } else {
                        type = object.getClass();
                    }
                    if (formalType.isAssignableFrom(type)) {
                        return RANK_EXACT;
                    }
                }
                break;
            default:
                break;   // BOOLEAN/NUMBER/STRING/FUNCTION/JAVAFUNCTION were ranked by the table above
        }

        return formalType == Object.class ? RANK_OBJECT : RANK_NONE;
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
        // A boolean, number or string argument goes through the registered converters; when none is
        // registered for the formal type, only Object still has a fallback (the per-case code below).
        if (luaType == LuaType.BOOLEAN || luaType == LuaType.NUMBER || luaType == LuaType.STRING) {
            LuaValueConverter<?> converter = of(formalType).fromLua;
            if (converter != null) {
                return (T) converter.convert(luaState, index);
            }
        }
        switch (luaType) {
            case NIL:
                return null;
            case BOOLEAN:
                if (formalType == Object.class) {
                    return (T) Boolean.valueOf(luaState.toBoolean(index));
                }
                break;
            case NUMBER:
                if (formalType == Object.class) {
                    final double d = luaState.toNumber(index);
                    final long l = (long) d;
                    if (l == d) {
                        final int i = (int) l;
                        if (i == l) return (T) Integer.valueOf(i);
                        return (T) (Long) l;
                    } else return (T) Double.valueOf(d);
                }
                break;
            case STRING:
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
        if (of(object.getClass()).push(luaState, object)) {
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
     * Everything the bridge knows about one Java type: how a Lua argument of each type ranks against it
     * (see the RANK_* constants; 0 means "not ranked for that Lua type", which leaves the caller's own
     * fallback in charge) and the converters in both directions. One instance per Java type, defined in
     * the block at the top of this class and cached per class by {@link #TYPES}.
     */
    private static final class TypeInfo {
        private final byte[] ranks = new byte[LUA_TYPES];
        private LuaValueConverter<?> fromLua;
        private JavaObjectConverter<?> toLua;

        <T> TypeInfo from(LuaValueConverter<T> converter) {
            fromLua = converter;
            return this;
        }

        <T> TypeInfo to(JavaObjectConverter<T> converter) {
            toLua = converter;
            return this;
        }

        TypeInfo rank(LuaType luaType, int rank) {
            ranks[luaType.ordinal()] = (byte) rank;
            return this;
        }

        int rank(LuaType luaType) {
            return ranks[luaType.ordinal()];
        }

        /**
         * Pushes a Java object to Lua with this type's converter; false when it has none, which leaves
         * the caller to push the raw object.
         */
        @SuppressWarnings("unchecked")
        boolean push(LuaState luaState, Object object) {
            if (toLua == null) {
                return false;
            }
            ((JavaObjectConverter<Object>) toLua).convert(luaState, object);
            return true;
        }
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
     * The tags are mirrored in jnlua.c. A value the buffer cannot carry - a raw Java object such as
     * Timestamp/ZonedDateTime, or a primitive array such as byte[] - crosses as PACK_OBJECT plus an
     * integer reference to the Lua value the per-element path would have produced for it; see
     * packObject(). */
    static final byte PACK_NIL = 0;
    static final byte PACK_BOOLEAN = 1;
    static final byte PACK_NUMBER = 3;
    static final byte PACK_STRING = 4;
    static final byte PACK_ARRAY = 16;
    static final byte PACK_MAP = 32;
    /** A value carried out of band as 4 bytes of luaL_ref into LUA_REGISTRYINDEX (see packObject). */
    static final byte PACK_OBJECT = 64;
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

    /**
     * The registry references allocated for PACK_OBJECT leaves while packing. They are what keeps each
     * object alive between packing and the native replay, so the caller releases them once that replay
     * has finished reading the buffer - and also when packing declines part-way, since it may already
     * have referenced some leaves by then.
     */
    static final class PackedRefs {
        private int[] refs = new int[8];
        private int count;

        void add(int ref) {
            if (count == refs.length) refs = java.util.Arrays.copyOf(refs, count * 2);
            refs[count++] = ref;
        }

        void release(LuaState L) {
            for (int i = 0; i < count; i++) L.unref(LuaState.REGISTRYINDEX, refs[i]);
            count = 0;
        }
    }

    private static final class PackedBuffer {
        /** Null for the LuaState-less overload, which declines object leaves instead of carrying them. */
        final LuaState L;
        final PackedRefs refs;
        byte[] buf = new byte[1024];
        int len;

        PackedBuffer() {
            this(null, null);
        }

        PackedBuffer(LuaState L, PackedRefs refs) {
            this.L = L;
            this.refs = refs;
        }

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
     * is not a value the native replay can reproduce exactly - a List/LuaTable/proxy (which turn into
     * Lua values rather than into raw objects), or a raw Java object when no LuaState is supplied - in
     * which case the caller falls back to the per-element conversion. This is the overload the probes
     * use; it carries no object leaves.
     */
    static byte[] packArray(Object[] arr) {
        return packArray(arr, null, null);
    }

    /**
     * The path the converter itself uses. A raw Java object or a primitive array is carried out of
     * band (see {@link #packObject}) and every reference taken for one is appended to {@code refsOut};
     * the caller must release those once the native replay has finished - and equally when this
     * returns null, since packing can decline after having referenced earlier leaves.
     */
    static byte[] packArray(Object[] arr, LuaState L, PackedRefs refsOut) {
        final PackedBuffer p = new PackedBuffer(L, refsOut);
        try {
            if (!packElement(p, arr, 0)) return null;
        } catch (Throwable t) {
            return null;
        }
        return java.util.Arrays.copyOf(p.buf, p.len);
    }

    /** Packs a map for {@code LuaState.tablePushPackedArray}; same contract as {@link #packArray}. */
    static byte[] packMap(Map<?, ?> map) {
        return packMap(map, null, null);
    }

    /** L-aware {@link #packMap(Map)}; see {@link #packArray(Object[], LuaState, PackedRefs)}. */
    static byte[] packMap(Map<?, ?> map, LuaState L, PackedRefs refsOut) {
        final PackedBuffer p = new PackedBuffer(L, refsOut);
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
            // Same rule as the number converter above: |value| >= 2^53 must stay a
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
        if (c.isArray() && !c.getComponentType().isPrimitive()) {
            final Object[] a = (Object[]) o;
            p.u8(PACK_ARRAY);
            p.i32(a.length);
            for (int i = 0; i < a.length; i++) {
                if (!packElement(p, a[i], depth + 1)) return false;
            }
            return true;
        }
        /* Everything else is a raw Java object, a primitive array included: carry it out of band. */
        return packObject(p, o);
    }

    /**
     * Emits a leaf value that cannot live in the buffer at all. It is pushed by the very conversion the
     * per-element path uses for such a value - {@link #convertJavaObject}, which is that path's
     * else-branch once the arrays, Lists and Maps are excluded - so the two paths agree by construction,
     * and a registry reference then keeps the resulting value alive for the replay to rawgeti. It is the
     * value that is referenced, not the object, so this covers everything that conversion produces: a
     * userdata for a Timestamp or ZonedDateTime, a string for a byte[], and so on.
     *
     * A List is declined: the per-element path routes those to the array converter and produces a Lua
     * table, where convertJavaObject would produce a raw userdata instead. Declines as well when there
     * is no LuaState (the probe overload) or no reference could be taken - in which case the stack is
     * restored to exactly what it was on entry, so a decline cannot disturb the caller.
     */
    private static boolean packObject(PackedBuffer p, Object o) {
        if (p.L == null || p.refs == null || o instanceof List) return false;
        final int top = p.L.getTop();
        p.L.getConverter().convertJavaObject(p.L, o);
        final int ref = p.L.ref(LuaState.REGISTRYINDEX);
        if (ref < 0) {
            final int now = p.L.getTop();
            if (now > top) p.L.pop(now - top);
            return false;
        }
        p.refs.add(ref);
        p.u8(PACK_OBJECT);
        p.i32(ref);
        return true;
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
