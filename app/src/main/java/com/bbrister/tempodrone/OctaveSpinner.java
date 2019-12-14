package com.bbrister.tempodrone;

import android.content.Context;

import java.util.List;

public class OctaveSpinner extends NoteSpinner<Integer> {

    public OctaveSpinner(Context context, DroneService.DroneBinder binder, int handle) {
        super(context, binder, handle);
    }

    // Convenience wrapper, since this doesn't need sharp/flat info
    public void update() {
        update(false);
    }

    @Override
    List<Integer> getChoices() {
        return droneBinder.getOctaveChoices(handle);
    }

    @Override
    Integer getChoice() {
        return droneBinder.getOctave(handle);
    }

    @Override
    Integer getItem(Integer code, boolean displaySharps) {
        return code;
    }

    @Override
    int getLayoutResource() {
        return R.layout.octave_spinner;
    }
}
