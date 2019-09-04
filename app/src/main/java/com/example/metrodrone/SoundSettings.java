package com.example.metrodrone;

import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

// Class to hold all data for the sound that is generated.
public class SoundSettings {

    // Constants
    final static int pitchMax = 11;
    final static int octaveMax = 7;
    final static int bpmMax = 512;
    final static int bpmMin = 20;

    // Data
    private int bpm = 80;
    private int keyLimitLo = 0;
    private int keyLimitHi = 127;
    private double velocity = 1.0; // [0,1]
    private double duration = 0.95; // [0,1]
    private NoteSettings[] notes;
    private List<Integer> freeHandles;
    private List<Integer> occupiedHandles;

    public SoundSettings(final int maxNumNotes) {
        // Initialize the array, mark all spots as open
        notes = new NoteSettings[maxNumNotes];
        freeHandles = new LinkedList<>();
        occupiedHandles = new LinkedList<>();
        for (int i = 0; i < maxNumNotes; i++) {
            freeHandles.add(i);
        }
    }

    // Tell whether there is room for new notes
    public boolean isFull() {
        return freeHandles.isEmpty();
    }

    // Create a new note, return its handle
    public int addNote() {
        // Take a free handle
        if (freeHandles.isEmpty())
            throw BuildConfig.DEBUG ? new DebugException("No slots left for new notes!") :
                    new DefaultException();
        final int handle = freeHandles.get(0);

        // Initialize the note
        notes[handle] = new NoteSettings();

        // Mark the note as occupied
        occupiedHandles.add(handle);
        freeHandles.remove(0);
        return handle;
    }

    // Delete a note, given its handle
    public void deleteNote(final int handle) {
        occupiedHandles.remove((Integer) handle);
        freeHandles.add(handle);
    }

    // Get a note
    private NoteSettings getNote(final int handle) {
        if (occupiedHandles.indexOf(handle) < 0)
            throw BuildConfig.DEBUG ? new DebugException(String.format(
                    "No note exists at handle %d", handle)) : new DefaultException();
        return notes[handle];
    }

    // Getters
    public int getBpm() {
        return bpm;
    }

    public double getVelocity() {
        return velocity;
    }

    public double getDuration() {
        return duration;
    }

    public int getNumNotes() {
        return occupiedHandles.size();
    }

    public List<Integer> getNoteHandles() {
        return occupiedHandles;
    }

    public int getPitch(final int handle) {
        return getNote(handle).pitch;
    }

    public int getOctave(final int handle) {
        return getNote(handle).octave;
    }

    // Get the possible octave choices for a given note
    public List<Integer> getOctaveChoices(final int handle) {

        // Get the pitch of that note
        final int pitch = getPitch(handle);

        // Create the list of choices
        List<Integer> possibleOctaves = new ArrayList<>();
        for (int octave = 0; octave < octaveMax; octave++) {
            final int key = MidiDriverHelper.encodePitch(pitch, octave);
            if (key < keyLimitLo || key > keyLimitHi)
                continue;

            possibleOctaves.add(octave);
        }

        return possibleOctaves;
    }

    // Setters
    public int setBpm(final int desiredBpm) {
        bpm = Math.max(Math.min(desiredBpm, bpmMax), bpmMin);
        return bpm;
    }

    public void setVelocity(final double desiredVelocity) {
        if (desiredVelocity < 0. || desiredVelocity > 1.)
            throw BuildConfig.DEBUG ? new DebugException(String.format(
                    "Invalid velocity: %f", velocity)) : new DefaultException();
        velocity = desiredVelocity;
    }

    public void setDuration(final double desiredDuration) {
        if (desiredDuration < 0. || desiredDuration > 1.)
            throw BuildConfig.DEBUG ? new DebugException(String.format(
                    "Invalid duration: %f", duration)) : new DefaultException();
        duration = desiredDuration;
    }

    public void setPitch(final int handle, final int desiredPitch) {
        // Get the possible choices, see if there's a conflict
        //TODO

        // Set the pitch
        notes[handle].pitch = desiredPitch;

        // Update the octave, in case we are out of range
        roundOctave(handle);
    }

    public void setOctave(final int handle, final int desiredOctave) {
        // Get the key corresponding to this octave
        final int desiredKey = MidiDriverHelper.encodePitch(getPitch(handle), desiredOctave);

        // Check for conflict
        if (desiredOctave < keyLimitLo|| desiredOctave > keyLimitHi)
            throw BuildConfig.DEBUG ? new DebugException(String.format("Invalid key: %d key limits: [%d, %d]",
                    desiredKey,keyLimitLo, keyLimitHi)) : new DefaultException();

        // Update the octave
        notes[handle].octave = desiredOctave;
    }

    public void setKeyLimits(final int lo, final int hi) {
        if (lo > MidiDriverHelper.keyMax || hi < 0 || hi < lo)
            throw BuildConfig.DEBUG ?  new DebugException(String.format(
                    "Invalid key limits: [%d, %d]", lo, hi)) : new DefaultException();

        keyLimitLo = Math.max(0, lo);
        keyLimitHi = Math.min(hi, MidiDriverHelper.keyMax);

        // Assume we have at least a full octave, otherwise the pitch adapter must be changed
        final int numPitches = keyLimitHi - keyLimitLo;
        if (numPitches < pitchMax)
            throw BuildConfig.DEBUG ? new DebugException(String.format(
                    "Must have at least one full octave of pitches. Received %d", numPitches)) :
                    new DefaultException();

        // Update the pitches, in case we are now out of range
        for (int i = 0; i < occupiedHandles.size(); i++) {
            roundOctave(occupiedHandles.get(i));
        }
    }

    // Given the pitch limits, round the octave choice to the nearest possible one
    private void roundOctave(final int handle) {
        // Get the current choice--keep it if we can
        final int currentOctave = getOctave(handle);

        // Query the list of choices for our given pitch
        final List<Integer> possibleOctaves = getOctaveChoices(handle);

        // Set the octave to be the closest possible choice to our current one
        final int minPossibleOctave = possibleOctaves.get(0);
        final int maxPossibleOctave = possibleOctaves.get(possibleOctaves.size() - 1);
        final int newOctave = currentOctave < minPossibleOctave ? minPossibleOctave :
                (currentOctave > maxPossibleOctave ? maxPossibleOctave : currentOctave);

        setOctave(handle, newOctave);
    }
}
