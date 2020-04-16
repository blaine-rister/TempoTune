package com.bbrister.tempodrone;

import android.content.Context;

import java.util.List;

public class OctaveSpinner extends NoteSpinner<Integer> {

    public OctaveSpinner(Context context, DroneService.DroneBinder binder, int handle) {
        super(context, binder, handle);
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
    Integer getItem(Integer code) {
        return code;
    }

    @Override
    int getLayoutResource() {
        return R.layout.octave_spinner;
    }
}
