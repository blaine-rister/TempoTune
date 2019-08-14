package com.example.metrodrone;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.Binder;
import java.util.List;

import org.billthefarmer.mididriver.MidiDriver;

// Responsible for playing sound through the MIDI driver //
public class DroneService extends Service implements MidiDriver.OnMidiStartListener {

    // Create midi driver
    protected MidiDriverHelper midi;
    protected MediaPlayer player; // TODO do we need this?

    // Create binder to return on binding
    final IBinder droneBinder = new DroneBinder();

    // State
    protected boolean isPlaying = false;

    public void onCreate() {
        super.onCreate();

        // Set on midi start listener
        midi = new MidiDriverHelper();
        midi.setOnMidiStartListener(this);
    }

    // Interface for the main activity
    public class DroneBinder extends Binder {
        void play(int bpm, List<Note> notes, int instrument, NoteSettingsReader settings) {
            DroneService.this.play(bpm, notes, instrument, settings);
        }
        void pause() {
            DroneService.this.pause();
        }
        void stop() { DroneService.this.stopSelf(); }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return droneBinder;
    }

    public void play(int bpm, List<Note> notes, int instrument,
                     NoteSettingsReader settings) {
        // Reset the state, cancelling old notes
        if (isPlaying) pause();

        // Start the midi
        midi.start();
        //TODO: Do we need to start the player? The example app didn't have it

        // Set the instrument
        midi.changeProgram((byte) instrument);

        // Calculate the note and beat durations
        final double msPerBeat = MidiDriverHelper.getMsPerBeat(bpm);
        final long beatDurationMs = Math.round(msPerBeat);
        final long noteDurationMs = Math.round(msPerBeat * settings.getDuration());

        // Encode the pitches to be played
        final int numPitches = notes.size();
        byte[] pitches = new byte[numPitches];
        for (int i = 0; i < numPitches; i++) {
            final Note note = notes.get(i);
            pitches[i] = MidiDriverHelper.encodePitch(note.getPitch(), note.getOctave());
        };

        // Play the sound
        final byte velocity = MidiDriverHelper.encodeVelocity(settings.getVelocity());
        midi.renderNotes(pitches, velocity, noteDurationMs, beatDurationMs);
        isPlaying = true;
    }

    // Pause playing
    public void pause() {
        // Stop midi
        if (midi != null) midi.stop();

        // Stop player
        if (player != null) player.stop();

        isPlaying = false;
    }

    // Listener for sending initial midi messages when the Sonivox
    // synthesizer has been started, such as program change.
    @Override
    public void onMidiStart()
    {
        // Nothing happens here, the program is set in play()
    }
}
