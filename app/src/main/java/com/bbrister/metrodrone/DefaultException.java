package com.bbrister.metrodrone;

// Throws a runtime exception with a default message
public class DefaultException extends RuntimeException {
    public static final String msg = "Message optimized out.";
    public DefaultException() {
        super(msg);
        if (BuildConfig.DEBUG_EXCEPTIONS) {
            throw new RuntimeException("DefaultException should not be used in debug builds!");
        }
    }
}
