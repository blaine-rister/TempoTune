package com.bbrister.tempodrone;

import android.util.Log;

/**
 * Wrapper class for 'log' which only works in debug builds.
 */
public class DebugLog {
    public static void e(final String tag, final String msg) {
        if (!BuildConfig.DEBUG_EXCEPTIONS)
            return;

        Log.e(tag, msg);
    }
}
