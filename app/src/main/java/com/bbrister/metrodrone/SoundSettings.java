package com.bbrister.metrodrone;

import com.bbrister.mididriver.RenderSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

// Class to hold all data for the sound that is generated.
public abstract class SoundSettings {

    // Public constants
    final static int pitchMax = 11;
    final static int octaveMax = 7;
    final static int bpmMax = 512;
    final static int bpmMin = 20;

    // Private constants
    final private static int defaultPitch = 0;
    final private static int defaultoctave = 3;
    final private static byte defaultKey = MidiDriverHelper.encodePitch(defaultPitch,
            defaultoctave);

    // Sound metadata--parameters for acceptable values of the latter
    private int keyLimitLo = 0;
    private int keyLimitHi = 127;
    private int maxReverbPreset;

    // Sound data--updates the sound when it's changed
    private UpdateValue<Boolean> boostVolume;
    private UpdateValue<Integer> bpm;
    private UpdateValue<Integer> reverbPreset;
    private UpdateValue<Double> velocity; // [0,1]
    private UpdateValue<Double> duration; // [0,1]
    private List<UpdateValue<Byte>> notes;
    private List<Integer> freeHandles;
    private List<Integer> occupiedHandles;

    // Callbacks
    private UpdateInterface updateInterface;

    // Callback function for sound updates
    abstract void onSoundChanged();

    public SoundSettings(final int maxNumNotes, final int numReverbPresets) {

        // Defaults
        final int defaultReverbPreset = 1;
        final boolean defaultBoostVolume = true;
        final int defaultBpm = 80;
        final double defaultVelocity = 1.0;
        final double defaultDuration = 0.95;

        // Set up the callback interface
        updateInterface = new UpdateInterface() {
            @Override
            public void update() {
                onSoundChanged();
            }
        };
        reverbPreset = new UpdateValue<>(defaultReverbPreset, updateInterface);

        // Initialize the data to defaults
        boostVolume = new UpdateValue<>(defaultBoostVolume, updateInterface);
        bpm = new UpdateValue<>(defaultBpm, updateInterface);
        velocity = new UpdateValue<>(defaultVelocity, updateInterface);
        duration = new UpdateValue<>(defaultDuration, updateInterface);

        // Initialize the reverb limits, possibly overriding defaults
        maxReverbPreset = numReverbPresets - 1;
        reverbPreset.set(Math.min(reverbPreset.get(), maxReverbPreset));

        // Initialize the array, mark all spots as open
        notes = new ArrayList<>(maxNumNotes);
        freeHandles = new LinkedList<>();
        occupiedHandles = new LinkedList<>();
        for (int i = 0; i < maxNumNotes; i++) {
            // Mark the handle as free
            freeHandles.add(i);

            // Add a dummy note--this will be overwritten later
            notes.add(new UpdateValue<>((byte) 0, updateInterface));
        }
    }

    // Tell whether there is room for new notes
    public boolean isFull() {
        return freeHandles.isEmpty();
    }

    // Create a new note, returning its handle
    public int addNote() {
        // Take a free handle
        if (freeHandles.isEmpty())
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException("No slots left for new notes!") :
                    new DefaultException();
        final int handle = freeHandles.get(0);

        // Mark the note as occupied
        occupiedHandles.add(handle);
        freeHandles.remove(0);

        // Update the note pitch, which also updates the sound
        setKeyRounded(handle, defaultKey);

        return handle;
    }

    // Delete a note, given its handle
    public void deleteNote(final int handle) {
        // Mark the note as free
        occupiedHandles.remove((Integer) handle);
        freeHandles.add(handle);

        // Update the sound
        updateInterface.update();
    }

    // Get a note
    private UpdateValue<Byte> getNote(final int handle) {
        if (occupiedHandles.indexOf(handle) < 0) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "No note exists at handle %d", handle)) : new DefaultException();
        }
        return notes.get(handle);
    }

    // Getters
    public int getBpm() {
        return bpm.get();
    }

    public double getVelocity() {
        return velocity.get();
    }

    public double getDuration() {
        return duration.get();
    }

    public int getNumNotes() {
        return occupiedHandles.size();
    }

    public List<Integer> getNoteHandles() {
        return occupiedHandles;
    }

    public byte getKey(final int handle) {
        return getNote(handle).get();
    }

    public int getPitch(final int handle) {
        return MidiDriverHelper.decodePitchClass(getKey(handle));
    }

    public int getOctave(final int handle) {
        return MidiDriverHelper.decodeOctave(getKey(handle));
    }

    public boolean getVolumeBoost() { return boostVolume.get(); }

    public int getReverbPreset() { return reverbPreset.get(); }

    // Get the possible octave choices for a given key
    public List<Integer> getOctaveChoices(final int pitchClass) {

        // Create the list of choices
        List<Integer> possibleOctaves = new ArrayList<>();
        for (int octave = 0; octave < octaveMax; octave++) {
            final int key = MidiDriverHelper.encodePitch(pitchClass, octave);
            if (key < keyLimitLo || key > keyLimitHi)
                continue;

            possibleOctaves.add(octave);
        }

        return possibleOctaves;
    }

    // Condense the settings into a class for rendering
    public RenderSettings getRenderSettings() {

        // Calculate the note and beat durations
        final double msPerBeat = MidiDriverHelper.getMsPerBeat(bpm.get());
        final long beatDurationMs = Math.round(msPerBeat);
        final long noteDurationMs = Math.round(msPerBeat * duration.get());

        // Put the pitches in a set, to remove duplicates
        Set<Byte> keys = new HashSet<>();
        for (int i = 0; i < getNumNotes(); i++) {
            final int handle = occupiedHandles.get(i);
            keys.add(getKey(handle));
        }

        // Convert the set to an array, for easy access in C
        byte[] pitchArray = new byte[keys.size()];
        Iterator<Byte> it = keys.iterator();
        for (int i = 0; it.hasNext(); i++) {
            pitchArray[i] = it.next();
        }

        // Create the settings object
        RenderSettings settings = new RenderSettings();
        settings.pitchArray = pitchArray;
        settings.velocity = MidiDriverHelper.encodeVelocity(velocity.get());
        settings.noteDurationMs = noteDurationMs;
        settings.recordDurationMs = beatDurationMs;
        settings.reverbPreset = reverbPreset.get();
        settings.volumeBoost = boostVolume.get();

        return settings;
    }

    // Setters
    public int setBpm(final int desiredBpm) {
        bpm.set(Math.max(Math.min(desiredBpm, bpmMax), bpmMin));
        return bpm.get();
    }

    public void setVelocity(final double desiredVelocity) {
        if (desiredVelocity < 0. || desiredVelocity > 1.)
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Invalid velocity: %f", velocity.get())) : new DefaultException();
        velocity.set(desiredVelocity);
    }

    public void setDuration(final double desiredDuration) {
        if (desiredDuration < 0. || desiredDuration > 1.)
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Invalid duration: %f", duration.get())) : new DefaultException();
        duration.set(desiredDuration);
    }

    // Set the key at the given handle
    private void setKey(final int handle, final byte key) {
        getNote(handle).set(key);
    }

    // Like setKey, but pass the key through roundOctave using the current limits
    private void setKeyRounded(final int handle, final byte desiredKey) {
        setKey(handle, roundOctave(desiredKey));
    }

    public void setPitch(final int handle, final int desiredPitch) {
        // Get the possible pitch classes, see if there's a conflict
        //TODO

        // Update the octave, in case we are out of range
        final byte desiredKey = MidiDriverHelper.encodePitch(desiredPitch, getOctave(handle));
        setKeyRounded(handle, desiredKey);
    }

    public void setOctave(final int handle, final int desiredOctave) {
        // Get the key corresponding to this octave
        final byte desiredKey = MidiDriverHelper.encodePitch(getPitch(handle), desiredOctave);

        // Check for conflict
        if (desiredKey < keyLimitLo || desiredKey > keyLimitHi)
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Invalid key: %d key limits: [%d, %d]", desiredKey, keyLimitLo, keyLimitHi)) :
                    new DefaultException();

        // Update the key
        setKey(handle, desiredKey);
    }

    public void setKeyLimits(final int lo, final int hi) {
        if (lo > MidiDriverHelper.keyMax || hi < 0 || hi < lo)
            throw BuildConfig.DEBUG_EXCEPTIONS ?  new DebugException(String.format(
                    "Invalid key limits: [%d, %d]", lo, hi)) : new DefaultException();

        keyLimitLo = Math.max(0, lo);
        keyLimitHi = Math.min(hi, MidiDriverHelper.keyMax);

        // Assume we have at least a full octave, otherwise the pitch adapter must be changed
        final int numPitches = keyLimitHi - keyLimitLo;
        if (numPitches < pitchMax)
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Must have at least one full octave of pitches. Received %d", numPitches)) :
                    new DefaultException();

        // Update the octaves, in case we are now out of range
        for (Integer handle : occupiedHandles) {
            setKeyRounded(handle, getKey(handle));
        }
    }

    // Choose whether or not to boost the volume with DNR compression
    public void setVolumeBoost(final boolean boostVolume) {
        this.boostVolume.set(boostVolume);
    }

    // Choose the reverb preset
    public void setReverbPreset(final int preset) {
        if (preset < 0 || preset > maxReverbPreset) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Invalid reverb preset: %d (max: %d)", preset, maxReverbPreset)) :
                    new DefaultException();
        }

        reverbPreset.set(preset);
    }

    // Given the pitch limits, round the octave choice to the nearest possible one. Input and output
    // are keys (0-127).
    private byte roundOctave(byte key) {

        // Convert the key to pitch class & octave
        final int pitch = MidiDriverHelper.decodePitchClass(key);
        final int currentOctave = MidiDriverHelper.decodeOctave(key);

        // Query the list of choices for our given pitch
        final List<Integer> possibleOctaves = getOctaveChoices(pitch);

        // Set the octave to be the closest possible choice to our current one
        final int minPossibleOctave = possibleOctaves.get(0);
        final int maxPossibleOctave = possibleOctaves.get(possibleOctaves.size() - 1);
        final int newOctave = currentOctave < minPossibleOctave ? minPossibleOctave :
                (currentOctave > maxPossibleOctave ? maxPossibleOctave : currentOctave);

        return MidiDriverHelper.encodePitch(pitch, newOctave);
    }
}
