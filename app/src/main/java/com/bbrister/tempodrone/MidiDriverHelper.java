package com.bbrister.tempodrone;

import com.bbrister.mididriver.MidiDriver;

public class MidiDriverHelper extends MidiDriver {

    // Public constants
    final public static int keyMax = 127;

    // Private constants
    final private static int firstPitch = 21; // MIDI number of A0
    final private static int pitchesPerOctave = 12; // 12 pitches in an octave

    // Convert BPM to ms per beat
    public static double getMsPerBeat(int bpm) {
        final long msPerMinute = 60 * 1000;
        return msPerMinute / bpm;
    }

    // Converts a pitch class (0-11) and octave to a MIDI key
    public static byte encodePitch(int pitch, int octave) {
        return (byte) (pitch + pitchesPerOctave * octave + firstPitch);
    }

    // Converts a MIDI key to a pitch class (0-11)
    public static int decodePitchClass(byte key) {
        return (key - firstPitch) % pitchesPerOctave;
    }

    // Converts a MIDI key to an octave
    public static int decodeOctave(byte key) {
        return (key - firstPitch - decodePitchClass(key)) / pitchesPerOctave;
    }

    // Convert [0-1] to byte velocity. Values range from [1,127].
    public static byte encodeVelocity(double velocity) {
        final double minVelocity = 1.;
        final double maxVelocity = 127.;
        return (byte) (Math.floor(velocity * (maxVelocity - minVelocity)) + minVelocity);
    }
}
