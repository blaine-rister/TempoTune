package com.example.metrodrone;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;

import java.util.Set;
import java.util.HashSet;
import java.util.List;

// Responsible for playing sound through the MIDI driver //
public class DroneService extends Service {

    // Constants
    final static int programMax = 127;

    // State
    boolean isPlaying;

    // Sound parameters
    SoundSettings settings;

    // Create midi driver
    protected MidiDriverHelper midi;

    // Create binder to return on binding
    final IBinder droneBinder = new DroneBinder();

    public void onCreate() {
        super.onCreate();

        // Initialize the sound driver
        midi = new MidiDriverHelper();

        // Initialize the sound parameters
        isPlaying = false;
        settings = new SoundSettings(midi.getMaxVoices());

        // Start with a single note
        settings.addNote();

        // Initialize the asset retrieval data
        AssetManager assetManager = getAssets();
        final boolean testMode = true;
        final String soundfontFilename = testMode ? "fluidr3_gm_with_holes_small.sf2" :
                "fluidr3_gm_with_holes.sf2";

        // Query the device audio parameters, on supported devices
        int sampleRate = -1;
        int bufferSize = -1;
        final int audioParamsSdkVersion = 17;
        if (android.os.Build.VERSION.SDK_INT >= audioParamsSdkVersion) {

            // Query the device sample rate
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            sampleRate = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));

            // Query the device buffer size
            bufferSize = Integer.parseInt(am.getProperty(
                    AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
        } else {
            Log.w(MainActivity.logTag, String.format("Cannot query device audio parameters since " +
                    "current SDK version %d < %d", Build.VERSION.SDK_INT,
                    audioParamsSdkVersion));
        }

        // Substitute default parameters if querying failed
        if (sampleRate < 1) {
            sampleRate = 44100;
            Log.w(MainActivity.logTag, String.format("Failed to query the device sample rate. " +
                    "Defaulting to %d", sampleRate));
        }
        if (bufferSize < 1) {
            bufferSize = 256;
            Log.w(MainActivity.logTag, String.format("Failed to query the device buffer size. " +
                    "Defaulting to %d", bufferSize));
        }

        // Start the midi
        midi.start(assetManager, soundfontFilename, sampleRate, bufferSize);
    }

    // Interface for the main activity
    public class DroneBinder extends Binder {
        void play() { DroneService.this.play(); }
        void pause() {
            DroneService.this.pause();
        }
        void stop() { DroneService.this.stop(); }
        boolean queryProgram(int instrument) { return midi.queryProgram((byte) instrument); }
        String getProgramName(int instrument) { return midi.getProgramName((byte) instrument); }
        void changeProgram(int instrument) { DroneService.this.changeProgram(instrument); }
        int getProgram() { return midi.getProgram(); }
        int addNote() { return settings.addNote(); }
        void deleteNote(int handle) { settings.deleteNote(handle); }
        boolean notesFull() { return settings.isFull(); }
        int getBpm() { return settings.getBpm(); }
        double getVelocity() { return settings.getVelocity(); }
        double getDuration() { return settings.getDuration(); }
        int getNumNotes() { return settings.getNumNotes(); }
        List<Integer> getNoteHandles() { return settings.getNoteHandles(); }
        int getPitch(int handle) { return settings.getPitch(handle); }
        int getOctave(int handle) { return settings.getOctave(handle); }
        List<Integer> getOctaveChoices(int handle) { return settings.getOctaveChoices(handle); }
        int setBpm(int bpm) {
            return settings.setBpm(bpm);
        }
        void setVelocity(double velocity) {
            settings.setVelocity(velocity);
            updateSound();
        }
        void setDuration(double duration) {
            settings.setDuration(duration);
            updateSound();
        }
        void setPitch(int handle, int pitch) {
            settings.setPitch(handle, pitch);
        }
        void setOctave(int handle, int octave) {
            settings.setOctave(handle, octave);
        }
        void updateSound() { DroneService.this.updateSound(); }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return droneBinder;
    }

    // Change the MIDI program
    public void changeProgram(final int instrument) {
        midi.changeProgram((byte) instrument);
        settings.setKeyLimits(midi.getKeyMin(), midi.getKeyMax());
    }

    public void stop() {
        // Stop the MIDI
        pause();
        midi.stop();

        // Shut down the service
        DroneService.this.stopSelf();
    }

    // Start playing the sound, according to the current settings
    public void play() {

        // Reset the state, cancelling old notes
        pause();

        // Do nothing in the absence of notes to play
        final int numNotes = settings.getNumNotes();
        if (numNotes < 1)
            return;

        // Calculate the note and beat durations
        final double msPerBeat = MidiDriverHelper.getMsPerBeat(settings.getBpm());
        final long beatDurationMs = Math.round(msPerBeat);
        final long noteDurationMs = Math.round(msPerBeat * settings.getDuration());

        // Encode the pitches to be played in a set
        final List<Integer> handles = settings.getNoteHandles();
        Set<Byte> keys = new HashSet<>();
        for (int i = 0; i < numNotes; i++) {
            final int handle = handles.get(i);
            keys.add(MidiDriverHelper.encodePitch(settings.getPitch(handle),
                    settings.getOctave(handle)));
        };

        // Play the sound
        final byte velocity = MidiDriverHelper.encodeVelocity(settings.getVelocity());
        midi.renderNotes(keys, velocity, noteDurationMs, beatDurationMs);
        isPlaying = true;
    }

    // Pause playing
    public void pause() {
        // Stop midi
        midi.pause();
        isPlaying = false;
    }

    // Update the sound if we are playing
    public void updateSound() {
        if (isPlaying)
            play();
    }
}
