package com.bbrister.tempodrone.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public class BooleanPreference extends Preference<Boolean> {

    public BooleanPreference(Context context, String key, boolean defaultValue) {
        super(context, key, defaultValue);
    }

    /** Need this vacuous function to avoid type casting the return of setUpdate **/
    public BooleanPreference setUpdate(UpdateInterface updateInterface) {
        super.setUpdate(updateInterface);
        return this;
    }

    @Override
    protected Boolean readValue(SharedPreferences preferences, String key, Boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    @Override
    protected void writeValue(SharedPreferences.Editor editor, String key, Boolean val) {
        editor.putBoolean(key, val);
    }
}
