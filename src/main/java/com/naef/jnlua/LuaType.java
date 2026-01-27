/*
 * $Id: LuaType.java 121 2012-01-22 01:40:14Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

/**
 * Represents a Lua type.
 */
public enum LuaType {
    NIL(0), BOOLEAN(1), LIGHTUSERDATA(2), NUMBER(3), STRING(4),
    TABLE(5), FUNCTION(6), USERDATA(7), THREAD(8),
    JAVAFUNCTION(9), JAVAOBJECT(10);

    public final byte id;

    LuaType(int id) {
        this.id = (byte) id;
    }
    // -- Properties

    /**
     * Returns the display text of this Lua type. The display text is the type
     * name in lower case.
     *
     * @return the display text
     */
    public String displayText() {
        return toString().toLowerCase();
    }

    public final static LuaType get(int emu) {
        return emu > -1 ? values()[emu % 16] : null;
    }
}
