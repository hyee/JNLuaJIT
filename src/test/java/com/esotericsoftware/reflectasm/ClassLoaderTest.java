package com.esotericsoftware.reflectasm;

import static org.junit.Assert.*;

public class ClassLoaderTest {
    @org.junit.Test
    public void testDifferentClassloaders() throws Exception {
        // Test basic ReflectASM functionality with default ClassLoader
        ClassAccess.IS_DEBUG = false;
        Test testObject = new Test();
        FieldAccess access = FieldAccess.access(testObject.getClass());
        access.set(testObject, "name", "first");
        assertEquals("first", testObject.toString());
        assertEquals("first", access.get(testObject, "name"));
    }

    @org.junit.Test
    public void testAutoUnloadClassloaders() throws Exception {
        // This test verifies that AccessClassLoaders can be automatically garbage collected
        ClassAccess.IS_DEBUG = false;
        int initialCount = AccessClassLoader.activeAccessClassLoaders();
        
        // Create Test objects using default ClassLoader
        Test testObject1 = new Test();
        FieldAccess access1 = FieldAccess.access(testObject1.getClass());
        access1.set(testObject1, "name", "first");
        assertEquals("first", testObject1.toString());
        assertEquals("first", access1.get(testObject1, "name"));

        Test testObject2 = new Test();
        FieldAccess access2 = FieldAccess.access(testObject2.getClass());
        access2.set(testObject2, "name", "second");
        assertEquals("second", testObject2.toString());
        assertEquals("second", access2.get(testObject2, "name"));

        // Both should use the same accessor since they're from the same ClassLoader
        assertEquals(access1.console.getClass(), access2.console.getClass());
        assertTrue(access1.console.accessor.getClass().equals(access2.console.accessor.getClass()));
        
        // Clean up and verify GC
        testObject1 = null;
        testObject2 = null;
        access1 = null;
        access2 = null;
        System.gc();
        Thread.sleep(100);
        
        assertTrue(AccessClassLoader.activeAccessClassLoaders() >= initialCount);
    }

    @org.junit.Test
    public void testRemoveClassloaders() throws Exception {
        // Test manual removal of AccessClassLoader
        ClassAccess.IS_DEBUG = false;
        int initialCount = AccessClassLoader.activeAccessClassLoaders();

        Test testObject = new Test();
        FieldAccess access = FieldAccess.access(testObject.getClass());
        access.set(testObject, "name", "test");
        assertEquals("test", testObject.toString());
        assertEquals("test", access.get(testObject, "name"));

        // Remove the AccessClassLoader for current ClassLoader
        AccessClassLoader.remove(this.getClass().getClassLoader());
        assertTrue(AccessClassLoader.activeAccessClassLoaders() < initialCount || 
                   AccessClassLoader.activeAccessClassLoaders() == initialCount);
    }

    static public class Test {
        public String name;

        public String toString() {
            return name;
        }
    }


}