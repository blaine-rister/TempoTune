package com.bbrister.tempodrone;

import android.content.Context;

import android.view.View;

import android.widget.AdapterView;

// Class for the user interface for selecting a note
public class NoteSelector {

    // Interface to underlying model
    DroneService.DroneBinder droneBinder;
    int handle;

    // Lookup tables
    static String[] pitchLookup;

    // UI items
    public PitchSpinner pitchSpinner;
    public OctaveSpinner octaveSpinner;

    // Remove this note from the model
    public void destroy() {
        droneBinder.deleteNote(handle);
        handle = -1;
    }

    // Update the UI
    public void update(final boolean displaySharps) {
        pitchSpinner.update(displaySharps); // Calls updateOctave()
        octaveSpinner.update(); // Just in case--spinners are unreliable
    }

    // Create a new note, using existing spinners
    public NoteSelector(Context context,
                        final DroneService.DroneBinder droneBinder,
                        final int handle,
                        final boolean displaySharps) {

        // Initialize the drone interface
        this.droneBinder = droneBinder;
        this.handle = handle;

        // ---Initialize UI elements---

        pitchSpinner = new PitchSpinner(context, droneBinder, handle);
        octaveSpinner = new OctaveSpinner(context, droneBinder, handle);

        // Update the UI. Called before listeners are installed, to avoid triggering them
        update(displaySharps);

        // Install the pitch spinner listener
        pitchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos,
                                       long id) {
                droneBinder.setPitch(handle,
                        ((NameValPair<Integer>) adapterView.getItemAtPosition(pos)).val);
                octaveSpinner.update();
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
