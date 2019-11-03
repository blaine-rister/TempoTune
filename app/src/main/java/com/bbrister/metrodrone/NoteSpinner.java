package com.bbrister.metrodrone;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.List;

public abstract class NoteSpinner<T> {

    Spinner spinner;
    ArrayAdapter<T> adapter;
    DroneService.DroneBinder droneBinder;
    int handle;

    abstract List<Integer> getChoices(); // Get the integer choices
    abstract Integer getChoice(); // Get the current integer choice
    abstract T getItem(Integer code, boolean displaySharps); // Get the displayed item
    abstract int getLayoutResource();

    public NoteSpinner(Context context, DroneService.DroneBinder binder, int handle) {
        // Register the service connection
        droneBinder = binder;
        this.handle = handle;

        // Inflate the spinner
        LayoutInflater inflater = LayoutInflater.from(context);
        spinner = (Spinner) inflater.inflate(getLayoutResource(), null);

        // Create an adapter
        adapter = new ArrayAdapter<>(context, R.layout.pitch_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    // Update the data of this spinner and set the selection
    public void update(boolean displaySharps) {
        // Update the data
        List<Integer> choices = getChoices();
        adapter.clear();
        for (Integer choice : choices) {
            adapter.add(getItem(choice, displaySharps));
        }

        // Set the selection
        spinner.setSelection(choices.indexOf(getChoice()));
    }

    // To set a listener for the underlying spinner
    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
        spinner.setOnItemSelectedListener(listener);
    }
}
