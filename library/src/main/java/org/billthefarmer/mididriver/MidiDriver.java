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

import android.content.res.AssetManager;

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
    public void start(AssetManager assetManager, final String soundfontFilename)
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
        if (!isStarted && !init(assetManager, soundfontFilename)) {
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

    /**
     * Change the program
     */
    public void changeProgram(byte programNumber) {

        if (!changeProgramJNI(programNumber))
            throw new RuntimeException("Failed to change the MIDI program");
    }

    /**
     * Render and check for errors.
     */
    public void renderNotes(byte pitches[], byte velocity, long noteDurationMs,
                       long recordDurationMs) {
        if (!render(pitches, velocity, noteDurationMs, recordDurationMs))
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
    private native boolean init(Object assetManager, String soundfontFilename);

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
     * @param pitches The pitches to play.
     * @param velocity The velocity at which to play them.
     * @param noteDurationMs - The delay before end is sent, in ms.
     * @param recordDurationMs - The total duration of the recording, in ms.
     *
     */
    private native boolean render(byte pitches[], byte velocity, long noteDurationMs,
                                 long recordDurationMs);

    /**
     *  Change the MIDI program.
     *
     * @return True on success
     */
    public native boolean changeProgramJNI(byte programNum);

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
