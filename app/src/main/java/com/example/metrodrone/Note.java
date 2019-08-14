package com.example.metrodrone;

import android.view.View;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public abstract class Note {

    // Defaults
    public final int defaultPitch = 0; // A
    public final int defaultOctave = 3; // 0-7

    // Pitch data
    private int pitch; // 0-11
    private int octave; // 0-7

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

    // Create a new note, using existing spinners
    public Note(Spinner pitchSpinner,
                final ArrayAdapter<CharSequence> pitchAdapter,
                Spinner octaveSpinner,
                final ArrayAdapter<Integer> octaveAdapter) {

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
                setPitch((String) adapterView.getItemAtPosition(pos));
                onPitchChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Set up octave spinner
        this.octaveSpinner = octaveSpinner;
        octaveSpinner.setAdapter(octaveAdapter);
        octaveSpinner.setSelection(defaultOctave);
        octaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos,
                                       long id) {
                octave = (Integer) adapterView.getItemAtPosition(pos);
                onPitchChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });
    }

    // Updates the pitch given the pitch name in pitchStr. This does not change the UI--it's used in
    // response to the UI.
    private void setPitch(CharSequence pitchStr) {
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
        pitch = isSharp ? naturalPitch + 1 : naturalPitch;
    }
}
