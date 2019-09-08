package com.bbrister.metrodrone;

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
        pitchSpinner.setSelection(droneBinder.getPitch(handle), true); // Calls updateOctave()
        updateOctave(); // Just in case--spinners are unreliable
    }

    private void updateOctave() {
        List<Integer> possibleOctaves = updateOctaveChoices();
        octaveSpinner.setSelection(droneBinder.getOctave(handle) - possibleOctaves.get(0), true);
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
                        final ArrayAdapter<NameValPair> pitchAdapter,
                        Spinner octaveSpinner) {

        // Initialize the drone interface
        this.droneBinder = droneBinder;
        this.handle = handle;

        // ---Initialize UI elements---

        // Set up pitch spinner
        this.pitchSpinner = pitchSpinner;
        pitchSpinner.setAdapter(pitchAdapter);

        // Create an adapter for the octave spinner
        ArrayAdapter<Integer> octaveAdapter = new ArrayAdapter<>(context,
                R.layout.pitch_spinner_item, getOctaveChoices());
        octaveAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Set up octave spinner
        this.octaveSpinner = octaveSpinner;
        octaveSpinner.setAdapter(octaveAdapter);

        // Update the UI. Called before listeners are installed, to avoid triggering them
        update();

        // Install the pitch spinner listener
        pitchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos,
                                       long id) {
                droneBinder.setPitch(handle, ((NameValPair) adapterView.getItemAtPosition(pos)).i);
                updateOctave();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Install the octave spinner listener
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
    }
}
