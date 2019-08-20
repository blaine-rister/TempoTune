package com.example.metrodrone;

import android.content.Context;

import android.view.View;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

public abstract class Note {

    // Constants
    public final int pitchMax = 11;
    public final int octaveMax = 7;
    public final int keyMax = 127;

    // Defaults
    public final int defaultPitch = 0; // A
    public final int defaultOctave = 3; // 0-7

    // Pitch data
    private int pitch; // 0-11
    private int octave; // 0-7

    // Limits on the displayable pitches from the underlying instrument
    private int limitLo = 0;
    private int limitHi = 127;

    // UI items
    public Spinner pitchSpinner;
    public Spinner octaveSpinner;

    // Allow the parent to perform actions when the pitch is updated, e.g. changing the drone
    abstract void onPitchChanged();

    // Read-only access to data
    public int getPitch() {
        return pitch;
    }

    public int getOctave() {
        return octave;
    }

    // Write to the data, updating the UI
    //TODO do we need this? I bet we can just set the pitch limits as part of this class
    public void setData(int pitch, int octave) {
        // Verify inputs
        if (pitch < 0 || pitch > pitchMax)
            throw new RuntimeException("Invalid pitch");
        if (octave < 0 || octave > octaveMax)
            throw new RuntimeException("Invalid octave");

        // Set the data
        this.pitch = pitch;
        pitchSpinner.setSelection(pitch);
        this.octave = octave;
        octaveSpinner.setSelection(octave);
        onPitchChanged();
    }

    // Set limits on the pitches which can be displayed. The inputs are in units of absolute MIDI
    // key numbers, meaningful in the range [0-127].
    public void setLimits(int lo, int hi) {
        if (lo > keyMax || hi < 0 || hi < lo)
            throw new RuntimeException(String.format("Invalid pitch limits: [%d, %d]", lo, hi));

        limitLo = Math.max(0, lo);
        limitHi = Math.min(hi, keyMax);

        // Assume we have at least a full octave, otherwise the pitch adapter must be changed
        final int numPitches = limitHi - limitLo;
        if (numPitches < pitchMax)
            throw new RuntimeException(String.format("Must have at least one full octave of " +
                            "pitches. Received %d", numPitches));

        setOctaveChoices();
    }

    // Get the possible octave choices
    private List<Integer> getOctaveChoices() {

        List<Integer> possibleOctaves = new ArrayList<>();
        for (int octave = 0; octave < octaveMax; octave++) {
            final int key = MidiDriverHelper.encodePitch(pitch, octave);
            if (key < limitLo || key > limitHi)
                continue;

            possibleOctaves.add(octave);
        }

        return possibleOctaves;
    }

    // Based on the selected pitch and key limits, find the available octaves and set the UI
    // accordingly
    private void setOctaveChoices() {
        // Get the current choice--keep it if we can
        final int currentOctave = (Integer) octaveSpinner.getSelectedItem();

        // Get the list of possible octaves
        List<Integer> possibleOctaves = getOctaveChoices();

        // Update the data of the octave array adapter
        ArrayAdapter<Integer> octaveAdapter = (ArrayAdapter<Integer>) octaveSpinner.getAdapter();
        octaveAdapter.clear();
        octaveAdapter.addAll(possibleOctaves);

        // Set the octave to be the closest possible choice to our current one
        final int minPossibleOctave = possibleOctaves.get(0);
        final int maxPossibleOctave = possibleOctaves.get(possibleOctaves.size() - 1);
        final int newOctave = currentOctave < minPossibleOctave ? minPossibleOctave :
                (currentOctave > maxPossibleOctave ? maxPossibleOctave : currentOctave);
        octaveSpinner.setSelection(possibleOctaves.indexOf(newOctave));
    }

    // Create a new note, using existing spinners
    public Note(Context context,
                Spinner pitchSpinner,
                final ArrayAdapter<CharSequence> pitchAdapter,
                Spinner octaveSpinner) {

        // Initialize the data
        pitch = defaultPitch;
        octave = defaultOctave;

        // ---Initialize UI elements---

        // Set up pitch spinner
        this.pitchSpinner = pitchSpinner;
        pitchSpinner.setAdapter(pitchAdapter);
        pitchSpinner.setSelection(defaultPitch);
        pitchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos,
                                       long id) {
                pitch = str2Pitch((String) adapterView.getItemAtPosition(pos));
                setOctaveChoices();
                onPitchChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Create an adapter for the octave spinner
        ArrayAdapter<Integer> octaveAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, getOctaveChoices());
        octaveAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);

        // Set up octave spinner
        this.octaveSpinner = octaveSpinner;
        octaveSpinner.setAdapter(octaveAdapter);
        octaveSpinner.setSelection(defaultOctave);
        octaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos,
                                       long id) {
                // Update the data
                final int newOctave = (Integer) adapterView.getItemAtPosition(pos);
                if (octave != newOctave) {
                    octave = newOctave;

                    // Play new sound only if the octave has changed. Do this because program change
                    // could affect our choice of octave, which will also update the sound.
                    onPitchChanged();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });
    }

    // Convert a string representation (e.g A#) to a pitch key number, in octave 0
    public static int str2Pitch(CharSequence pitchStr) {
        // Convert the first character to an ASCII code
        final char firstChar = pitchStr.charAt(0);
        final boolean isSharp = pitchStr.length() > 1;
        final int firstCharAscii = (int) firstChar;

        // Make sure the character is in the range A-
        final int asciiA = (int) 'A';
        final int asciiG =  (int) 'G';
        if (firstCharAscii < asciiA || firstCharAscii > asciiG)
            throw new RuntimeException("Invalid pitch " + pitchStr);

        // Convert the natural pitch using a lookup table
        final int[] pitchLookup = {
                0,  // A, A#
                2,  // B
                3,  // C, C#
                5,  // D, D#
                7,  // E
                8,  // F, F#
                10,  // G, G#
        };
        final int naturalPitch = pitchLookup[firstCharAscii - asciiA];

        // Add sharps, update the drone
        return isSharp ? naturalPitch + 1 : naturalPitch;
    }
}
