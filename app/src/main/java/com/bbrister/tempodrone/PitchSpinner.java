package com.bbrister.tempodrone;

import android.content.Context;
import android.content.res.Resources;

import com.bbrister.tempodrone.preferences.ReadOnlyPreference;

import java.util.List;

public class PitchSpinner extends NoteSpinner<NameValPair<Integer>> {

    // Preferences
    ReadOnlyPreference<Boolean> displaySharps;

    // State
    private String[] sharpStrings;
    private String[] flatStrings;

    public PitchSpinner(Context context, DroneService.DroneBinder binder,
                        ReadOnlyPreference<Boolean> displaySharps, final int handle) {
        super(context, binder, handle);

        // Set the preferences
        this.displaySharps = displaySharps;

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
    NameValPair<Integer> getItem(Integer code) {
        final String[] pitchStrings = displaySharps.read() ? sharpStrings : flatStrings;
        return new NameValPair<>(pitchStrings[code], code);
    }

    @Override
    int getLayoutResource() {
        return R.layout.pitch_spinner;
    }
}
