package com.naef.jnlua.debug;

import com.naef.jnlua.LuaState;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Diagnostic tool for monitoring Lua-Java memory usage and detecting leaks.
 * <p>
 * Usage:
 * <pre>
 * // 1. Enable diagnostics
 * LuaMemoryDiagnostics.enable();
 *
 * // 2. Take snapshots at key points
 * LuaMemoryDiagnostics.snapshot("before_operation");
 * // ... your code ...
 * LuaMemoryDiagnostics.snapshot("after_operation");
 *
 * // 3. Print report
 * LuaMemoryDiagnostics.printReport();
 *
 * // 4. Check for leaks
 * if (LuaMemoryDiagnostics.hasLeak()) {
 *     System.out.println("Memory leak detected!");
 * }
 * </pre>
 */
public class LuaMemoryDiagnostics {

    private static volatile boolean enabled = false;
    private static final Map<String, Snapshot> snapshots = new HashMap<>();
    private static final AtomicInteger tableCreationCount = new AtomicInteger(0);
    private static final AtomicInteger tableReleaseCount = new AtomicInteger(0);
    private static final AtomicLong peakLuaMemory = new AtomicLong(0);
    private static final AtomicLong peakJavaHeap = new AtomicLong(0);

    /**
     * Enable diagnostics and tracing
     */
    public static void enable() {
        enabled = true;
        System.out.println("[LuaMemoryDiagnostics] Diagnostics enabled with trace level 5");
    }

    /**
     * Disable diagnostics
     */
    public static void disable() {
        enabled = false;
    }

    /**
     * Set trace level (0-9)
     * - 0: No trace
     * - 1: Basic trace (5 & 1 = 1)
     * - 5: Detailed GC trace (5 & 5 = 5)
     * - 9: All trace
     */
    public static void setTraceLevel(int level) {
        try {
            Field traceField = LuaState.class.getDeclaredField("trace");
            traceField.setAccessible(true);
            traceField.set(null, level);
            System.out.println("[LuaMemoryDiagnostics] Trace level set to " + level);
        } catch (Exception e) {
            System.err.println("[LuaMemoryDiagnostics] Failed to set trace level: " + e.getMessage());
        }
    }

    /**
     * Take a memory snapshot with a label
     */
    public static Snapshot snapshot(String label) {
        if (!enabled) return null;

        Snapshot snapshot = new Snapshot(label);
        snapshots.put(label, snapshot);

        // Update peaks
        if (snapshot.luaMemory > peakLuaMemory.get()) {
            peakLuaMemory.set(snapshot.luaMemory);
        }
        if (snapshot.javaHeapUsed > peakJavaHeap.get()) {
            peakJavaHeap.set(snapshot.javaHeapUsed);
        }

        return snapshot;
    }

    /**
     * Take snapshot for a LuaState
     */
    public static Snapshot snapshot(String label, LuaState luaState) {
        if (!enabled) return null;

        Snapshot snapshot = new Snapshot(label, luaState);
        snapshots.put(label, snapshot);

        if (snapshot.luaMemory > peakLuaMemory.get()) {
            peakLuaMemory.set(snapshot.luaMemory);
        }
        if (snapshot.javaHeapUsed > peakJavaHeap.get()) {
            peakJavaHeap.set(snapshot.javaHeapUsed);
        }

        return snapshot;
    }

    /**
     * Get proxy set size (number of unreleased Lua references)
     */
    public static int getProxySetSize(LuaState luaState) {
        try {
            Field proxySetField = LuaState.class.getDeclaredField("proxySet");
            proxySetField.setAccessible(true);
            Object proxySet = proxySetField.get(luaState);
            if (proxySet instanceof java.util.Set) {
                return ((java.util.Set<?>) proxySet).size();
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    /**
     * Force cleanup on LuaState
     */
    public static int forceCleanup(LuaState luaState) {
        int proxySetSizeBefore = getProxySetSize(luaState);

        // Call cleanup multiple times
        for (int i = 0; i < 3; i++) {
            luaState.cleanup();
        }

        // Force Java GC
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.gc();

        // Call cleanup again
        for (int i = 0; i < 3; i++) {
            luaState.cleanup();
        }

        // Force Lua GC
        luaState.gc(LuaState.GcAction.COLLECT, 0);
        luaState.gc(LuaState.GcAction.COLLECT, 0);

        int proxySetSizeAfter = getProxySetSize(luaState);
        int cleaned = proxySetSizeBefore - proxySetSizeAfter;

        if (enabled) {
            System.out.println("[LuaMemoryDiagnostics] Cleanup: " + cleaned + " proxies released (before=" +
                    proxySetSizeBefore + ", after=" + proxySetSizeAfter + ")");
        }

        return cleaned;
    }

    /**
     * Compare two snapshots
     */
    public static void compare(String label1, String label2) {
        Snapshot s1 = snapshots.get(label1);
        Snapshot s2 = snapshots.get(label2);

        if (s1 == null || s2 == null) {
            System.out.println("[LuaMemoryDiagnostics] Snapshot not found");
            return;
        }

        System.out.println("=== Snapshot Comparison: " + label1 + " -> " + label2 + " ===");
        System.out.println("Lua Memory:   " + formatBytes(s1.luaMemory) + " -> " + formatBytes(s2.luaMemory) +
                " (" + formatDelta(s2.luaMemory - s1.luaMemory) + ")");
        System.out.println("Java Heap:    " + formatBytes(s1.javaHeapUsed) + " -> " + formatBytes(s2.javaHeapUsed) +
                " (" + formatDelta(s2.javaHeapUsed - s1.javaHeapUsed) + ")");
        System.out.println("Proxy Count:  " + s1.proxySetSize + " -> " + s2.proxySetSize +
                " (" + formatDelta(s2.proxySetSize - s1.proxySetSize) + ")");
        System.out.println();
    }

    /**
     * Print full diagnostic report
     */
    public static void printReport() {
        System.out.println("========================================");
        System.out.println("  Lua Memory Diagnostics Report");
        System.out.println("========================================");
        System.out.println("Peak Lua Memory:  " + formatBytes(peakLuaMemory.get()));
        System.out.println("Peak Java Heap:   " + formatBytes(peakJavaHeap.get()));
        System.out.println("Table Created:    " + tableCreationCount.get());
        System.out.println("Table Released:   " + tableReleaseCount.get());
        System.out.println("Table Leaked:     " + (tableCreationCount.get() - tableReleaseCount.get()));
        System.out.println();

        System.out.println("Snapshots:");
        snapshots.forEach((label, snapshot) -> {
            System.out.println("  [" + label + "]");
            System.out.println("    Lua Memory: " + formatBytes(snapshot.luaMemory));
            System.out.println("    Java Heap:  " + formatBytes(snapshot.javaHeapUsed));
            System.out.println("    Proxies:    " + snapshot.proxySetSize);
        });
        System.out.println("========================================");
    }

    /**
     * Check if there's a potential leak
     */
    public static boolean hasLeak() {
        if (snapshots.size() < 2) return false;

        Snapshot first = snapshots.values().iterator().next();
        Snapshot last = null;
        for (Snapshot s : snapshots.values()) {
            last = s;
        }

        if (last == null) return false;

        // More sensitive proxy count check
        // Even small increases (>10 proxies or >5% growth) indicate potential leak
        int proxyGrowth = last.proxySetSize - first.proxySetSize;
        double proxyGrowthPercent = first.proxySetSize > 0 ?
                (proxyGrowth * 100.0 / first.proxySetSize) : 0;

        // Leak detected if:
        // 1. Absolute growth > 10 proxies AND
        // 2. Percentage growth > 5% OR absolute count > 100
        if (proxyGrowth > 10 && (proxyGrowthPercent > 5.0 || last.proxySetSize > 100)) {
            return true;
        }

        // Check if Lua memory is growing significantly
        // More than 10MB growth is suspicious
        long luaMemoryGrowth = last.luaMemory - first.luaMemory;
        if (luaMemoryGrowth > 10 * 1024 * 1024) {
            return true;
        }

        return false;
    }

    /**
     * Clear all snapshots
     */
    public static void clear() {
        snapshots.clear();
        tableCreationCount.set(0);
        tableReleaseCount.set(0);
        peakLuaMemory.set(0);
        peakJavaHeap.set(0);
    }

    /**
     * Get recommendations based on current diagnostics
     */
    public static void printRecommendations() {
        System.out.println("=== Recommendations ===");

        if (snapshots.size() < 2) {
            System.out.println("⚠ Not enough data to analyze (need at least 2 snapshots)");
            System.out.println("  - Take snapshots before and after operations");
            System.out.println();
            return;
        }

        // Get first and last snapshots
        Snapshot first = snapshots.values().iterator().next();
        Snapshot last = null;
        for (Snapshot s : snapshots.values()) {
            last = s;
        }

        if (last == null) {
            System.out.println("⚠ No snapshots available");
            System.out.println();
            return;
        }

        // Calculate metrics
        int proxyGrowth = last.proxySetSize - first.proxySetSize;
        double proxyGrowthPercent = first.proxySetSize > 0 ?
                (proxyGrowth * 100.0 / first.proxySetSize) : 0;
        long luaMemoryGrowth = last.luaMemory - first.luaMemory;
        long javaHeapGrowth = last.javaHeapUsed - first.javaHeapUsed;

        if (hasLeak()) {
            System.out.println("⚠ LEAK DETECTED!");
            System.out.println();

            // Specific diagnosis
            if (proxyGrowth > 10) {
                System.out.println("[Proxy Leak] +" + proxyGrowth + " proxies (" +
                        String.format("%.1f%%", proxyGrowthPercent) + " growth)");
                System.out.println("  Root Cause: LuaValueProxy objects not being garbage collected");
                System.out.println("  Solutions:");
                System.out.println("    1. Call luaState.cleanup() every 50-100 operations");
                System.out.println("    2. Don't store AbstractTableMap/List in long-lived collections");
                System.out.println("    3. Use try-finally to ensure cleanup() is always called");
                System.out.println();
                System.out.println("  Example:");
                System.out.println("    for (int i = 0; i < count; i++) {");
                System.out.println("        Map table = luaState.toJavaObject(-1, Map.class);");
                System.out.println("        // Process table...");
                System.out.println("        if (i % 100 == 0) luaState.cleanup();  // KEY!");
                System.out.println("    }");
                System.out.println();
            }

            if (luaMemoryGrowth > 10 * 1024 * 1024) {
                System.out.println("[Lua Memory Leak] +" + formatBytes(luaMemoryGrowth));
                System.out.println("  Root Cause: Lua objects not being collected");
                System.out.println("  Solutions:");
                System.out.println("    1. Call luaState.gc(GcAction.COLLECT, 0) periodically");
                System.out.println("    2. Ensure Java references to tables are released");
                System.out.println("    3. Check for circular references in Lua");
                System.out.println();
            }

            System.out.println("[Action Required]");
            System.out.println("  1. Add cleanup() calls in your code");
            System.out.println("  2. Use forceCleanup() to verify cleanup effectiveness");
            System.out.println("  3. Monitor proxy count after cleanup");

        } else {
            System.out.println("✓ No obvious leaks detected");
            System.out.println();
            System.out.println("[Metrics]");
            System.out.println("  Proxy Growth: " + proxyGrowth + " (" +
                    String.format("%.1f%%", proxyGrowthPercent) + ")");
            System.out.println("  Lua Memory: " + formatDelta(luaMemoryGrowth));
            System.out.println("  Java Heap: " + formatDelta(javaHeapGrowth));
            System.out.println();
            System.out.println("[Best Practices]");
            System.out.println("  - Continue calling cleanup() every 50-100 operations");
            System.out.println("  - Monitor for longer runs to ensure stability");
            System.out.println("  - Consider adding more snapshots for detailed tracking");
        }
        System.out.println();
    }

    // Helper methods

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
    }

    private static String formatDelta(long delta) {
        String sign = delta >= 0 ? "+" : "";
        return sign + formatBytes(delta);
    }

    /**
     * Memory snapshot at a point in time
     */
    public static class Snapshot {
        public final String label;
        public final long timestamp;
        public final long luaMemory;
        public final long javaHeapUsed;
        public final long javaHeapMax;
        public final int proxySetSize;

        public Snapshot(String label) {
            this(label, null);
        }

        public Snapshot(String label, LuaState luaState) {
            this.label = label;
            this.timestamp = System.currentTimeMillis();

            // Get Lua memory
            if (luaState != null) {
                this.luaMemory = luaState.gc(LuaState.GcAction.COUNT, 0) * 1024L +
                        luaState.gc(LuaState.GcAction.COUNTB, 0);
                this.proxySetSize = getProxySetSize(luaState);
            } else {
                this.luaMemory = 0;
                this.proxySetSize = 0;
            }

            // Get Java heap
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            this.javaHeapUsed = heapUsage.getUsed();
            this.javaHeapMax = heapUsage.getMax();
        }

        @Override
        public String toString() {
            return String.format("Snapshot[%s: Lua=%s, Heap=%s, Proxies=%d]",
                    label, formatBytes(luaMemory), formatBytes(javaHeapUsed), proxySetSize);
        }
    }
}
