package com.example.metrodrone;

import android.os.Handler;

import java.util.TimerTask;

public class PlayNoteTask extends TimerTask {

    MidiDriverHelper midi;
    int pitch;
    int octave;
    int bpm;
    NoteSettingsReader settings;
    Handler endNoteHandler;
    boolean isPlaying;

    public PlayNoteTask(MidiDriverHelper midi, int pitch, int octave, int bpm,
                        NoteSettingsReader settings) {
        this.pitch = pitch;
        this.octave = octave;
        this.midi = midi;
        this.settings = settings;
        this.bpm = bpm;
        endNoteHandler = new Handler();
        isPlaying = false;
    }

    // Convert BPM to ms per beat
    public static double getMsPerBeat(int bpm) {
        final long msPerMinute = 60 * 1000;
        return msPerMinute / bpm;
    }

    public void run() {
        // Start the note
        startNote();

        // Calculate the note duration in ms
        final long durationMs = (long) Math.floor(getMsPerBeat(bpm) * settings.getDuration());

        // Schedule the end of the note
        endNoteHandler.postDelayed(new Runnable() {
            @Override
            public void run () {
                endNote();
            }}, durationMs);
    }

    public boolean cancel() {
        super.cancel();
        endNote();
        return super.cancel();
    }

    // Starts a new note
    protected void startNote() {
        // End the previous note
        if (isPlaying) endNote();

        // Note start message
        final int noteOnByte = 0x90; // Starts the note
        final int volumeByte = (int) Math.floor(settings.getVelocity() * 127.); // 0-127
        midi.send(noteOnByte, MidiDriverHelper.encodePitch(pitch, octave), volumeByte);
        isPlaying = true;
    }

    // Ends a note
    protected void endNote() {
        // End the note
        final int noteOffByte = 0x80; // Ends the note
        final int volumeByte = 0x0; // Silence
        midi.send(noteOffByte, MidiDriverHelper.encodePitch(pitch, octave), volumeByte);
        isPlaying = false;

        // Remove any scheduled calls to end the note
        endNoteHandler.removeCallbacksAndMessages(null);
    }
}
