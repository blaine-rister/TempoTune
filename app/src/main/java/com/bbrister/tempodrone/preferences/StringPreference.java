package com.bbrister.tempodrone.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public class StringPreference extends Preference<String> {
    public StringPreference(Context context, String key, String defaultValue) {
        super(context, key, defaultValue);
    }

    /** Need this vacuous function to avoid type casting the return of setUpdate **/
    public StringPreference setUpdate(UpdateInterface updateInterface) {
        super.setUpdate(updateInterface);
        return this;
    }

    @Override
    protected String readValue(SharedPreferences preferences, String key, String defaultValue) {
        return preferences.getString(key, defaultValue);
    }

    @Override
    protected void writeValue(SharedPreferences.Editor editor, String key, String val) {
        editor.putString(key, val);
    }
}
