package com.bbrister.tempodrone.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public class FloatPreference extends Preference<Float> {

    public FloatPreference(Context context, String key, float defaultValue) {
        super(context, key, defaultValue);
    }

    /** Need this vacuous function to avoid type casting the return of setUpdate **/
    public FloatPreference setUpdate(UpdateInterface updateInterface) {
        super.setUpdate(updateInterface);
        return this;
    }

    @Override
    protected Float readValue(SharedPreferences preferences, String key, Float defaultValue) {
        return preferences.getFloat(key, defaultValue);
    }

    @Override
    protected void writeValue(SharedPreferences.Editor editor, String key, Float val) {
        editor.putFloat(key, val);
    }
}
