package com.bbrister.metrodrone;

import android.content.Context;
import android.content.res.Resources;

import java.util.List;

public class PitchSpinner extends NoteSpinner<NameValPair<Integer>> {

    private String[] sharpStrings;
    private String[] flatStrings;

    public PitchSpinner(Context context, DroneService.DroneBinder binder, final int handle) {
        super(context, binder, handle);

        // Read the pitch strings from the context
        Resources resources = context.getResources();
        sharpStrings = resources.getStringArray(R.array.pitches_sharp_array);
        flatStrings = resources.getStringArray(R.array.pitches_flat_array);
    }

    @Override
    List<Integer> getChoices() {
        return droneBinder.getPitchChoices();
    }

    @Override
    Integer getChoice() {
        return droneBinder.getPitch(handle);
    }

    @Override
    NameValPair<Integer> getItem(Integer code, boolean displaySharps) {
        final String[] pitchStrings = displaySharps ? sharpStrings : flatStrings;
        return new NameValPair<>(pitchStrings[code], code);
    }

    @Override
    int getLayoutResource() {
        return R.layout.pitch_spinner;
    }
}
