package com.bbrister.tempodrone.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public class IntegerPreference extends Preference<Integer> {

    public IntegerPreference(Context context, String key, int defaultValue) {
        super(context, key, defaultValue);
    }

    /** Need this vacuous function to avoid type casting the return of setUpdate **/
    public IntegerPreference setUpdate(UpdateInterface updateInterface) {
        super.setUpdate(updateInterface);
        return this;
    }

    public Integer readValue(SharedPreferences preferences, String key, Integer defaultValue) {
        return preferences.getInt(key, defaultValue);
    }

    @Override
    protected void writeValue(SharedPreferences.Editor editor, String key, Integer val) {
        editor.putInt(key, val);
    }
}
