package com.bbrister.mididriver;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

public class PlaybackDriver {

    // Constants
    private static final String logTag = "Playback";

    // Shortcut to query a parameter from the Audio manager. Returns -1 on failure.
    private static int getAudioProperty(Context context, String tag) {

        final int audioParamsSdkVersion = 17;

        if (android.os.Build.VERSION.SDK_INT >= audioParamsSdkVersion) {
            // Query the device sample rate
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            return Integer.parseInt(am.getProperty(tag));
        } else {
            if (BuildConfig.DEBUG) {
                Log.w(logTag, String.format("Cannot query device audio parameters since " +
                                "current SDK version %d < %d", Build.VERSION.SDK_INT,
                        audioParamsSdkVersion));
            }
            return -1;
        }
    }

    // Return the playback sample rate
    public static int getSampleRate(Context context) {
        // Query the device audio parameters, on supported devices
        int sampleRate = getAudioProperty(context, AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);

        // Substitute default parameters if querying failed
        if (sampleRate < 1) {
            sampleRate = 44100;
            if (BuildConfig.DEBUG) {
                Log.w(logTag, String.format("Failed to query the device sample " +
                        "rate. Defaulting to %d", sampleRate));
            }
        }

        return sampleRate;
    }

    // Return the playback buffer size
    public static int getBufferSize(Context context) {
        // Query the device audio parameters, on supported devices
        int bufferSize = getAudioProperty(context, AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);

        // Substitute default parameters if querying failed
        if (bufferSize < 1) {
            bufferSize = 256;
            if (BuildConfig.DEBUG) {
                Log.w(logTag, String.format("Failed to query the device buffer " +
                        "size. Defaulting to %d", bufferSize));
            }
        }

        return bufferSize;
    }

    /*
     * Initiate playback.
     */
    public void play(Context context, float[] sound) {
        if (!playJNI(getSampleRate(context), getBufferSize(context), sound))
            throw new RuntimeException(BuildConfig.DEBUG ? "Failed to start playback" : "");
    }

    /*
     * Pause playback and check for errors.
     */
    public void pause() {
        if (!pauseJNI())
            throw new RuntimeException(BuildConfig.DEBUG ? "Failed to pause playback" : "");
    }

    /**
     * Play the sound.
     * @return true on success.
     */
    private boolean playJNI(final int sampleRate, final int bufferSizeMono, final float[] sound) {
        return B(sampleRate, bufferSizeMono, sound);
    }
    private native boolean B(int sampleRate, int bufferSizeMono, float[] sound);

    /**
     * Pause the sound.
     * @return true on success.
     */
    private boolean pauseJNI() {
        return C();
    }
    private native boolean C();

    // Load playback library
    static
    {
        System.loadLibrary("playback");
    }
}
