/**
 * Diagnostic tools for monitoring and debugging Lua-Java memory usage.
 * 
 * <h2>Quick Start</h2>
 * 
 * <pre>{@code
 * // 1. Enable diagnostics at application start
 * LuaMemoryDiagnostics.enable();
 * 
 * // 2. Create your LuaState
 * LuaState luaState = new LuaState();
 * luaState.openLibs();
 * 
 * // 3. Take snapshots at key points
 * LuaMemoryDiagnostics.snapshot("app_start", luaState);
 * 
 * // 4. Your application logic with CRITICAL cleanup calls
 * for (int i = 0; i < 1000; i++) {
 *     luaState.load("return {data = 'value'}", "=app");
 *     luaState.call(0, 1);
 *     Map table = luaState.toJavaObject(-1, Map.class);
 *     luaState.pop(1);
 *     
 *     // CRITICAL: Call cleanup() every 50-100 operations
 *     if (i % 100 == 0) {
 *         luaState.cleanup();
 *     }
 * }
 * 
 * // 5. Take final snapshot and analyze
 * LuaMemoryDiagnostics.snapshot("app_end", luaState);
 * LuaMemoryDiagnostics.compare("app_start", "app_end");
 * LuaMemoryDiagnostics.printReport();
 * LuaMemoryDiagnostics.printRecommendations();
 * }</pre>
 * 
 * <h2>Common Memory Leak Patterns</h2>
 * 
 * <h3>Pattern 1: Forgetting to call cleanup()</h3>
 * <pre>{@code
 * // BAD: Memory leak! Proxies: 0 -> 1000 (+1000)
 * for (int i = 0; i < 10000; i++) {
 *     Map table = luaState.toJavaObject(-1, Map.class);
 *     // ... use table ...
 * }
 * // NO cleanup() called!
 * 
 * // GOOD: Regular cleanup, Proxies remain stable
 * for (int i = 0; i < 10000; i++) {
 *     Map table = luaState.toJavaObject(-1, Map.class);
 *     // ... use table ...
 *     
 *     if (i % 100 == 0) {
 *         luaState.cleanup();  // Clean up garbage-collected proxies
 *     }
 * }
 * }</pre>
 * 
 * <h3>Pattern 2: Storing table references long-term</h3>
 * <pre>{@code
 * // BAD: Keeping references indefinitely
 * List<Map> allTables = new ArrayList<>();
 * for (int i = 0; i < 10000; i++) {
 *     Map table = luaState.toJavaObject(-1, Map.class);
 *     allTables.add(table);  // Prevents GC!
 * }
 * // Result: Proxies: 0 -> 10000 (+10000), Memory: +100MB
 * 
 * // GOOD: Extract data and release references
 * List<String> data = new ArrayList<>();
 * for (int i = 0; i < 10000; i++) {
 *     Map table = luaState.toJavaObject(-1, Map.class);
 *     data.add((String) table.get("key"));  // Extract what you need
 *     // Let table be GC'd
 * }
 * // Result: Proxies remain stable, Memory growth minimal
 * }</pre>
 * 
 * <h3>Pattern 3: Not handling exceptions</h3>
 * <pre>{@code
 * // BAD: Exception prevents cleanup
 * for (int i = 0; i < 1000; i++) {
 *     Map table = luaState.toJavaObject(-1, Map.class);
 *     processTable(table);  // May throw exception!
 * }
 * luaState.cleanup();  // Never reached if exception thrown!
 * 
 * // GOOD: Use try-finally
 * try {
 *     for (int i = 0; i < 1000; i++) {
 *         Map table = luaState.toJavaObject(-1, Map.class);
 *         processTable(table);
 *         
 *         if (i % 100 == 0) luaState.cleanup();
 *     }
 * } finally {
 *     luaState.cleanup();  // Always called
 * }
 * }</pre>
 * 
 * <h2>Diagnostic Output Interpretation</h2>
 * 
 * <h3>Healthy Output (No Leak)</h3>
 * <pre>
 * === Snapshot Comparison: app_start -> app_end ===
 * Lua Memory:   1.24 MB -> 1.28 MB (+40.00 KB)
 * Java Heap:    45.67 MB -> 46.12 MB (+450.00 KB)
 * Proxy Count:  5 -> 8 (+3)  // Small stable growth is OK
 * 
 * === Recommendations ===
 * ✓ No obvious leaks detected
 * [Metrics]
 *   Proxy Growth: 3 (60.0%)
 *   Lua Memory: +40.00 KB
 * </pre>
 * 
 * <h3>Leak Detected</h3>
 * <pre>
 * === Snapshot Comparison: app_start -> app_end ===
 * Lua Memory:   83.51 MB -> 93.28 MB (+9.77 MB)
 * Java Heap:    308.00 MB -> 348.00 MB (+40.00 MB)
 * Proxy Count:  192 -> 216 (+24)  // Growing proxies = LEAK!
 * 
 * === Recommendations ===
 * ⚠ LEAK DETECTED!
 * 
 * [Proxy Leak] +24 proxies (12.5% growth)
 *   Root Cause: LuaValueProxy objects not being garbage collected
 *   Solutions:
 *     1. Call luaState.cleanup() every 50-100 operations
 *     2. Don't store AbstractTableMap/List in long-lived collections
 *     3. Use try-finally to ensure cleanup() is always called
 * </pre>
 * 
 * <h2>Diagnostic Features</h2>
 * 
 * <ul>
 *   <li><b>Memory Snapshots</b>: Capture Lua and Java memory state at any point</li>
 *   <li><b>Proxy Tracking</b>: Monitor number of unreleased Lua references</li>
 *   <li><b>Leak Detection</b>: Automatic detection with 5% growth threshold</li>
 *   <li><b>Trace Logging</b>: Enable detailed GC trace messages</li>
 *   <li><b>Specific Recommendations</b>: Get actionable suggestions for fixing leaks</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * 
 * <ol>
 *   <li><b>Call cleanup() every 50-100 operations</b>: This is CRITICAL!
 *       <pre>if (i % 100 == 0) luaState.cleanup();</pre></li>
 *   <li><b>Don't store table proxies long-term</b>: Extract data and release references</li>
 *   <li><b>Use try-finally</b>: Ensure cleanup happens even with exceptions</li>
 *   <li><b>Monitor in production</b>: Use snapshots to track memory over time</li>
 *   <li><b>Test with diagnostics</b>: Enable in tests to catch leaks early</li>
 * </ol>
 * 
 * @see com.naef.jnlua.debug.LuaMemoryDiagnostics
 */
package com.naef.jnlua.debug;
