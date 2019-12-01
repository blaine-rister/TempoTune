package com.bbrister.tempodrone;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Binder;

import java.util.List;

// Responsible for playing sound through the MIDI driver //
public class DroneService extends Service {

    // Constants
    final static int programMax = 127;

    // State
    boolean isPlaying;

    // Sound parameters
    SoundSettings settings;
    String soundfontName;

    // Create midi driver
    protected MidiDriverHelper midi;

    // Create binder to return on binding
    final IBinder droneBinder = new DroneBinder();

    @Override
    public void onCreate() {
        super.onCreate();

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
        void loadSounds(final String filename) {
            DroneService.this.loadSounds(filename);
        }
        boolean haveSoundfont() {return !soundfontName.isEmpty(); }
        String getSoundfont() {
            return soundfontName;
        }
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
        List<Integer> getPitchChoices() { return settings.getPitchChoices(); }
        List<Integer> getOctaveChoices(int handle) { return settings.getOctaveChoices(handle); }
        int getNumReverbPresets() { return midi.getNumReverbPresets(); }
        int getReverbPreset() { return settings.getReverbPreset(); }
        boolean getVolumeBoost() { return settings.getVolumeBoost(); }
        int setBpm(int bpm) {
            return settings.setBpm(bpm);
        }
        void setVelocity(double velocity) { settings.setVelocity(velocity); }
        void setDuration(double duration) { settings.setDuration(duration); }
        void setReverbPreset(int preset) { settings.setReverbPreset(preset); }
        void setPitch(int handle, int pitch) {
            settings.setPitch(handle, pitch);
        }
        void setOctave(int handle, int octave) {
            settings.setOctave(handle, octave);
        }
        void setVolumeBoost(boolean boostVolume) { settings.setVolumeBoost(boostVolume); }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return droneBinder;
    }

    // Load the sounds and remember the soundfont name
    private void loadSounds(final String soundfontName) {
        this.soundfontName = soundfontName;
        midi.loadSounds(getApplicationContext(), soundfontName);
    }

    // Change the MIDI program
    public void changeProgram(final int instrument) {
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

    public void stop() {
        // Stop the MIDI
        pause();
        midi.stop();

        // Shut down the service
        stopSelf();
    }

    // Start playing the sound, according to the current settings
    public void play() {

        // Reset the state, cancelling existing playback
        pause();

        // Do nothing in the absence of notes to play
        final int numNotes = settings.getNumNotes();
        if (numNotes < 1)
            return;

        // Render the sound
        float[] sound = midi.renderNotes(settings.getRenderSettings());

        // Save the sound to the singleton class
        AudioData.setData(sound);

        // Launch a new playback service
        startService(PlaybackService.getStartIntent(this));
        isPlaying = true;
    }

    // Pause playing
    public void pause() {
        // Stop playing
        stopService(new Intent(this, PlaybackService.class));
        isPlaying = false;
    }

    // Update the sound if we are playing
    public void updateSound() {
        if (isPlaying)
            play();
    }
}
