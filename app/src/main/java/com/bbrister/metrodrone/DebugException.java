package com.bbrister.metrodrone;

// An exception that will throw the given message in debug mode. In runtime mode, throws a generic
// message.
public class DebugException extends RuntimeException {
    public DebugException(String str) {
        super(BuildConfig.DEBUG ? str : DefaultException.msg);
    }
}
