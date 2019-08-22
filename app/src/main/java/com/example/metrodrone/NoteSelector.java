package com.example.metrodrone;

import android.content.Context;

import android.view.View;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.List;

// Class for the user interface for selecting a note
public class NoteSelector {

    // Interface to underlying model
    DroneService.DroneBinder droneBinder;
    int handle;

    // Lookup tables
    static String[] pitchLookup;

    // UI items
    public Spinner pitchSpinner;
    public Spinner octaveSpinner;

    // Remove this note from the model
    public void destroy() {
        droneBinder.deleteNote(handle);
        handle = -1;
    }

    // Update the UI
    public void update() {
        pitchSpinner.setSelection(droneBinder.getPitch(handle)); // Calls updateOctave()
    }

    private void updateOctave() {
        List<Integer> possibleOctaves = updateOctaveChoices();
        octaveSpinner.setSelection(droneBinder.getOctave(handle) - possibleOctaves.get(0));
    }

    // Query for the possible octaves for this note
    private List<Integer> getOctaveChoices() {
        return droneBinder.getOctaveChoices(handle);
    }

    // Update the UI, based on the available octave choices.
    private List<Integer> updateOctaveChoices() {
        // Update the data of the octave array adapter
        ArrayAdapter<Integer> octaveAdapter = (ArrayAdapter<Integer>) octaveSpinner.getAdapter();
        octaveAdapter.clear();
        List<Integer> possibleOctaves = getOctaveChoices();
        octaveAdapter.addAll(possibleOctaves);

        return possibleOctaves;
    }

    // Create a new note, using existing spinners
    public NoteSelector(Context context,
                        final DroneService.DroneBinder droneBinder,
                        final int handle,
                        Spinner pitchSpinner,
                        final ArrayAdapter<CharSequence> pitchAdapter,
                        Spinner octaveSpinner) {

        // Initialize the drone interface
        this.droneBinder = droneBinder;
        this.handle = handle;

        // Initialize the lookup table
        if (pitchLookup == null)
            initLookup(context);

        // ---Initialize UI elements---

        // Disable sound updates so we don't create any new sounds
        droneBinder.pushUpdates(false);

        // Set up pitch spinner
        this.pitchSpinner = pitchSpinner;
        pitchSpinner.setAdapter(pitchAdapter);
        droneBinder.ignoreNextUpdate();
        pitchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos,
                                       long id) {
                droneBinder.pushUpdates(false);
                droneBinder.setPitch(handle,
                        str2Pitch((String) adapterView.getItemAtPosition(pos)));
                droneBinder.popUpdates();
                updateOctave();
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
        droneBinder.ignoreNextUpdate();
        octaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos,
                                       long id) {
                // Update the data
                droneBinder.setOctave(handle, (Integer) adapterView.getItemAtPosition(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Update the UI and re-enable sound updates
        update();
        droneBinder.popUpdates();
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

    // Inverse of str2Pitch().
    public static String pitch2Str(final int pitch) {
        return pitchLookup[pitch];
    }

    // Initialize static variables
    static {
        pitchLookup = null;
    }

    // Initialize the pitch lookup table. This essentially inverts the pitch2Str() method.
    private static void initLookup(Context context) {

        // Initialize the lookup table to null
        pitchLookup = new String[SoundSettings.pitchMax + 1];
        for (int i = 0; i < pitchLookup.length; i++) {
            pitchLookup[i] = null;
        }

        // Get the possible strings
        final String[] pitchStrings = context.getResources().getStringArray(R.array.pitches_array);

        // Write in the value of each string, checking for collisions
        for (int i = 0; i < pitchStrings.length; i++) {
            // Get the (pitch, string) pair
            final String pitchStr = pitchStrings[i];
            final int pitchCode = str2Pitch(pitchStr);

            // Check for collisions
            if (pitch2Str(pitchCode) != null)
                throw new RuntimeException(String.format("Found a collision in pitch2Str: (%d, %s)",
                        pitchCode, pitchStr));

            // Save the inverse
            pitchLookup[pitchCode] = pitchStr;
        }
    }
}
