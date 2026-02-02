package com.naef.jnlua.test;

public class NativeLibraryTest {
    public static void main(String[] args) {
        System.out.println("=== Native Library Test ===");
        String libraryPath = System.getProperty("java.library.path");
        System.out.println("java.library.path = " + libraryPath);
        
        // Check if jnlua library exists in path
        boolean foundLib = false;
        for (String path : libraryPath.split(";")) {
            java.io.File dir = new java.io.File(path);
            if (dir.exists() && dir.isDirectory()) {
                String[] files = dir.list();
                if (files != null) {
                    for (String file : files) {
                        if (file.contains("jnlua") && (file.endsWith(".dll") || file.endsWith(".so"))) {
                            System.out.println("✓ Found: " + path + "\\" + file);
                            foundLib = true;
                        }
                    }
                }
            }
        }
        
        if (!foundLib) {
            System.err.println();
            System.err.println("=== WARNING ===");
            System.err.println("No jnlua library found in java.library.path!");
            System.err.println("Please add VM option: -Djava.library.path=<path-to-jnlua-dll>");
            System.err.println("Example: -Djava.library.path=D:\\JavaProjects\\JNLua\\src\\main\\c\\Win32");
            System.err.println();
        }
        System.out.println();
        
        try {
            System.out.println("[1] Loading LuaState class...");
            Class<?> luaStateClass = Class.forName("com.naef.jnlua.LuaState");
            System.out.println("    ✓ LuaState class loaded");
            
            System.out.println("[2] Accessing LuaState.VERSION...");
            Object version = luaStateClass.getField("VERSION").get(null);
            System.out.println("    ✓ VERSION = " + version);
            
            System.out.println("[3] Accessing LuaState.LUA_VERSION...");
            Object luaVersion = luaStateClass.getField("LUA_VERSION").get(null);
            System.out.println("    ✓ LUA_VERSION = " + luaVersion);
            
            System.out.println("[4] Creating LuaState instance...");
            Object luaState = luaStateClass.newInstance();
            System.out.println("    ✓ LuaState instance created");
            
            System.out.println();
            System.out.println("=== All Tests Passed ===");
            
        } catch (Throwable e) {
            System.err.println();
            System.err.println("=== ERROR ===");
            System.err.println("Failed at step: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
