package com.example.metrodronemidi;

import java.util.TimerTask;

import org.billthefarmer.mididriver.MidiDriver;

class PlayNoteTask extends TimerTask {

    MidiDriverHelper midi;
    int pitch;
    int octave;

    public PlayNoteTask(MidiDriverHelper midi, int pitch, int octave) {
        this.pitch = pitch;
        this.octave = octave;
        this.midi = midi;
    }

    public void run() {
        startNote();
    }

    public boolean cancel() {
        super.cancel();
        endNote();
        return super.cancel();
    }

    // Starts a new note
    protected void startNote() {
        // Note start message
        final int noteOnByte = 0x90; // Starts the note
        final int volumeByte = 0x60; // Loud
        midi.send(noteOnByte, MidiDriverHelper.encodePitch(pitch, octave), volumeByte);
    }

    // Ends a note
    protected void endNote() {
        final int noteOffByte = 0x80; // Ends the note
        final int volumeByte = 0x0; // Silence
        midi.send(noteOffByte, MidiDriverHelper.encodePitch(pitch, octave), volumeByte);
    }
}
