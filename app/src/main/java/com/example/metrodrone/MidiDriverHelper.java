package com.example.metrodrone;

import org.billthefarmer.mididriver.MidiDriver;

public class MidiDriverHelper extends MidiDriver {
    // Send a midi message, 2 bytes
    public void send(int m, int n)
    {
        byte msg[] = new byte[2];

        msg[0] = (byte) m;
        msg[1] = (byte) n;

        this.write(msg);
    }

    // Send a midi message, 3 bytes
    public void send(int m, int n, int v)
    {
        byte msg[] = new byte[3];

        msg[0] = (byte) m;
        msg[1] = (byte) n;
        msg[2] = (byte) v;

        this.write(msg);
    }

    // Converts a pitch class (0-11) using the octave setting
    public static int encodePitch(int pitch, int octave) {
        final int firstPitch = 21; // MIDI number of A0
        final int pitchesPerOctave = 12; // 12 pitches in an octave
        return pitch + pitchesPerOctave * octave + firstPitch;
    }
}
