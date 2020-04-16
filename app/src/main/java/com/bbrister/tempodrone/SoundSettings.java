package com.bbrister.tempodrone;

import android.content.Context;

import com.bbrister.mididriver.RenderSettings;
import com.bbrister.tempodrone.preferences.BooleanPreference;
import com.bbrister.tempodrone.preferences.BytePreference;
import com.bbrister.tempodrone.preferences.FloatPreference;
import com.bbrister.tempodrone.preferences.IntegerPreference;
import com.bbrister.tempodrone.preferences.UpdateInterface;

import java.util.ArrayList;
import java.util.Arrays;
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

    // Starting defaults
    final private static int defaultStartPitch = 0;
    final private static int defaultStartOctave = 3;
    final private static byte noteEmptyKey = -1;

    // Names of the parameters in the preference files
    final private static String boostVolumeKey = "boostVolume";
    final private static String bpmKey = "bpm";
    final private static String reverbKey = "reverb";
    final private static String velocityKey = "velocity";
    final private static String durationKey = "duration";

    // Key default. This is changed throughout the program
    private byte defaultKey;

    // Sound metadata--parameters for acceptable values of the latter
    private boolean[] keyRange;
    private int maxReverbPreset;

    // Sound data--updates the sound when it's changed
    private BooleanPreference boostVolume;
    private IntegerPreference bpm;
    private IntegerPreference reverbPreset;
    private FloatPreference velocity; // [0,1]
    private FloatPreference duration; // [0,1]
    private List<BytePreference> notes;
    private List<Integer> freeHandles;
    private List<Integer> occupiedHandles;

    // Callbacks
    private UpdateInterface updateInterface;

    // Callback function for sound updates
    abstract void onSoundChanged();

    public SoundSettings(Context context, final int maxNumNotes, final int numReverbPresets) {

        // Defaults
        defaultKey = MidiDriverHelper.encodePitch(defaultStartPitch, defaultStartOctave);
        final int defaultReverbPreset = 1;
        final boolean defaultBoostVolume = false;
        final int defaultBpm = 80;
        final float defaultVelocity = 1.0f;
        final float defaultDuration = 0.95f;

        // Initialize the key range to dummy values
        keyRange = new boolean[MidiDriverHelper.keyMax + 1];
        Arrays.fill(keyRange, true);

        // Set up the callback interface
        updateInterface = new UpdateInterface() {
            @Override
            public void update() {
                onSoundChanged();
            }
        };

        // Initialize the updatable parameters with storage backed to shared preferences
        boostVolume = new BooleanPreference(context, boostVolumeKey, defaultBoostVolume)
                .setUpdate(updateInterface);
        bpm = new IntegerPreference(context, bpmKey, defaultBpm).setUpdate(updateInterface);
        velocity = new FloatPreference(context, velocityKey, defaultVelocity)
                .setUpdate(updateInterface);
        duration = new FloatPreference(context, durationKey, defaultDuration)
                .setUpdate(updateInterface);
        reverbPreset = new IntegerPreference(context, reverbKey, defaultReverbPreset)
                .setUpdate(updateInterface);

        // Apply the reverb limits, possibly overriding defaults
        maxReverbPreset = numReverbPresets - 1;
        reverbPreset.write(Math.min(reverbPreset.read(), maxReverbPreset));

        // Initialize the notes array, mark all spots as open
        notes = new ArrayList<>(maxNumNotes);
        freeHandles = new LinkedList<>();
        occupiedHandles = new LinkedList<>();
        for (int handle = 0; handle < maxNumNotes; handle++) {

            // Initialize the preferences object for this note, using a negative default value
            final String lookupKey = String.format("note_%d", handle);
            BytePreference note = new BytePreference(context, lookupKey, noteEmptyKey)
                    .setUpdate(updateInterface);

            // Add the note to the 'notes' array
            notes.add(note);

            // Mark the note as occupied or free depending on whether the note has been stored
            // before.
            final int previousKey = note.read();
            if (previousKey >= 0) {
                occupiedHandles.add(handle);
                roundKey(handle, (byte) previousKey);
            } else {
                freeHandles.add(handle);
            }
        }

        // Add a single note if there are none
        if (occupiedHandles.isEmpty()) {
            addNote();
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
        roundKey(handle, defaultKey);

        return handle;
    }

    // Delete a note, given its handle
    public void deleteNote(final int handle) {
        // Mark the note as free
        occupiedHandles.remove(Integer.valueOf(handle));
        freeHandles.add(handle);

        // Update the underlying preference object (updates the sound)
        notes.get(handle).write(noteEmptyKey);
    }

    // Get a note
    private BytePreference getNote(final int handle) {
        if (occupiedHandles.indexOf(handle) < 0) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "No note exists at handle %d", handle)) : new DefaultException();
        }
        return notes.get(handle);
    }

    // Getters
    public int getBpm() {
        return bpm.read();
    }

    public double getVelocity() {
        return velocity.read();
    }

    public double getDuration() {
        return duration.read();
    }

    public int getNumNotes() {
        return occupiedHandles.size();
    }

    public List<Integer> getNoteHandles() {
        return occupiedHandles;
    }

    public byte getKey(final int handle) {
        return getNote(handle).read();
    }

    public int getPitch(final int handle) {
        return MidiDriverHelper.decodePitchClass(getKey(handle));
    }

    public int getOctave(final int handle) {
        return MidiDriverHelper.decodeOctave(getKey(handle));
    }

    public boolean getVolumeBoost() { return boostVolume.read(); }

    public int getReverbPreset() { return reverbPreset.read(); }

    // Check if this key is available
    public boolean haveKey(final byte key) {
        return keyRange[key];
    }

    // Check if this pitch is available at any octave
    private boolean havePitch(final int pitch) {
        return !getOctaveChoices(pitch).isEmpty();
    }

    // Get the possible pitch choices, at any octave
    public List<Integer> getPitchChoices() {
        // Iterate through all pitches
        List<Integer> possiblePitches = new ArrayList<>();
        for (int pitch = 0; pitch <= pitchMax; pitch++) {
            // Check if this pitch has any possible octaves
            if (!havePitch(pitch))
                continue;

            possiblePitches.add(pitch);
        }

        return possiblePitches;
    }

    // Get the possible octave choices for a given key
    public List<Integer> getOctaveChoices(final int pitchClass) {

        // Create the list of choices
        List<Integer> possibleOctaves = new ArrayList<>();
        for (int octave = 0; octave <= octaveMax; octave++) {
            final int key = MidiDriverHelper.encodePitch(pitchClass, octave);
            if (!keyRange[key])
                continue;

            possibleOctaves.add(octave);
        }

        return possibleOctaves;
    }

    // Condense the settings into a class for rendering
    public RenderSettings getRenderSettings() {

        // Calculate the note and beat durations
        final double msPerBeat = MidiDriverHelper.getMsPerBeat(bpm.read());
        final long beatDurationMs = Math.round(msPerBeat);
        final long noteDurationMs = Math.round(msPerBeat * duration.read());

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
        settings.velocity = MidiDriverHelper.encodeVelocity(velocity.read());
        settings.noteDurationMs = noteDurationMs;
        settings.recordDurationMs = beatDurationMs;
        settings.reverbPreset = reverbPreset.read();
        settings.volumeBoost = boostVolume.read();

        return settings;
    }

    // Setters
    public int setBpm(final int desiredBpm) {
        bpm.write(Math.max(Math.min(desiredBpm, bpmMax), bpmMin));
        return bpm.read();
    }

    public void setVelocity(final double desiredVelocity) {
        if (desiredVelocity < 0. || desiredVelocity > 1.)
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Invalid velocity: %f", velocity.read())) : new DefaultException();
        velocity.write((float) desiredVelocity);
    }

    public void setDuration(final double desiredDuration) {
        if (desiredDuration < 0. || desiredDuration > 1.)
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Invalid duration: %f", duration.read())) : new DefaultException();
        duration.write((float) desiredDuration);
    }

    /**
     * Set the key at the given handle. Updates the default key.
     */
    private void setKey(final int handle, final byte key) {
        getNote(handle).write(key);
        defaultKey = key;
    }

    /**
     * Select the nearest key to the current.
     */
    public void roundKey(final int handle, final byte desiredKey) {

        // Search forward
        byte forwardMatch = -1;
        for (int key = desiredKey; key <= MidiDriverHelper.keyMax; key++) {

            final byte keyByte = (byte) key; // Do this to avoid overflow in the loop counter!

            if (haveKey(keyByte)) {
                forwardMatch = keyByte;
                break;
            }
        }

        // Search backward at least this many steps
        final boolean haveForward = forwardMatch >= 0;
        final int backwardLimit = haveForward ? Math.max(0, desiredKey - forwardMatch + 1) : 0;
        byte backwardMatch = -1;
        for (byte key = desiredKey; key >= backwardLimit; key--) {
            if (haveKey(key)) {
                backwardMatch = key;
                break;
            }
        }

        // Ensure there is some match
        final boolean haveBackward = backwardMatch >= 0;
        if (!haveForward && !haveBackward) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(
                    "Failed to find any valid key!") : new DefaultException();
        }

        // Pick the closer match. By the limited search, this is the backward one
        setKey(handle, haveBackward ? backwardMatch : forwardMatch);
    }

    /**
     * Set the pitch. Throws an exception if it's not available.
     */
    public void setPitch(final int handle, final int desiredPitch) {
        // Check if the pitch is supported
        if (!havePitch(desiredPitch)) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Missing pitch: %d", desiredPitch)) : new DefaultException();
        }

        // Update the octave, in case we are out of range
        final byte desiredKey = MidiDriverHelper.encodePitch(desiredPitch, getOctave(handle));
        setKey(handle, roundOctave(desiredKey));
    }

    public void setOctave(final int handle, final int desiredOctave) {
        // Get the key corresponding to this octave
        final byte desiredKey = MidiDriverHelper.encodePitch(getPitch(handle), desiredOctave);

        // Check for conflict
        if (!keyRange[desiredKey])
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Missing key: %d]", desiredKey)) :
                    new DefaultException();

        // Update the key
        setKey(handle, desiredKey);
    }

    public void setKeyRange(final boolean[] keyRange) {
        if (keyRange.length > MidiDriverHelper.keyMax + 1)
            throw BuildConfig.DEBUG_EXCEPTIONS ?  new DebugException(String.format(
                    "Invalid key range: size %d", keyRange.length)) : new DefaultException();

        this.keyRange = keyRange;

        // Round the pitch
        for (Integer handle : occupiedHandles) {
            roundKey(handle, getKey(handle));
        }
    }

    // Choose whether or not to boost the volume with DNR compression
    public void setVolumeBoost(final boolean boostVolume) {
        this.boostVolume.write(boostVolume);
    }

    // Choose the reverb preset
    public void setReverbPreset(final int preset) {
        if (preset < 0 || preset > maxReverbPreset) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Invalid reverb preset: %d (max: %d)", preset, maxReverbPreset)) :
                    new DefaultException();
        }

        reverbPreset.write(preset);
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
