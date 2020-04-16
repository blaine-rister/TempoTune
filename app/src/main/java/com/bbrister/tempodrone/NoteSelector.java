package com.bbrister.tempodrone;

import android.content.Context;

import android.view.View;

import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;

import com.bbrister.tempodrone.preferences.ReadOnlyPreference;

// Class for the user interface for selecting a note
public class NoteSelector {

    // Interface to underlying model
    DroneService.DroneBinder droneBinder;
    int handle;

    // UI items
    public LinearLayout layout;
    private PitchSpinner pitchSpinner;
    private OctaveSpinner octaveSpinner;

    // Remove this note from the model
    public void destroy() {
        droneBinder.deleteNote(handle);
        handle = -1;
    }

    // Update the UI
    public void update() {
        pitchSpinner.update(); // Calls updateOctave()
        octaveSpinner.update(); // Just in case--spinners are unreliable
    }

    // Create a new note, using existing spinners
    public NoteSelector(Context context,
                        final DroneService.DroneBinder droneBinder,
                        final int handle,
                        final ReadOnlyPreference<Boolean> displaySharps) {

        // Initialize the drone interface
        this.droneBinder = droneBinder;
        this.handle = handle;

        // ---Initialize UI elements---

        // Create the spinners
        pitchSpinner = new PitchSpinner(context, droneBinder, displaySharps, handle);
        octaveSpinner = new OctaveSpinner(context, droneBinder, handle);

        // Initialize the LinearLayout and put the spinners in it
        layout = new LinearLayout(context);
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(pitchSpinner.getView(), layoutParams);
        layout.addView(octaveSpinner.getView(), layoutParams);

        // Update the UI. Called before listeners are installed, to avoid triggering them
        update();

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
