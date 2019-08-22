package com.example.metrodrone;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.Binder;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;

import org.billthefarmer.mididriver.MidiDriver;

// Responsible for playing sound through the MIDI driver //
public class DroneService extends Service implements MidiDriver.OnMidiStartListener {

    // State
    boolean isPlaying;
    boolean soundUpdateEnabled;
    Stack<Boolean> updatesStack; // Allow storage of previous soundUpdateEnabled values

    // Sound parameters
    SoundSettings settings;

    // Create midi driver
    protected MidiDriverHelper midi;

    // Create binder to return on binding
    final IBinder droneBinder = new DroneBinder();

    public void onCreate() {
        super.onCreate();

        // Initialize the sound parameters
        isPlaying = false;
        soundUpdateEnabled = true;
        updatesStack = new Stack<>();
        settings = new SoundSettings();

        // Start with a single note
        settings.addNote();

        // Initialize the MIDI class
        midi = new MidiDriverHelper();
        midi.setOnMidiStartListener(this);

        // Initialize the asset retrieval data
        AssetManager assetManager = getAssets();
        final String soundfontFilename = "fluidr3_gm.sf2";

        // Query the device audio parameters, on supported devices
        int sampleRate = -1;
        int bufferSize = -1;
        if (android.os.Build.VERSION.SDK_INT >= 17) {

            // Query the device sample rate
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            sampleRate = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));

            // Query the device buffer size
            bufferSize = Integer.parseInt(am.getProperty(
                    AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
        } else {
            // TODO log warning
        }

        // Substitute default parameters if querying failed
        if (sampleRate < 1) {
            // TODO log warning
            sampleRate = 44100;
        }
        if (bufferSize < 1) {
            // TODO log warning
            bufferSize = 256;
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
        void changeProgram(int instrument) { DroneService.this.changeProgram(instrument); }
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
            bpm = settings.setBpm(bpm);
            updateSound();
            return bpm;
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
            updateSound();
        }
        void setOctave(int handle, int octave) {
            settings.setOctave(handle, octave);
            updateSound();
        }
        void updateSound() { DroneService.this.updateSound(); }
        void pushUpdates(boolean enable) { DroneService.this.pushUpdates(enable); }
        boolean popUpdates() { return DroneService.this.popUpdates(); }
        boolean updatesEnabled() { return soundUpdateEnabled; }
        void enableUpdates() { soundUpdateEnabled = true; }
        void disableUpdates() { soundUpdateEnabled = false; }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return droneBinder;
    }

    // Push the current updates() variable onto the stack and set the new one
    void pushUpdates(boolean enable) {
        updatesStack.push(soundUpdateEnabled);
        soundUpdateEnabled = enable;
    }

    // Pop the top of the updates stack and set its value
    boolean popUpdates() {
        soundUpdateEnabled = updatesStack.pop();
        return soundUpdateEnabled;
    }

    // Change the MIDI program, update note settings
    public void changeProgram(final int instrument) {
        midi.changeProgram((byte) instrument);
        settings.setKeyLimits(midi.getKeyMin(), midi.getKeyMax());
        updateSound();
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

    // Update the sound if we are playing. Can be disabled with the soundUpdateEnabled flag.
    public void updateSound() {
        // Update the sound if we are playing something
        if (isPlaying && soundUpdateEnabled)
            play();
    }

    // Listener for sending initial midi messages when the Sonivox
    // synthesizer has been started, such as program change.
    @Override
    public void onMidiStart()
    {
        // Nothing happens here, the program is set in play()
    }
}
