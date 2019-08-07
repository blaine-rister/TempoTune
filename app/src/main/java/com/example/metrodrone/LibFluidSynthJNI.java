package com.example.metrodrone;

public class LibFluidSynthJNI {
    static {
        System.loadLibrary("fluidsynthJNI");
    }

    public final int success = 0;

    public native int init();
    public native int load_soundfont(String filename);
    public native int note_on(int channel, int key, int velocity);
    public native int note_off(int channel, int key);
    public native int all_notes_off(int channel);
    public native int all_sounds_off(int channel);
    public native int program_change(int channel, int program_number);
}
