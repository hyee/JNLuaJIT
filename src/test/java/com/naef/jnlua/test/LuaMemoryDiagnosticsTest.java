package com.naef.jnlua.test;

import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.debug.LuaMemoryDiagnostics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Demonstrates how to use LuaMemoryDiagnostics to detect memory leaks in real applications.
 */
public class LuaMemoryDiagnosticsTest extends AbstractLuaTest {
    
    @Before
    public void setUpDiagnostics() throws Exception {
        // Enable diagnostics
        LuaMemoryDiagnostics.enable();
    }
    
    @After
    public void tearDownDiagnostics() throws Exception {
        // Print report after each test
        LuaMemoryDiagnostics.printReport();
        LuaMemoryDiagnostics.printRecommendations();
        LuaMemoryDiagnostics.clear();
        // CRITICAL: Disable diagnostics to prevent trace pollution in other tests
        LuaMemoryDiagnostics.disable();
    }
    
    /**
     * Example: Monitoring a typical Lua-Java interaction pattern
     */
    @Test
    public void testTypicalUsagePattern() throws Exception {
        luaState.openLibs();
        
        // Take initial snapshot
        LuaMemoryDiagnostics.snapshot("initial", luaState);
        
        // Simulate typical usage: calling Lua functions that return tables
        for (int i = 0; i < 100; i++) {
            luaState.load(
                "return {key = 'value', index = " + i + "}",
                "=test"
            );
            luaState.call(0, 1);
            
            // Convert to Java object
            Map<Object, Object> table = luaState.toJavaObject(-1, Map.class);
            assertNotNull(table);
            luaState.pop(1);
            
            // Periodic cleanup (recommended pattern)
            if (i % 10 == 0) {
                luaState.cleanup();
            }
        }
        
        // Take snapshot after operations
        LuaMemoryDiagnostics.snapshot("after_operations", luaState);
        
        // Force cleanup
        LuaMemoryDiagnostics.forceCleanup(luaState);
        
        // Take final snapshot
        LuaMemoryDiagnostics.snapshot("after_cleanup", luaState);
        
        // Compare snapshots
        LuaMemoryDiagnostics.compare("initial", "after_operations");
        LuaMemoryDiagnostics.compare("after_operations", "after_cleanup");
        
        // Verify no leak
        assertFalse("Memory leak detected!", LuaMemoryDiagnostics.hasLeak());
    }
    
    /**
     * Example: Detecting a memory leak caused by not calling cleanup()
     */
    @Test
    public void testDetectLeakWithoutCleanup() throws Exception {
        luaState.openLibs();
        
        LuaMemoryDiagnostics.snapshot("start", luaState);
        
        // Simulate leak: create many tables without cleanup
        for (int i = 0; i < 500; i++) {
            luaState.load(
                "return {data = string.rep('x', 1000)}",
                "=leak"
            );
            luaState.call(0, 1);
            Map<Object, Object> table = luaState.toJavaObject(-1, Map.class);
            luaState.pop(1);
            // INTENTIONALLY NOT CALLING cleanup()
        }
        
        LuaMemoryDiagnostics.snapshot("after_operations", luaState);
        
        // Now cleanup
        LuaMemoryDiagnostics.forceCleanup(luaState);
        
        LuaMemoryDiagnostics.snapshot("after_cleanup", luaState);
        
        System.out.println("\n=== Leak Detection Test ===");
        LuaMemoryDiagnostics.compare("start", "after_operations");
        LuaMemoryDiagnostics.compare("after_operations", "after_cleanup");
        
        // This test intentionally creates a pattern that looks like a leak
        // The diagnostics should detect it
        System.out.println("\nLeak detected: " + LuaMemoryDiagnostics.hasLeak());
    }
    
    /**
     * Example: Monitoring long-running operation
     */
    @Test
    public void testLongRunningOperation() throws Exception {
        luaState.openLibs();
        
        // Register a function that processes tables
        final int[] processedCount = {0};
        JavaFunction processTable = new JavaFunction() {
            @Override
            public int invoke(LuaState luaState) {
                Map<Object, Object> table = luaState.toJavaObject(1, Map.class);
                processedCount[0]++;
                return 0;
            }
            
            @Override
            public String getName() {
                return "processTable";
            }
        };
        luaState.register(processTable);
        
        LuaMemoryDiagnostics.snapshot("start", luaState);
        
        // Simulate long-running operation with periodic snapshots
        for (int batch = 0; batch < 5; batch++) {
            for (int i = 0; i < 100; i++) {
                luaState.load(
                    "local t = {batch = " + batch + ", index = " + i + "}\n" +
                    "processTable(t)",
                    "=batch"
                );
                luaState.call(0, 0);
            }
            
            // Cleanup after each batch
            luaState.cleanup();
            
            // Take snapshot
            LuaMemoryDiagnostics.snapshot("batch_" + batch, luaState);
        }
        
        // Force final cleanup
        LuaMemoryDiagnostics.forceCleanup(luaState);
        LuaMemoryDiagnostics.snapshot("final", luaState);
        
        System.out.println("\n=== Long Running Operation ===");
        System.out.println("Processed " + processedCount[0] + " tables");
        
        // Compare first and last batch
        LuaMemoryDiagnostics.compare("batch_0", "batch_4");
        LuaMemoryDiagnostics.compare("batch_4", "final");
    }
    
    /**
     * Example: Testing with Lua script
     */
    @Test
    public void testWithLuaScript() throws Exception {
        luaState.openLibs();
        
        LuaMemoryDiagnostics.snapshot("before_script", luaState);
        
        // Load and run Lua script
        luaState.load(
            "function createTables(count)\n" +
            "  local tables = {}\n" +
            "  for i = 1, count do\n" +
            "    tables[i] = {index = i, data = string.rep('x', 100)}\n" +
            "  end\n" +
            "  return tables\n" +
            "end\n" +
            "\n" +
            "return createTables(1000)",
            "=script"
        );
        luaState.call(0, 1);
        
        // Get the result
        Object result = luaState.toJavaObject(-1, Object.class);
        assertNotNull(result);
        luaState.pop(1);
        
        LuaMemoryDiagnostics.snapshot("after_script", luaState);
        
        // Cleanup
        result = null; // Release Java reference
        System.gc();
        Thread.sleep(100);
        luaState.cleanup();
        luaState.gc(LuaState.GcAction.COLLECT, 0);
        
        LuaMemoryDiagnostics.snapshot("after_cleanup", luaState);
        
        System.out.println("\n=== Lua Script Test ===");
        LuaMemoryDiagnostics.compare("before_script", "after_script");
        LuaMemoryDiagnostics.compare("after_script", "after_cleanup");
    }
    
    /**
     * Example: Testing cleanup frequency
     */
    @Test
    public void testCleanupFrequency() throws Exception {
        luaState.openLibs();
        
        System.out.println("\n=== Testing Different Cleanup Frequencies ===");
        
        // Test 1: No cleanup
        LuaMemoryDiagnostics.snapshot("no_cleanup_start", luaState);
        for (int i = 0; i < 200; i++) {
            luaState.load("return {index = " + i + "}", "=test");
            luaState.call(0, 1);
            luaState.toJavaObject(-1, Map.class);
            luaState.pop(1);
        }
        LuaMemoryDiagnostics.snapshot("no_cleanup_end", luaState);
        
        // Cleanup
        LuaMemoryDiagnostics.forceCleanup(luaState);
        LuaMemoryDiagnostics.snapshot("after_manual_cleanup", luaState);
        
        // Test 2: Cleanup every 50 operations
        LuaMemoryDiagnostics.snapshot("frequent_cleanup_start", luaState);
        for (int i = 0; i < 200; i++) {
            luaState.load("return {index = " + i + "}", "=test");
            luaState.call(0, 1);
            luaState.toJavaObject(-1, Map.class);
            luaState.pop(1);
            
            if (i % 50 == 0) {
                luaState.cleanup();
            }
        }
        LuaMemoryDiagnostics.snapshot("frequent_cleanup_end", luaState);
        
        System.out.println("\nWithout cleanup:");
        LuaMemoryDiagnostics.compare("no_cleanup_start", "no_cleanup_end");
        
        System.out.println("\nWith frequent cleanup:");
        LuaMemoryDiagnostics.compare("frequent_cleanup_start", "frequent_cleanup_end");
        
        System.out.println("\n✓ Recommendation: Call cleanup() every 10-100 operations depending on table size");
    }
}
