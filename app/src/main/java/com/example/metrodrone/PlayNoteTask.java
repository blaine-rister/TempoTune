package com.example.metrodrone;

import java.util.TimerTask;

class PlayNoteTask extends TimerTask {

    MidiDriverHelper midi;
    int pitch;
    int octave;
    NoteSettingsReader settings;

    public PlayNoteTask(MidiDriverHelper midi, int pitch, int octave, NoteSettingsReader settings) {
        this.pitch = pitch;
        this.octave = octave;
        this.midi = midi;
        this.settings = settings;
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
        final int volumeByte = (int) Math.floor(settings.getVelocity() * 127.); // 0-127
        midi.send(noteOnByte, MidiDriverHelper.encodePitch(pitch, octave), volumeByte);
    }

    // Ends a note
    protected void endNote() {
        final int noteOffByte = 0x80; // Ends the note
        final int volumeByte = 0x0; // Silence
        midi.send(noteOffByte, MidiDriverHelper.encodePitch(pitch, octave), volumeByte);
    }
}
