package com.bbrister.tempodrone;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;

// Responsible for playing sound through the MIDI driver //
public class DroneService extends Service {

    // Callback interface
    public interface UpdateListener {
        void onSoundChanged();
    }
    Map<Integer, UpdateListener> updateListeners; // Store the registered listeners
    int nextListenerHandle; // Return a handle to deactivate them

    // Constants
    final public static int programMax = 127;

    // State
    private boolean isPlaying;

    // Sound parameters
    private SoundSettings settings;
    private String soundfontName;

    // Create midi driver
    private MidiDriverHelper midi;

    // Create binder to return on binding
    private final IBinder droneBinder = new DroneBinder();

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize the listeners
        nextListenerHandle = 0;
        updateListeners = new LinkedHashMap<>();

        // Initialize the sound driver
        midi = new MidiDriverHelper();

        // Initialize the sound parameters
        isPlaying = false;
        settings = new SoundSettings(midi.getMaxVoices(), midi.getNumReverbPresets()) {
            @Override
            public void onSoundChanged() {
                updateSound();
            }
        };

        // Start with a single note
        settings.addNote();

        // Start the midi
        midi.start(this.getApplicationContext());

        // Initialize the soundfont to empty
        soundfontName = "";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stop();
    }

    // Interface for drone activities
    public class DroneBinder extends Binder {
        boolean isPlaying() { return isPlaying; }
        synchronized void loadSounds(final String filename) {
            DroneService.this.loadSounds(filename);
        }
        boolean haveSoundfont() {return !soundfontName.isEmpty(); }
        String getSoundfont() {
            return soundfontName;
        }
        synchronized void playPause() { DroneService.this.playPause(); }
        synchronized void stop() { DroneService.this.stop(); }
        boolean queryProgram(int instrument) { return midi.queryProgram((byte) instrument); }
        String getProgramName(int instrument) { return midi.getProgramName((byte) instrument); }
        synchronized void changeProgram(int instrument) { DroneService.this.changeProgram(instrument); }
        int getProgram() { return midi.getProgram(); }
        synchronized int addNote() { return settings.addNote(); }
        synchronized void deleteNote(int handle) { settings.deleteNote(handle); }
        boolean notesFull() { return settings.isFull(); }
        int getBpm() { return settings.getBpm(); }
        double getVelocity() { return settings.getVelocity(); }
        double getDuration() { return settings.getDuration(); }
        int getNumNotes() { return settings.getNumNotes(); }
        List<Integer> getNoteHandles() { return settings.getNoteHandles(); }
        int getPitch(int handle) { return settings.getPitch(handle); }
        int getOctave(int handle) { return settings.getOctave(handle); }
        List<Integer> getPitchChoices() { return settings.getPitchChoices(); }
        List<Integer> getOctaveChoices(int handle) { return settings.getOctaveChoices(handle); }
        int getNumReverbPresets() { return midi.getNumReverbPresets(); }
        int getReverbPreset() { return settings.getReverbPreset(); }
        boolean getVolumeBoost() { return settings.getVolumeBoost(); }
        int registerListener(UpdateListener listener) {
            return DroneService.this.registerListener(listener);
        }
        void unregisterListener(int handle) { DroneService.this.unregisterListener(handle); }
        synchronized int setBpm(int bpm) {
            return settings.setBpm(bpm);
        }
        synchronized void setVelocity(double velocity) { settings.setVelocity(velocity); }
        synchronized void setDuration(double duration) { settings.setDuration(duration); }
        synchronized void setReverbPreset(int preset) { settings.setReverbPreset(preset); }
        synchronized void setPitch(int handle, int pitch) {
            settings.setPitch(handle, pitch);
        }
        synchronized void setOctave(int handle, int octave) {
            settings.setOctave(handle, octave);
        }
        synchronized void setVolumeBoost(boolean boostVolume) { settings.setVolumeBoost(boostVolume); }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return droneBinder;
    }

    // Register an update listener. Return the handle.
    private int registerListener(final UpdateListener listener) {
        final int handle = nextListenerHandle++;
        updateListeners.put(handle, listener);
        return handle;
    }

    // Un-register an update listener, given the handle.
    private void unregisterListener(final int handle) {
        updateListeners.remove(handle);
    }

    // Call all the callbacks
    private void callListeners() {
        for (UpdateListener listener : updateListeners.values()) {
            listener.onSoundChanged();
        }
    }

    // Load the sounds and remember the soundfont name
    private void loadSounds(final String soundfontName) {
        this.soundfontName = soundfontName;
        midi.loadSounds(getApplicationContext(), soundfontName);
    }

    // Change the MIDI program
    private void changeProgram(final int instrument) {
        // Get the currently running program
        final int currentProgram = midi.getProgram();

        // Change the program and set the key range
        midi.changeProgram((byte) instrument);
        settings.setKeyRange(midi.getKeyRange());

        // Update the sound only if this is a new program
        if (instrument != currentProgram) {
            updateSound();
        }
    }

    private void stop() {
        // Stop the MIDI
        pause();
        midi.stop();

        // Shut down the service
        stopSelf();
    }

    // Toggle play/pause state
    private void playPause() {

        // Toggle the play state
        if (isPlaying) {
            pause();
        } else {
            play();
        }

        // Update the play/pause UI
        callListeners();
    }

    // Start playing the sound, according to the current settings
    private void play() {

        // Reset the state, cancelling existing playback
        pause();

        // Do nothing in the absence of notes to play
        final int numNotes = settings.getNumNotes();
        if (numNotes < 1)
            return;

        // Render the sound
        float[] sound = midi.renderNotes(settings.getRenderSettings());

        // Save the sound to the singleton class
        AudioData.pushData(sound);

        // Launch a new playback service
        startService(PlaybackService.getStartIntent(this));
        isPlaying = true;
    }

    // Pause playing
    private void pause() {
        // Stop playing
        stopService(new Intent(this, PlaybackService.class));
        isPlaying = false;
    }

    // Update the sound if we are playing. Update the UI to reflect new parameters.
    private void updateSound() {
        // Re-play the sound
        if (isPlaying) {
            play();
        }

        // Update the UI
        callListeners();
    }
}
