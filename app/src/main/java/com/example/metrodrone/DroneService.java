package com.example.metrodrone;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.IBinder;
import android.os.Binder;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.billthefarmer.mididriver.MidiDriver;

// Responsible for playing sound through the MIDI driver //
public class DroneService extends Service implements MidiDriver.OnMidiStartListener {

    // Create midi driver
    protected MidiDriverHelper midi;

    // Create binder to return on binding
    final IBinder droneBinder = new DroneBinder();

    // State
    protected boolean isPlaying = false;

    public void onCreate() {
        super.onCreate();

        // Initialize the MIDI
        midi = new MidiDriverHelper();
        midi.setOnMidiStartListener(this);

        // Initialize the asset retrieval data
        AssetManager assetManager = getAssets();
        final String soundfontFilename = "fluidr3_gm.sf2";

        // Start the midi
        midi.start(assetManager, soundfontFilename);
    }

    // Interface for the main activity
    public class DroneBinder extends Binder {
        void play(int bpm, List<Note> notes, NoteSettingsReader settings) {
            DroneService.this.play(bpm, notes, settings);
        }
        void pause() {
            DroneService.this.pause();
        }
        void stop() { DroneService.this.stop(); }
        void changeProgram(int instrument) {
            midi.changeProgram((byte) instrument);
        }
        int getKeyMin() { return midi.getKeyMin(); };
        int getKeyMax() { return midi.getKeyMax(); };
    }

    @Override
    public IBinder onBind(Intent intent) {
        return droneBinder;
    }

    public void stop() {
        // Stop the MIDI
        midi.stop();

        // Shut down the service
        DroneService.this.stopSelf();
    }

    public void play(int bpm, List<Note> notes, NoteSettingsReader settings) {
        // Reset the state, cancelling old notes
        if (isPlaying) pause();

        // Do nothing in the absence of notes to play
        if (notes.isEmpty())
            return;

        // Calculate the note and beat durations
        final double msPerBeat = MidiDriverHelper.getMsPerBeat(bpm);
        final long beatDurationMs = Math.round(msPerBeat);
        final long noteDurationMs = Math.round(msPerBeat * settings.getDuration());

        // Encode the pitches to be played in a set
        Set<Byte> keys = new HashSet<>();
        final int numPitches = notes.size();
        for (int i = 0; i < numPitches; i++) {
            final Note note = notes.get(i);
            keys.add(MidiDriverHelper.encodePitch(note.getPitch(), note.getOctave()));
        };

        // Play the sound
        final byte velocity = MidiDriverHelper.encodeVelocity(settings.getVelocity());
        midi.renderNotes(keys, velocity, noteDurationMs, beatDurationMs);
        isPlaying = true;
    }

    // Pause playing
    public void pause() {
        // Stop midi
        if (midi != null) midi.pause();

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
