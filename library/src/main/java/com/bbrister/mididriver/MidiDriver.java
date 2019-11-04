////////////////////////////////////////////////////////////////////////////////
//
//  MidiDriver - An Android Midi Driver.
//
//  Copyright (C) 2013	Bill Farmer
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//
//  Bill Farmer	 william j farmer [at] yahoo [dot] co [dot] uk.
//
///////////////////////////////////////////////////////////////////////////////

package com.bbrister.mididriver;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.util.Arrays;

/**
 * MidiDriver class
 */
public class MidiDriver
{
    private static boolean isStarted;

    /**
     * Class constructor
     */
    public MidiDriver()
    {
    }

    /**
     * Round an int to the nearest byte value. This is important to avoid overflow e.g. in key
     * ranges for soundfont files.
     */
    public static byte toByte(int i) {
        return (byte) Math.max(Byte.MIN_VALUE, Math.min(i, Byte.MAX_VALUE));
    }

    /**
     * Start the midi driver, querying native audio parameters.
     */
    public void start(Context context)
    {
        // Query the device sample rate
        final int sampleRate = PlaybackDriver.getSampleRate(context);

        // Initialize the MIDI, if it hasn't been
        if (!isStarted && !initJNI(sampleRate)) {
            throw new RuntimeException(BuildConfig.DEBUG_EXCEPTIONS ? "Failed to initialize MIDI" :
                    "");
        }
        isStarted = true;
    }

    /**
     * Load a soundfont from an asset file. Uses the context to locate the asset.
     */
    public void loadSounds(final Context context, final String filename) {
        // Get the assets
        AssetManager assetManager = context.getAssets();

        // Ensure the asset exists
        String[] assets = null;
        try {
            assets = assetManager.list("");
        } catch (IOException e) {
            if (BuildConfig.DEBUG_EXCEPTIONS) {
                throw new RuntimeException("Failed to query assets");
            }
        }
        if (assets == null || !Arrays.asList(assets).contains(filename)) {
            throw new RuntimeException(BuildConfig.DEBUG_EXCEPTIONS ? String.format(
                    "Failed to locate asset %s", filename) : "");
        }

        // Load the soundfont
        if (!loadSoundfontJNI(assetManager, filename)) {
            throw new RuntimeException(BuildConfig.DEBUG_EXCEPTIONS ? String.format(
                    "Failed to load soundfont %s", filename) : "");
        }
    }

    /**
     * Stop midi driver
     */
    public void stop()
    {
        shutdownJNI();
        isStarted = false;
    }

    /*
     * Query if the given program number is valid.
     */
    public boolean queryProgram(byte programNumber) {
        int result = queryProgramJNI(programNumber);
        if (result < 0)
            throw new RuntimeException(BuildConfig.DEBUG_EXCEPTIONS ? String.format(
                    "Failed to query program number %d", programNumber) : "");

        return result > 0;
    }

    /*
     * Get the name of a program given its number.
     */
    public String getProgramName(byte programNumber) {
        String name = getProgramNameJNI(programNumber);
        if (name.isEmpty())
            throw new RuntimeException(BuildConfig.DEBUG_EXCEPTIONS ? String.format(
                    "Failed to get the name of program %d", programNumber) : "");

        return name;
    }

    /**
     * Change the program
     */
    public void changeProgram(byte programNumber) {

        if (!changeProgramJNI(programNumber))
            throw new RuntimeException(BuildConfig.DEBUG_EXCEPTIONS ?
                    "Failed to change the MIDI program" : "");
    }

    /**
     * Retrieve the current program number.
     */
    public int getProgram() {
        final int program = getProgramJNI();
        if (program < 0)
            throw new RuntimeException(BuildConfig.DEBUG_EXCEPTIONS ?
                    "Failed to get the MIDI program" : "");
        return program;
    }

    /**
     * Get the key range for the current program, as a byte mask.
     */
    public boolean[] getKeyRange() {
        final boolean[] keyRange = getKeyRangeJNI();
        if (keyRange == null)
            throw new RuntimeException(BuildConfig.DEBUG_EXCEPTIONS ?
                    "Failed to get the key range" : "");
        return keyRange;
    }

    /**
     * Render and check for errors.
     */
    public float[] renderNotes(RenderSettings settings) {
        final float[] sound = renderJNI(settings);
        if (sound == null)
            throw new RuntimeException(BuildConfig.DEBUG_EXCEPTIONS ?
                    "Failed to render pitches" : "");

        return sound;
    }

    /**
     * Get the synth maximum polyphony count.
     */
    public int getMaxVoices() {
        return getMaxVoicesJNI();
    }

    /**
     * Get the number of available reverb presets.
     */
    public int getNumReverbPresets() { return getNumReverbPresetsJNI(); }

    // Native midi methods

    /**
     * Initialise native code
     *
     * @return true for success
     */
    private boolean initJNI(int sampleRate) {
        return A(sampleRate);
    }
    private native boolean A(int sampleRate);


    /**
     *
     * @return The maximum polyphony count.
     */
    private int getMaxVoicesJNI() {
        return B();
    }
    private native int B();

    /**
     * Get the key range for the current program, as a byte mask.
     * @return The key range, or null on error.
     */
    private boolean[] getKeyRangeJNI() {
        return D();
    }
    private native boolean[] D();

    /**
     * Renders an audio signal, then loops it.
     *
     * @param settings holds all the information to play the notes
     *
     */
    private float[] renderJNI(final RenderSettings settings) {
        return F(
                settings.pitchArray,
                settings.noteDurationMs,
                settings.recordDurationMs,
                settings.reverbPreset,
                settings.velocity,
                settings.volumeBoost
        );
    }
    private native float[] F(
            byte[] pitches,
            long noteDurationMs,
            long recordingDurationMs,
            int reverbPreset,
            byte velocity,
            boolean volumeBoost
    );

    /*
     * Query if the given MIDI program number is valid.
     *
     * @return 1 if valid, 0 if invalid, -1 on error.
     */
    private int queryProgramJNI(byte programNum) {
        return G(programNum);
    }
    private native int G(byte programNum);

    /*
     * Get the name of a given MIDI program.
     *
     * @return The program name.
     */
    private String getProgramNameJNI(byte programNum) {
        return H(programNum);
    }
    private native String H(byte programNum);

    /**
     *  Change the MIDI program.
     *
     * @return True on success
     */
    private boolean changeProgramJNI(byte programNum) {
        return I(programNum);
    }
    private native boolean I(byte programNum);

    /**
     * Retrieve the current MIDI program.
     *
     * @return Program number, or -1 on failure.
     */
    private int getProgramJNI() {
        return J();
    }
    private native int J();

    /**
     * Shut down native code
     *
     * @return true for success
     */
    private boolean shutdownJNI() {
        return K();
    }
    private native boolean K();

    /**
     * Get the number of available reverb presets.
     */
    private int getNumReverbPresetsJNI() {
        return L();
    }
    private native int L();

    /**
     * Load a new soundfont.
     */
    private boolean loadSoundfontJNI(Object assetManager, String filename) {
        return M(assetManager, filename);
    }
    private native boolean M(Object assetManager, String filename);

    // Load midi library
    static
    {
        isStarted = false;
        System.loadLibrary("midi");
    }
}
