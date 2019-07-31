package com.example.metrodronemidi;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.Binder;

import java.util.Timer;
import java.util.TimerTask;

import org.billthefarmer.mididriver.MidiDriver;

// Responsible for playing sound through the MIDI driver //
public class DroneService extends Service implements MidiDriver.OnMidiStartListener {

    // Create midi driver
    protected MidiDriverHelper midi;
    protected MediaPlayer player; // TODO do we need this?

    private Timer noteTimer;

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
        void play(double bpm, int pitch, int octave, int instrument, NoteSettingsReader settings) {
            DroneService.this.play(bpm, pitch, octave, instrument, settings);
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


    public void play(double bpm, int pitch, int octave, int instrument,
                     NoteSettingsReader settings) {
        // Reset the state, cancelling old notes
        if (isPlaying) pause();

        // Start the midi
        midi.start();
        //TODO: Do we need to start the player? The example app didn't have it

        // Set the instrument
        setInstrument(instrument);

        // Start a timer to play the notes
        final long msPerMinute = 60 * 1000;
        final double msPerBeat = msPerMinute / bpm;
        noteTimer = new Timer();
        TimerTask playNote = new PlayNoteTask(midi, pitch, octave, settings);
        noteTimer.schedule(playNote, 0, (long) msPerBeat);
        isPlaying = true;
    }

    // Pause playing
    public void pause() {
        // Stop midi
        if (midi != null) midi.stop();

        // Stop player
        if (player != null) player.stop();

        // Stop the timer, cancelling old notes
        if (noteTimer != null) noteTimer.cancel();

        isPlaying = false;
    }

    // Listener for sending initial midi messages when the Sonivox
    // synthesizer has been started, such as program change.
    @Override
    public void onMidiStart()
    {
        // Nothing happens here, the program is set in play()
    }

    protected void setInstrument(int code) {
        final int setProgram = 0xc0;
        midi.send(setProgram, code);
    }
}
