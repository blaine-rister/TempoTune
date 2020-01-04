package com.bbrister.tempodrone;

import java.util.Stack;

/**
 * Static class for passing around the audio data stream. We need this since Intent has a data
 * size limit. */
public class AudioData {

    private static Stack<float[]> stack;

    static {
        stack = new Stack<>();
    }

    /**
     * Push new data onto the stack.
     */
    public static synchronized void pushData(final float[] data) {
        stack.push(data);
    }

    /**
     * Retrieve data from the stack.
     */
    public static synchronized float[] popData() {
        return stack.pop();
    }
}
