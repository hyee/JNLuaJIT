/*
 * $Id: LuaError.java 154 2012-02-01 20:40:01Z andre@naef.com $
 * See LICENSE.txt for license terms.
 */
package com.naef.jnlua;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

/**
 * Contains information about a Lua error condition. This object is created in
 * the native library.
 */
class LuaError {
    // -- State
    private final String message;
    private LuaStackTraceElement[] luaStackTrace;
    private Throwable cause;

    // -- Construction

    /**
     * Creates a new instance.
     */
    public LuaError(String message, Throwable cause) {
        this.message = message;
        this.cause = cause;
    }

    // -- Properties

    /**
     * Returns the message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the Lua stack trace.
     */
    public LuaStackTraceElement[] getLuaStackTrace() {
        return luaStackTrace;
    }

    /**
     * Sets the Lua stack trace.
     */
    void setLuaStackTrace(LuaStackTraceElement[] luaStackTrace) {
        this.luaStackTrace = luaStackTrace;
    }

    /**
     * Returns the cause.
     */
    public Throwable getCause() {
        return cause;
    }

    // -- Package private methods

    // -- Object methods
    @Override
    public String toString() {
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause != null) {
            StringWriter sw = new StringWriter();
            cause.printStackTrace(new PrintWriter(sw));
            return sw.toString();
        } else return message;
    }

    public Integer getErrorCode() {
        if (cause instanceof SQLException) {
            return ((SQLException) cause).getErrorCode();
        }
        return null;
    }

    public String getSQLState() {
        if (cause instanceof SQLException) {
            return ((SQLException) cause).getSQLState();
        }
        return null;
    }
}
