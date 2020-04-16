package com.bbrister.tempodrone.preferences;

import android.content.Context;

/**
 * Convenience class which typecasts byte values before writing to an IntegerPreference.
 */
public class BytePreference  {

    IntegerPreference integerPreference;

    public BytePreference(Context context, String key, byte defaultValue) {
        integerPreference = new IntegerPreference(context, key, defaultValue);
    }

    /** Need this vacuous function to avoid type casting the return of setUpdate **/
    public BytePreference setUpdate(UpdateInterface updateInterface) {
        integerPreference.setUpdate(updateInterface);
        return this;
    }

    public byte read() {
        return (byte) (int) integerPreference.read();
    }

    public void write(byte val) {
        integerPreference.write((int) val);
    }
}
