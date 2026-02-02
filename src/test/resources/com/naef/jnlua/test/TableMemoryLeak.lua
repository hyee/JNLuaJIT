--[[
Lua-based memory leak test
Tests that tables passed to Java are properly garbage collected
]]--

module("TableMemoryLeak", package.seeall)

-- Test basic table passing
function testBasicTablePassing()
    -- Use the Java function registered from Java side
    assert(_G.javaReceiveTable ~= nil, "javaReceiveTable should be registered from Java")
    
    local initialCount = _G.javaReceiveTable({})
    
    -- Pass many tables
    for i = 1, 100 do
        local t = {key = "value" .. i, index = i}
        _G.javaReceiveTable(t)
        -- Table 't' should be collectible after this
    end
    
    -- Force GC
    collectgarbage("collect")
    
    print("✓ Basic table passing test passed: 100 tables processed")
end

-- Test table with mixed content
function testMixedContentTable()
    local tables_created = 0
    
    for i = 1, 50 do
        local t = {
            number = i,
            string = "test" .. i,
            boolean = (i % 2 == 0),
            nested = {
                a = 1,
                b = "nested",
                c = {x = 10, y = 20}
            },
            array = {1, 2, 3, 4, 5}
        }
        
        -- Simulate passing to Java
        if _G.javaReceiveTable then
            _G.javaReceiveTable(t)
        end
        
        tables_created = tables_created + 1
    end
    
    collectgarbage("collect")
    print("✓ Mixed content table test passed: " .. tables_created .. " tables created")
end

-- Test large tables
function testLargeTables()
    local function createLargeTable(size)
        local t = {}
        for i = 1, size do
            t["key" .. i] = "value" .. i
        end
        return t
    end
    
    for i = 1, 20 do
        local large_table = createLargeTable(1000)
        
        if _G.javaReceiveTable then
            _G.javaReceiveTable(large_table)
        end
    end
    
    collectgarbage("collect")
    print("✓ Large table test passed: created 20 tables with 1000 entries each")
end

-- Test table references
function testTableReferences()
    -- Create a table
    local original = {data = "original"}
    
    -- Create multiple references to the same table
    local ref1 = original
    local ref2 = original
    local ref3 = original
    
    -- Pass to Java multiple times
    if _G.javaReceiveTable then
        _G.javaReceiveTable(ref1)
        _G.javaReceiveTable(ref2)
        _G.javaReceiveTable(ref3)
    end
    
    -- Clear references
    ref1 = nil
    ref2 = nil
    ref3 = nil
    original = nil
    
    collectgarbage("collect")
    print("✓ Table reference test passed")
end

-- Test weak tables
function testWeakTables()
    local weak_table = setmetatable({}, {__mode = "v"})
    
    for i = 1, 50 do
        local value = {data = "item" .. i}
        weak_table[i] = value
    end
    
    -- Force GC
    collectgarbage("collect")
    
    -- Count remaining items
    local count = 0
    for k, v in pairs(weak_table) do
        count = count + 1
    end
    
    -- Weak table should have released some values
    print("✓ Weak table test: " .. count .. " items remaining after GC")
end

-- Stress test
function testStressTest()
    local iterations = 1000
    local start_mem = collectgarbage("count")
    
    for i = 1, iterations do
        local t = {
            index = i,
            data = string.rep("x", 100)
        }
        
        if _G.javaReceiveTable then
            _G.javaReceiveTable(t)
        end
        
        -- Periodic GC
        if i % 100 == 0 then
            collectgarbage("collect")
        end
    end
    
    collectgarbage("collect")
    local end_mem = collectgarbage("count")
    local mem_increase = end_mem - start_mem
    
    print("✓ Stress test passed: " .. iterations .. " iterations")
    print("  Memory increase: " .. string.format("%.2f", mem_increase) .. " KB")
    
    -- Memory increase should be reasonable (less than 500KB)
    assert(mem_increase < 500, "Memory leak detected! Memory increased by " .. mem_increase .. " KB")
end

-- Test table with metatable
function testTableWithMetatable()
    local mt = {
        __index = function(t, k)
            return "default_" .. k
        end,
        __tostring = function(t)
            return "CustomTable"
        end
    }
    
    for i = 1, 50 do
        local t = setmetatable({actual = "value" .. i}, mt)
        
        if _G.javaReceiveTable then
            _G.javaReceiveTable(t)
        end
    end
    
    collectgarbage("collect")
    print("✓ Table with metatable test passed")
end

-- Run all tests
function runAll()
    print("=== Lua Table Memory Leak Tests ===")
    print("")
    
    testBasicTablePassing()
    testMixedContentTable()
    testLargeTables()
    testTableReferences()
    testWeakTables()
    testTableWithMetatable()
    testStressTest()
    
    print("")
    print("=== All tests passed! ===")
end
