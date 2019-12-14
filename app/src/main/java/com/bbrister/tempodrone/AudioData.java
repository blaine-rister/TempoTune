package com.bbrister.tempodrone;

/**
 * Singleton class for passing around the audio data stream. We need this since Intent has a data
 * size limit. (Note: this is not exactly a singleton because it allows for persistence of old
 * instances, but only one is queryable at a given time. Is there a name for this?)
 */
public class AudioData {

    private static AudioData instance = null;

    private float[] data;

    /**
     * Prevents instantiation outside this class.
     */
    private AudioData() {};

    /**
     * Internal method of setting the data.
     */
    private AudioData(final float[] data) {
        this.data = data;
    }

    /**
     * Possibly returns null if no data has been set.
     */
    public static AudioData getInstance() {
        return instance;
    }

    public float[] getData() {
        return data;
    }

    /**
     * Create a new instance with this data. Data is read-only within in an instance to avoid
     * conflicts with other references.
     */
    public static void setData(final float[] data) {
        // Create a new instance with data
        instance = new AudioData(data);
    }

    /**
     * Releases the internal reference to this instance. It will be garbage collected as soon as no
     * one holds a reference to it.
     */
    public void release() {
        if (instance == this) { //FIXME is this correct?
            instance = null;
        }
    }
}
