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

package org.billthefarmer.mididriver;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioManager;

import java.util.Set;
import java.util.Iterator;

import java.io.IOException;
import java.io.InputStream;

/**
 * MidiDriver class
 */
public class MidiDriver
{
    /**
     * Midi start listener
     */
    private OnMidiStartListener listener;
    private static boolean isStarted;

    /**
     * Class constructor
     */
    public MidiDriver()
    {
    }

    /**
     * Start the midi driver, given a soundfont file stored as an Android asset. Must also pass an
     * AssetManager which is capable of reading the asset.
     */
    public void start(AssetManager assetManager, final String soundfontFilename,
                      final int sampleRate, final int bufferSize)
    {
        // Test that the asset can be opened
        try {
           InputStream stream = assetManager.open(soundfontFilename, AssetManager.ACCESS_RANDOM);
           stream.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to open soundfont " + soundfontFilename);
        }

        // Initialize the MIDI, if it hasn't been
        if (!isStarted && !init(assetManager, soundfontFilename, sampleRate, bufferSize)) {
            throw new RuntimeException("Failed to initialize MIDI");
        }
        isStarted = true;

        // Call the listener
        if (listener != null)
            listener.onMidiStart();
    }

    /**
     * Stop midi driver
     */
    public void stop()
    {
        shutdown();
        isStarted = false;
    }

    /**
     * Set midi driver start listener
     *
     * @param OnMidiStartListener
     */
    public void setOnMidiStartListener(OnMidiStartListener l)
    {
        listener = l;
    }

    /**
     * Midi start listener interface
     */
    public interface OnMidiStartListener
    {
        void onMidiStart();
    }

    /*
     * Query if the given program number is valid.
     */
    public boolean queryProgram(byte programNumber) {
        int result = queryProgramJNI(programNumber);
        if (result < 0)
            throw new RuntimeException(String.format("Failed to query program number %d",
                    programNumber));

        return result > 0;
    }

    /*
     * Get the name of a program given its number.
     */
    public String getProgramName(byte programNumber) {
        String name = getProgramNameJNI(programNumber);
        if (name.isEmpty())
            throw new RuntimeException(String.format("Failed to get the name of program %d",
                    programNumber));

        return name;
    }

    /**
     * Change the program
     */
    public void changeProgram(byte programNumber) {

        if (!changeProgramJNI(programNumber))
            throw new RuntimeException("Failed to change the MIDI program");
    }

    /**
     * Retrieve the current program number.
     */
    public int getProgram() {
        final int program = getProgramJNI();
        if (program < 0)
            throw new RuntimeException("Failed to get the MIDI program");
        return program;
    }

    /**
     * Get the minimum key for the current program.
     */
    public byte getKeyMin() {
        final int keyMin = getKeyMinJNI();
        if (keyMin < 0)
            throw new RuntimeException("Failed to get the minimum key");
        return (byte) keyMin;
    }

    /**
     * Get the maximum key for the current program.
     */
    public byte getKeyMax() {
        final int keyMax = getKeyMaxJNI();
        if (keyMax < 0)
            throw new RuntimeException("Failed to get the maximum key");
        return (byte) keyMax;
    }

    /**
     * Render and check for errors.
     */
    public void renderNotes(Set<Byte> pitches, byte velocity, long noteDurationMs,
                       long recordDurationMs) {

        // Convert the pitch set to an array
        byte pitchArray[] = new byte[pitches.size()];
        Iterator<Byte> it = pitches.iterator();
        for (int i = 0; it.hasNext(); i++) {
            pitchArray[i] = it.next();
        }

        if (!render(pitchArray, velocity, noteDurationMs, recordDurationMs))
            throw new RuntimeException("Failed to render pitches");
    }

    /*
     * Pause playback and check for errors.
     */
    public void pause() {
        if (!pauseJNI())
            throw new RuntimeException("Failed to pause playback");
    }

    // Native midi methods

    /**
     * Initialise native code
     *
     * @return true for success
     */
    private native boolean init(Object assetManager, String soundfontFilename, int sampleRate,
                                int bufferSize);

    /**
     * Returm part of EAS config
     *
     * @return Int array of part of EAS config
     *   config[0] = pLibConfig->maxVoices;
     *   config[1] = pLibConfig->numChannels;
     *   config[2] = pLibConfig->sampleRate;
     *   config[3] = pLibConfig->mixBufferSize;
     */
    public  native int[]   config();

    /**
     *
     * @return
     */
    public native boolean pauseJNI();

    /**
     * Renders an audio signal, then loops it.
     *
     * @param pitches The pitches to play. Duplicates will cause an error.
     * @param velocity The velocity at which to play them.
     * @param noteDurationMs - The delay before end is sent, in ms.
     * @param recordDurationMs - The total duration of the recording, in ms.
     *
     */
    private native boolean render(byte pitches[], byte velocity, long noteDurationMs,
                                 long recordDurationMs);

    /*
     * Query if the given MIDI program number is valid.
     *
     * @return 1 if valid, 0 if invalid, -1 on error.
     */
    public native int queryProgramJNI(byte programNum);

    /*
     * Get the name of a given MIDI program.
     *
     * @return The program name.
     */
    public native String getProgramNameJNI(byte programNum);

    /**
     *  Change the MIDI program.
     *
     * @return True on success
     */
    public native boolean changeProgramJNI(byte programNum);

    /**
     * Retrieve the current MIDI program.
     *
     * @return Program number, or -1 on failure.
     */
    public native int getProgramJNI();

    /**
     * Get the minimum key for the current program.
     * @return The key value, or -1 on error.
     */
    public native int getKeyMinJNI();

    /**
     * Get the maximum key for the current program.
     * @return The key value, or -1 on error.
     */
    public native int getKeyMaxJNI();

    /**
     * Shut down native code
     *
     * @return true for success
     */
    private native boolean shutdown();

    // Load midi library
    static
    {
        isStarted = false;
        System.loadLibrary("midi");
    }
}
