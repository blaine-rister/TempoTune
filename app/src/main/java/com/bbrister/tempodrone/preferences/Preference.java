package com.bbrister.tempodrone.preferences;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Defines a key-value pair that is written and read to the SharedPreferences file
 */
public abstract class Preference<T> {

    // Name the preferences file uniquely for this app
    private final static String prefFileName = Preference.class.getPackage().getName();

    // Dummy update interface doing nothing
    private final static UpdateInterface dummyUpdateInterface = new UpdateInterface() {
        @Override
        public void update() {};
    };

    // Optional interface to trigger a callback when the value is updated
    UpdateInterface updateInterface;

    // Handle to the context-dependent preferences
    private SharedPreferences preferences;

    // Key from which to retrieve the value
    private String key;

    // Default value of the preference
    private T defaultValue;

    protected abstract T readValue(SharedPreferences preferences, String key, T defaultValue); // Private helper to read the value
    protected abstract void writeValue(SharedPreferences.Editor editor, String key, T value); // Private helper to write the value

    public Preference(Context context, String key, T defaultValue) {
        preferences = context.getSharedPreferences(prefFileName, Context.MODE_PRIVATE);
        this.key = key;
        this.defaultValue = defaultValue;
        updateInterface = dummyUpdateInterface;
    }

    /* Set the update interface, replacing the dummy one. Returns a handle to this object for
     * chaining */
    public Preference<T> setUpdate(UpdateInterface updateInterface) {
        this.updateInterface = updateInterface;
        return this;
    }

    /* Read a value from the preferences. Calls readValue from the subclass. */
    public T read() {
        return readValue(preferences, key, defaultValue);
    }

    /* Write a value to the preferences. Calls writeValue from the subclass. */
    public void write(T value) {

        // Check the existing value and see if this is an update
        final T previousValue = read();
        if (previousValue.equals(value)) {
            return;
        }

        // Write the new value, blocking until complete
        SharedPreferences.Editor editor = preferences.edit();
        writeValue(editor, key, value);
        editor.commit();

        // Call the optional update interface
        updateInterface.update();
    }
}
