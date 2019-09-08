package com.bbrister.metrodrone;

import org.billthefarmer.mididriver.MidiDriver;

public class MidiDriverHelper extends MidiDriver {

    // Constants
    final static int keyMax = 127;

    // Convert BPM to ms per beat
    public static double getMsPerBeat(int bpm) {
        final long msPerMinute = 60 * 1000;
        return msPerMinute / bpm;
    }

    // Converts a pitch class (0-11) using the octave setting
    public static byte encodePitch(int pitch, int octave) {
        final int firstPitch = 21; // MIDI number of A0
        final int pitchesPerOctave = 12; // 12 pitches in an octave
        return (byte) (pitch + pitchesPerOctave * octave + firstPitch);
    }

    // Convert [0-1] to byte velocity
    public static byte encodeVelocity(double velocity) {
        final double maxVelocity = 127.;
        return (byte) Math.floor(velocity * maxVelocity);
    }
}
