package com.naef.jnlua.test;

import com.esotericsoftware.reflectasm.ClassAccess;
import com.naef.jnlua.test.fixture.TestObject;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Test to verify that TestObject doesn't have a member named "pause11"
 */
public class TestObjectMemberTest {
    
    @Test
    public void testPause11DoesNotExist() {
        ClassAccess<TestObject> access = ClassAccess.access(TestObject.class);
        Set<String> members = access.classInfo.attrIndex.keySet();
        
        System.out.println("=== All members of TestObject ===");
        for (String member : members) {
            char id = member.charAt(0);
            String name = member.substring(1);
            String type = id == 1 ? "FIELD" : id == 2 ? "METHOD" : "CONSTRUCTOR";
            System.out.println(type + ": " + name);
            
            // Check if pause11 exists in any form
            if (name.contains("pause11")) {
                fail("Found unexpected member: " + type + " - " + name);
            }
        }
        
        // Verify getNameType returns null for pause11
        String type = access.getNameType("pause11");
        assertNull("pause11 should not exist, but getNameType returned: " + type, type);
        
        System.out.println("✓ Verified: pause11 does not exist in TestObject");
    }
}
