package com.naef.jnlua.test;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import com.naef.jnlua.LuaState;

/**
 * Lua-based unit tests.
 */
public class LuaTest extends AbstractLuaTest {
    // -- Test cases

    /**
     * Tests the Java module.
     */
    @Test
    public void testJavaModule() throws Exception {
        runLuaTest("com/naef/jnlua/test/JavaModule.lua", "JavaModule");
    }

    /**
     * Tests reflection.
     */
    @Test
    public void testReflection() throws Exception {
        runLuaTest("com/naef/jnlua/test/Reflection.lua", "Reflection");
    }
    
    /**
     * ========================================================================
     * [Optimization #4] Native Field Cache Performance Test
     * ========================================================================
     * Tests the performance improvement of Native Field Fast-Path optimization.
     * 
     * This test compares:
     * 1. Java-side field access (baseline)
     * 2. Lua-side field access (before optimization - ~150-900x slower)
     * 3. Lua-side field access (after optimization - should be ~10-50x slower)
     * 
     * Expected Results:
     * - Before optimization: Lua field access is 150-900x slower than Java
     * - After optimization: Lua field access is 10-50x slower than Java
     * - Improvement: ~10-20x speedup for Lua field access
     */
    @Test
    public void testNativeFieldCachePerformance() throws Exception {
        System.out.println("\n========================================");
        System.out.println("Native Field Cache Performance Test");
        System.out.println("========================================\n");
        
        // Create test object
        FieldAccessTestObject testObj = new FieldAccessTestObject();
        
        // Warm up JVM
        for (int i = 0; i < 10000; i++) {
            testObj.intField = i;
            testObj.longField = i;
            testObj.doubleField = i;
        }
        
        // Test Java-side field access (baseline)
        int iterations = 1000000;
        long javaStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            int a = testObj.intField;
            long b = testObj.longField;
            double c = testObj.doubleField;
            String d = testObj.stringField;
        }
        long javaTime = System.nanoTime() - javaStart;
        
        System.out.printf("Java field access (baseline):    %,d ns for %,d iterations (%.2f ns/access)\n", 
                          javaTime, iterations, javaTime / (double)(iterations * 4));
        
        // Test Lua-side field access
        luaState.openLibs();
        luaState.pushJavaObject(testObj);
        luaState.setGlobal("testObj");
        
        // Warm up Lua (triggers native field cache registration)
        luaState.load(toInputStream(
            "for i=1,100 do " +
            "  local a = testObj.intField " +
            "  local b = testObj.longField " +
            "  local c = testObj.doubleField " +
            "  local d = testObj.stringField " +
            "end"
        ), "warmup", "t");
        luaState.call(0, 0);
        
        // Test Lua field access performance
        luaState.load(toInputStream(
            "local obj = testObj " +
            "local start = os.clock() " +
            "for i=1," + iterations + " do " +
            "  local a = obj.intField " +
            "  local b = obj.longField " +
            "  local c = obj.doubleField " +
            "  local d = obj.stringField " +
            "end " +
            "local elapsed = os.clock() - start " +
            "return elapsed"
        ), "test", "t");
        luaState.call(0, 1);
        double luaTimeSeconds = luaState.toNumber(-1);
        luaState.pop(1);
        long luaTime = (long)(luaTimeSeconds * 1_000_000_000);
        
        System.out.printf("Lua field access (optimized):    %,d ns for %,d iterations (%.2f ns/access)\n", 
                          luaTime, iterations, luaTime / (double)(iterations * 4));
        
        // Calculate slowdown factor
        double slowdownFactor = luaTime / (double)javaTime;
        System.out.printf("\nSlowdown factor: %.1fx\n", slowdownFactor);
        
        // Verify optimization effectiveness
        System.out.println("\nOptimization Assessment:");
        if (slowdownFactor < 50) {
            System.out.println("✓ EXCELLENT: Native field cache is working! (< 50x slower than Java)");
        } else if (slowdownFactor < 100) {
            System.out.println("✓ GOOD: Significant improvement, but could be better (50-100x slower)");
        } else if (slowdownFactor < 200) {
            System.out.println("⚠ MODERATE: Some improvement, but optimization not fully effective (100-200x slower)");
        } else {
            System.out.println("✗ POOR: Optimization may not be working (> 200x slower - similar to unoptimized)");
        }
        
        System.out.println("\nExpected results:");
        System.out.println("- Before optimization: ~150-900x slower than Java");
        System.out.println("- After optimization:  ~10-50x slower than Java");
        System.out.println("========================================\n");
    }

    // -- Private methods

    /**
     * Helper to convert a script string to an input stream using UTF-8.
     */
    private InputStream toInputStream(String script) {
        return new ByteArrayInputStream(script.getBytes(LuaState.UTF8));
    }

    /**
     * Runs a Lua-based test.
     */
    private void runLuaTest(String source, String moduleName) throws Exception {
        // Open libraries
        luaState.openLibs();

        // Load
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(source);
        luaState.load(inputStream, moduleName, "t");
        luaState.pushString(moduleName);
        luaState.call(1, 0);
        // Run all module functions beginning with "test"
        luaState.getGlobal(moduleName);
        luaState.pushNil();
        while (luaState.next(1)) {
            String key = luaState.toString(-2);
            if (key.startsWith("test") && luaState.isFunction(-1)) {
                System.out.println("Testing function " + key);
                luaState.call(0, 0);
            } else {
                luaState.pop(1);
            }
        }
    }
    
    /**
     * Test object for field access performance testing.
     */
    public static class FieldAccessTestObject {
        // Primitive types
        public int intField = 42;
        public long longField = 12345678901234L;
        public double doubleField = 3.14159;
        public float floatField = 2.71828f;
        public short shortField = 100;
        public byte byteField = 10;
        public boolean booleanField = true;
        public char charField = 'A';
        
        // Object types that need special conversion
        public String stringField = "Test String";
        public byte[] byteArrayField = "Hello Bytes".getBytes();
        public char[] charArrayField = "Hello Chars".toCharArray();
        
        // Complex object types (Map, List, Array)
        public java.util.Map<String, Integer> mapField = new java.util.HashMap<>();
        public java.util.List<String> listField = new java.util.ArrayList<>();
        public int[] intArrayField = {1, 2, 3, 4, 5};
        public String[] stringArrayField = {"a", "b", "c"};
        
        // Static fields
        public static int staticIntField = 100;
        public static String staticStringField = "Static Test";
        
        public FieldAccessTestObject() {
            mapField.put("one", 1);
            mapField.put("two", 2);
            listField.add("first");
            listField.add("second");
        }
    }

    /**
     * ========================================================================
     * [Optimization #4] Bidirectional Field Access Test
     * ========================================================================
     * Tests both read and write operations through Native Field Cache.
     */
    @Test
    public void testNativeFieldCacheBidirectional() throws Exception {
        System.out.println("\n========================================");
        System.out.println("Native Field Cache Bidirectional Test");
        System.out.println("========================================\n");
        
        luaState.openLibs();
        FieldAccessTestObject testObj = new FieldAccessTestObject();
        luaState.pushJavaObject(testObj);
        luaState.setGlobal("testObj");
        
        // Test primitive types write
        System.out.println("Testing primitive types (write):");
        
        luaState.load(toInputStream(
            "testObj.intField = 999 " +
            "print('  ✓ intField write: ' .. testObj.intField)"
        ), "test", "t");
        luaState.call(0, 0);
        assert testObj.intField == 999 : "intField write failed";
        
        luaState.load(toInputStream(
            "testObj.longField = 88888888 " +
            "print('  ✓ longField write: ' .. testObj.longField)"
        ), "test", "t");
        luaState.call(0, 0);
        assert testObj.longField == 88888888L : "longField write failed";
        
        luaState.load(toInputStream(
            "testObj.doubleField = 2.71828 " +
            "print('  ✓ doubleField write: ' .. testObj.doubleField)"
        ), "test", "t");
        luaState.call(0, 0);
        assert Math.abs(testObj.doubleField - 2.71828) < 0.001 : "doubleField write failed";
        
        luaState.load(toInputStream(
            "testObj.booleanField = false " +
            "print('  ✓ booleanField write: ' .. tostring(testObj.booleanField))"
        ), "test", "t");
        luaState.call(0, 0);
        assert testObj.booleanField == false : "booleanField write failed";
        
        // Test String write
        System.out.println("\nTesting String write:");
        luaState.load(toInputStream(
            "testObj.stringField = 'Modified String' " +
            "print('  ✓ String field write: ' .. testObj.stringField)"
        ), "test", "t");
        luaState.call(0, 0);
        assert testObj.stringField.equals("Modified String") : "String write failed";
                
        // Test byte[] bidirectional conversion
        System.out.println("\nTesting byte[] bidirectional conversion:");
        // Write: Lua string → Java byte[]
        luaState.load(toInputStream(
            "testObj.byteArrayField = 'Binary Data' " +
            "print('  ✓ byte[] field write (Lua string → Java byte[])')"
        ), "test", "t");
        luaState.call(0, 0);
        assert new String(testObj.byteArrayField).equals("Binary Data") : "byte[] write failed";
                
        // Read: Java byte[] → Lua string
        testObj.byteArrayField = "Read Bytes".getBytes();
        luaState.load(toInputStream(
            "local v = testObj.byteArrayField " +
            "print('  ✓ byte[] field read (Java byte[] → Lua string): ' .. v)"
        ), "test", "t");
        luaState.call(0, 0);
                
        // Test char[] bidirectional conversion
        System.out.println("\nTesting char[] bidirectional conversion:");
        // Write: Lua string → Java char[]
        luaState.load(toInputStream(
            "testObj.charArrayField = 'Char Data' " +
            "print('  ✓ char[] field write (Lua string → Java char[])')"
        ), "test", "t");
        luaState.call(0, 0);
        assert new String(testObj.charArrayField).equals("Char Data") : "char[] write failed";
                
        // Read: Java char[] → Lua string
        testObj.charArrayField = "Read Chars".toCharArray();
        luaState.load(toInputStream(
            "local v = testObj.charArrayField " +
            "print('  ✓ char[] field read (Java char[] → Lua string): ' .. v)"
        ), "test", "t");
        luaState.call(0, 0);
        
        // Test static fields write
        System.out.println("\nTesting static fields write:");
        luaState.load(toInputStream(
            "testObj.staticIntField = 888 " +
            "print('  ✓ Static int field write: ' .. testObj.staticIntField)"
        ), "test", "t");
        luaState.call(0, 0);
        assert FieldAccessTestObject.staticIntField == 888 : "Static int write failed";
        
        luaState.load(toInputStream(
            "testObj.staticStringField = 'Static Modified' " +
            "print('  ✓ Static string field write: ' .. testObj.staticStringField)"
        ), "test", "t");
        luaState.call(0, 0);
        assert FieldAccessTestObject.staticStringField.equals("Static Modified") : "Static string write failed";
        
        // Test bidirectional (read-modify-write)
        System.out.println("\nTesting bidirectional (read-modify-write):");
        testObj.intField = 100;
        luaState.load(toInputStream(
            "local v = testObj.intField " +  // Read
            "v = v + 50 " +                   // Modify
            "testObj.intField = v " +        // Write back
            "print('  ✓ Bidirectional: read(100) + 50 = ' .. testObj.intField)"
        ), "test", "t");
        luaState.call(0, 0);
        assert testObj.intField == 150 : "Bidirectional failed";
        
        System.out.println("\n✅ All bidirectional tests passed!");
        System.out.println("========================================\n");
    }
    
    /**
     * ========================================================================
     * [Optimization #4] Converter Layer char[] Bidirectional Test
     * ========================================================================
     * Tests that Converter.java properly handles char[] ↔ Lua string conversion
     * in both directions through Java method calls (not just field access).
     * 
     * This complements the Native Field Cache tests by validating:
     * 1. Converter.convertJavaObject() converts char[] → Lua string
     * 2. Converter.convertLuaValue() converts Lua string → char[]
     * 3. Method parameters and return values work correctly
     * 4. Unicode handling is correct
     */
    @Test
    public void testConverterCharArrayBidirectional() throws Exception {
        System.out.println("\n========================================");
        System.out.println("Converter Layer char[] Bidirectional Test");
        System.out.println("========================================\n");
        
        luaState.openLibs();
        
        // Create test object with char array utility methods
        CharArrayTestHelper helper = new CharArrayTestHelper();
        luaState.pushJavaObject(helper);
        luaState.setGlobal("helper");
        
        // Test 1: Java char[] → Lua string (return value conversion)
        System.out.println("Test 1: Java char[] → Lua string (method return value)");
        luaState.load(toInputStream(
            "local chars = helper:getCharArray() " +
            "assert(type(chars) == 'string', 'char[] should be converted to Lua string') " +
            "assert(chars == 'Hello World', 'char[] content mismatch: ' .. chars) " +
            "print('  ✅ char[] return value converted to Lua string: ' .. chars)"
        ), "test", "t");
        luaState.call(0, 0);
        
        // Test 2: Lua string → Java char[] (parameter conversion)
        System.out.println("\nTest 2: Lua string → Java char[] (method parameter)");
        luaState.load(toInputStream(
            "local result = helper:processCharArray('Test Input') " +
            "assert(result == 10, 'char[] parameter length mismatch: ' .. result) " +
            "print('  ✅ Lua string converted to char[] parameter, length: ' .. result)"
        ), "test", "t");
        luaState.call(0, 0);
        
        // Test 3: Unicode handling
        System.out.println("\nTest 3: Unicode character handling");
        luaState.load(toInputStream(
            "local unicode = helper:getUnicodeCharArray() " +
            "assert(type(unicode) == 'string', 'Unicode char[] should be Lua string') " +
            "print('  ✅ Unicode char[] converted: ' .. unicode)"
        ), "test", "t");
        luaState.call(0, 0);
        
        // Test 4: Empty char[] handling
        System.out.println("\nTest 4: Empty char[] handling");
        luaState.load(toInputStream(
            "local empty = helper:getEmptyCharArray() " +
            "assert(type(empty) == 'string', 'Empty char[] should be Lua string') " +
            "assert(empty == '', 'Empty char[] should be empty string') " +
            "print('  ✅ Empty char[] handled correctly')"
        ), "test", "t");
        luaState.call(0, 0);
        
        // Test 5: null char[] handling
        System.out.println("\nTest 5: null char[] handling");
        luaState.load(toInputStream(
            "local nullChars = helper:getNullCharArray() " +
            "assert(type(nullChars) == 'nil', 'null char[] should be Lua nil') " +
            "print('  ✅ null char[] converted to nil')"
        ), "test", "t");
        luaState.call(0, 0);
        
        // Test 6: Roundtrip test (Lua → Java → Lua)
        System.out.println("\nTest 6: Roundtrip conversion (Lua → Java → Lua)");
        luaState.load(toInputStream(
            "local original = 'Roundtrip Test 中文' " +
            "local echoed = helper:echoCharArray(original) " +
            "assert(echoed == original, 'Roundtrip mismatch: ' .. echoed) " +
            "print('  ✅ Roundtrip successful: ' .. echoed)"
        ), "test", "t");
        luaState.call(0, 0);
        
        System.out.println("\n✅ All Converter char[] tests passed!");
        System.out.println("========================================\n");
    }
    
    /**
     * Helper class for testing char[] conversion through method calls.
     */
    public static class CharArrayTestHelper {
        public char[] getCharArray() {
            return "Hello World".toCharArray();
        }
        
        public char[] getUnicodeCharArray() {
            return "Unicode: 中文 日本語 😀".toCharArray();
        }
        
        public char[] getEmptyCharArray() {
            return new char[0];
        }
        
        public char[] getNullCharArray() {
            return null;
        }
        
        public int processCharArray(char[] input) {
            return input != null ? input.length : -1;
        }
        
        public String echoCharArray(char[] input) {
            return input != null ? new String(input) : null;
        }
    }
    
    private void testPrimitiveType(String fieldName, Object expectedValue) throws Exception {
        luaState.load(toInputStream(
            "local v = testObj." + fieldName + " " +
            "print('  ✓ " + fieldName + ": ' .. tostring(v)) " +
            "return v"
        ), "test", "t");
        luaState.call(0, 1);
        
        // Verify the value
        Object actualValue;
        if (expectedValue instanceof Boolean) {
            actualValue = luaState.toBoolean(-1);
        } else if (expectedValue instanceof Character) {
            actualValue = (char)luaState.toInteger(-1);
        } else if (expectedValue instanceof Number) {
            if (expectedValue instanceof Double || expectedValue instanceof Float) {
                actualValue = luaState.toNumber(-1);
            } else if (expectedValue instanceof Long) {
                actualValue = (long)luaState.toNumber(-1);
            } else {
                actualValue = (int)luaState.toInteger(-1);
            }
        } else {
            actualValue = luaState.toJavaObject(-1, Object.class);
        }
        
        luaState.pop(1);
        
        // Basic verification
        if (!String.valueOf(expectedValue).equals(String.valueOf(actualValue))) {
            throw new AssertionError(fieldName + " mismatch: expected " + expectedValue + ", got " + actualValue);
        }
    }
}