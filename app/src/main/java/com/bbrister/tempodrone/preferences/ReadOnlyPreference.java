package com.bbrister.tempodrone.preferences;

/**
 * Like preference, but values can only be read.
 */
public class ReadOnlyPreference<T> {

    private Preference<T> preference;

    public ReadOnlyPreference(Preference<T> preference) {
        this.preference = preference;
    }

    public T read() {
        return preference.read();
    }
}
