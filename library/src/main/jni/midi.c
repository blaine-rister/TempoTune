////////////////////////////////////////////////////////////////////////////////
//
//  MidiDriver - An Android Midi Driver.
//
//  Copyright (C) 2013	Bill Farmer
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
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

#include <jni.h>
#include <dlfcn.h>
#include <assert.h>
#include <pthread.h>
#include <malloc.h>
#include <math.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>

#include <fluidlite.h>

// Private headers
#include "global.h"

#ifdef __cplusplus
extern "C" {
#endif

// Internal fluid functions which are not in the public header
fluid_preset_t* fluid_synth_find_preset(fluid_synth_t* synth,
                                        unsigned int banknum,
                                        unsigned int prognum);
int fluid_synth_all_sounds_off(fluid_synth_t* synth, int chan);

// Internal functions
static int get_program(void);
static size_t ms2Samples(const size_t ms);
static int setReverbPreset(const int preset);

// Struct to hold sound synthesis parameters
struct sound_settings {
    jbyte *pitches;
    long noteDurationMs;
    long recordingDurationMs;
    int numPitches;
    int volumeBoost;
    int reverbPreset;
    uint8_t velocity;
};

// Struct to hold reverb parameters
struct reverb_settings {
    double roomSize;
    double damping;
    double width;
    double level;
};

// List of default reverb settings
static struct reverb_settings reverb_presets[] = {
        { 0.2,      0.0,       0.5,       0.9 },
        { 0.4,      0.2,       0.5,       0.8 },
        { 0.6,      0.4,       0.5,       0.7 },
        { 0.8,      0.7,       0.5,       0.6 },
        { 0.8,      1.0,       0.5,       0.5 }
};
static const int numReverbPresets = sizeof(reverb_presets) / sizeof(struct reverb_settings) + 1;

// MIDI info
const uint8_t velocityMax = 127; // Maximum allowed velocity in MIDI

// Constants
static const int midiChannel = 0;
static const int sfBank = 0;
static const int maxVoices = 8;

// Sound parameters
int sampleRate;

// Fluid data
static fluid_synth_t *fluidSynth = NULL;
static fluid_settings_t *fluidSettings = NULL;
static int soundfontId = -1;

// Checks for initialization, doesn't print any messages.
static jboolean isInitializedHelper() {
    return fluidSynth == NULL ? JNI_FALSE : JNI_TRUE;
}

// Check if the library is initialized. If not, return JNI_FALSE and print a message.
#ifdef NDEBUG
#define isInitialized(...) isInitializedHelper()
#else
// Checks for initialization, prints an error message if not initialized.
static jboolean isInitialized(const char *functionName) {
    const jboolean result = isInitializedHelper();
    if (result == JNI_FALSE) {
        LOG_E(LOG_TAG, "Must initialize fluid before calling %s", functionName);
    }
    return result;
}
#endif

// Computes the number of samples needed. Does not take into account the number of channels
static size_t ms2Samples(const size_t ms) {
    const size_t msPerSecond = 1000;
    return (ms * (size_t) sampleRate) / msPerSecond;
}

// Check if the given program number is available in the soundfont.
static int queryProgram(const uint8_t programNum, int *const isAvailable) {

    if (!isInitialized("queryProgram"))
        return -1;

    *isAvailable = fluid_synth_find_preset(fluidSynth, sfBank, programNum) != NULL;
    return 0;
}

// Get the name of the given program, if it exists. Returns NULL if it does not.
static const char *getProgramName(const uint8_t programNum) {

    fluid_preset_t *preset;

    if (!isInitialized("programName"))
        return NULL;

    if ((preset = fluid_synth_find_preset(fluidSynth, sfBank, programNum)) == NULL)
        return NULL;

    return preset->get_name(preset);
}

// Change the program
static int changeProgram(const uint8_t programNum) {

    int result;

    if (!isInitialized("changeProgram"))
        return -1;

    result = fluid_synth_program_change(fluidSynth, midiChannel, programNum);
    if (result != 0) {
        LOG_E(LOG_TAG, "Failed to change fluid program to %uc.", programNum);
    }

    return result;
}

// Get the key range for the currently selected program. Can substitue NULL for either arugment.
static jboolean getProgramRange(int *min, int *max) {

    if (!isInitialized("getProgramRange"))
        return JNI_FALSE;

    // Get the preset
    const fluid_preset_t *const preset = fluid_synth_get_channel_preset(fluidSynth, midiChannel);
    if (preset == NULL)
        return JNI_FALSE;

    preset->get_range(preset, min, max);

    return JNI_TRUE;
}

// Convenience wrappers for getProgramRange
static int getProgramKeyMin(void) {
    int min;
    return getProgramRange(&min, NULL) == JNI_TRUE ? min : -1;
}
static int getProgramKeyMax(void) {
    int max;
    return getProgramRange(NULL, &max) == JNI_TRUE ? max : -1;
}

// Mute all existing notes, including the release phase
static int muteSounds() {
    return !isInitialized("muteSounds") || fluid_synth_all_sounds_off(fluidSynth, midiChannel);
}

// Start a note
static int startNote(const uint8_t pitch, const uint8_t velocity) {
    return !isInitialized("startNote") || fluid_synth_noteon(fluidSynth, midiChannel, pitch,
                                                             velocity);

}

// End a note. Returns 0 on success, -1 on error, and 1 if the note failed to end. This can happen
// e.g. if the note is silent and fluid automatically kills it.
static int endNote(const uint8_t pitch) {
    if (!isInitialized("endNote"))
        return -1;

    // Return something different if the note end call fails. This can happen when the note is
    // silent.
    return fluid_synth_noteoff(fluidSynth, midiChannel, pitch) == 0 ? 0 : 1;
}

// Normalize the audio so the maximum value is given by maxLevel, on a scale of 0-1.
static int normalize(float *const buffer, const size_t bufferLength, const double maxLevel) {

    float maxBefore;
    size_t i;

    if (maxLevel < 0. || maxLevel > maxFloatLevel) {
        LOG_E(LOG_TAG, "normalize: invalid maxLevel: %f", maxLevel);
        return -1;
    }

    // Get the maximum value of the un-normalized stream
    maxBefore = 0;
    for (i = 0; i < bufferLength; i++) {
        const float sampleLevel = fabsf(buffer[i]);
        maxBefore = sampleLevel > maxBefore ? sampleLevel : maxBefore;
    }

    // Compute the linear gain
    const float gain = (float) (maxLevel / (double) maxBefore);

    // Apply the gain
    for (i = 0; i < bufferLength; i++) {
        buffer[i] *= gain;
    }

    return 0;
}

// Convert a MIDI pitch number to a frequency. pitch 0 corresponds to A0
static double pitch2frequency(const int pitch) {
    const double a4 = 440;
    const int pitchesPerOctave = 12;
    const int a0key = 21;
    const int a4key = a0key + pitchesPerOctave * 4;
    return a4 * pow(2, (double) (pitch - a4key) / pitchesPerOctave);
}

// Squared linear value to decibels
static float linSq2db(const float linSq) {
    const float minVal = 0.00001f; // To avoid really small values
    return 0.5 * 20 * ((linSq) > minVal ? log10f(linSq) : log10f(minVal));
}

// Decibels to linear
static float db2lin(const float db) {
    return powf(10, db / 20);
}

// RMS level detector, extrapolates with value 0. Applies a 1st-order IIR filter with parameters a0,
// b0 = 1 - a0
static float update_level(const float sample, const float level, const float a0) {
    const float sampleSq = sample * sample;
    return a0 * level + (1.f - a0) * sampleSq;
}

// Compute the time constant to decay by a certain amount in a certain number of samples.
static float getTimeConstant(const double decay, const double numSamples) {
    // Impulse invariance: a0^T = decay <-> log(a0) = log(decay) / T <-> a0 = exp(log(decay) / T)
    return numSamples == 0 ? 1 : expf((float) (log(decay) / numSamples));
}

// Like getTimeConstant, but for squared levels
static float getSquaredTimeConstant(const double decay, const double numSamples) {
    return getTimeConstant(decay * decay, numSamples);
}

// Compress the dynamic range of the audio. The allowable dynamic range is given by the velocity
// parameter. minFrequency is the fundamental frequency of the pitch, which determines the level
// detector window length.
static int compressDNR(float *const buffer, const size_t bufferLength, const size_t attackLength,
        const uint8_t velocity, const double minFrequency) {

    int i, maxLevelIdx;
    float level, maxLevelSq;

    // Verify inputs
    if (attackLength > bufferLength) {
        LOG_E(LOG_TAG, "Attack length %d greater than buffer length %d", (int) attackLength,
              (int) bufferLength);
        return -1;
    }

    // Internal parameters
    const double minCompressionRatio = 1.1; // This is reached at maximum velocity
    const double maxCompressionRatio = 5.0; // This is reached at minimum velocity
    const double periodDecay = 0.8; // Decay this much in one note period

    // Derived parameters -- compression ratio increases as velocity decreases
    const double compressionRatio = minCompressionRatio +
            (maxCompressionRatio - minCompressionRatio) *
            (1.0 - ((double) velocity + 1) / ((double) velocityMax + 1));
    const float compressionFactor = 1.f / (float) compressionRatio;

    // Convert the minimum frequency to a period in samples
    const double periodSamples = ceil((double) sampleRate / minFrequency);

    // Filter parameters -- note we're using levels squared
    const float a0Attack = getSquaredTimeConstant(periodDecay, periodSamples);

    // Run the level detector over the attack phase, recording the maximum level
    maxLevelIdx = maxLevelSq = level = 0;
    for (i = 0; i < attackLength; i++) {
        // Level detection
        level = update_level(buffer[i], level, a0Attack);

        // Maximum
        if (level > maxLevelSq) {
            maxLevelSq = level;
            maxLevelIdx = i;
        }
    }

    // Exit if the signal is zero
    if (maxLevelSq == 0)
        return 0;

    // Pass through the sustained portion of the note. This time apply compession.
    level = maxLevelSq;
    float gainLin = 1;
    const float maxDb = linSq2db(maxLevelSq);
    for (i = maxLevelIdx; i < attackLength; i++) {
        // Level detection
        level = update_level(buffer[i], level, a0Attack);

        // Get the gain
        const float currentDb = linSq2db(level);
        const float gainDb = (maxDb - currentDb) * compressionFactor;
        gainLin = db2lin(gainDb);

        // Apply the gain
        buffer[i] *= gainLin;
    }

    // Pass through the release stage. Keep the gain constant.
    for (i = attackLength; i < bufferLength; i++) {
        buffer[i] *= gainLin;
    }

    return 0;
}

// Ramp down the audio in the given buffer. The gain reaches the given number of decibels
// by the end of the buffer. Positive or negative values of dB are interpreted the same.
static void rampDown(float *const buffer, const size_t bufferLength, const double dB) {

    size_t i;

    // Take the negative absolute value of dB, to avoid ambiguity
    const double dbGain = -fabs(dB);

    // Convert the dB to a time constant
    const double linGain = pow(10, dbGain / 20);
    const float tau = (float) (-log(linGain) / (double) bufferLength);

    // Process the sound in-place
    for (i = 0; i < bufferLength; i++) {
        buffer[i] = buffer[i] * expf(-tau * (float) i);
    }
}

// Helper to render a specific number of samples to the buffer. Returns the new end of the buffer,
// or NULL on failure. In actuality, finishes the last buffer after numSamples
static float *renderSamples(const int numSamples, float *buffer) {



    // Render samples
    if (fluid_synth_write_float(fluidSynth, numSamples, buffer, 0, 2, buffer, 1, 2)) {
        LOG_E(LOG_TAG, "Fluid render failed");
        return NULL;
    }

    return buffer + getNumPcm(numSamples);
}

// Return the size of the recording in frames that would be rendered from the settings.
static size_t getRenderFrames(const struct sound_settings settings) {
    return ms2Samples(settings.recordingDurationMs);
}

// Render the data offline, then start looping it. buffer must be large enough to hold
// the number of frames returned by get_render_frames().
static int render(const struct sound_settings settings, float *const buffer) {

    float *noteEndPosition, *decayEndPosition;
    int i;

    // Shortcuts
    const jbyte *const pitches = settings.pitches;
    const long noteDurationMs = settings.noteDurationMs;
    const long recordingDurationMs = settings.recordingDurationMs;
    const int numPitches = settings.numPitches;
    const uint8_t velocity = settings.velocity;

    // Internal parameters
    const long rampDownMs = 15; // Time for the ramp-down of a note
    const double rampDb = -40; // Amount of ramping down
    const double minLevel = rampDb; // The sound level corresponding to zero velocity

    // Verify parameters
    if (settings.recordingDurationMs < settings.noteDurationMs) {
        LOG_E(LOG_TAG, "Recording duration less than note duration.");
        return -1;
    }
    if (settings.numPitches < 1) {
        LOG_E(LOG_TAG, "Invalid number of pitches: %d", settings.numPitches);
        return -1;
    }
    if (settings.numPitches > maxVoices) {
        LOG_E(LOG_TAG, "Too many pitches: %d (max: %d)", settings.numPitches, maxVoices);
        return -1;
    }
    if (settings.velocity > velocityMax) {
        LOG_E(LOG_TAG, "Velocity %d exceeds maximum value of %d", settings.velocity, velocityMax);
        return -1;
    }

    // Verify initialization
    if (!isInitialized("render"))
        return -1;

    // Get the range of allowable pitches
    const int keyMin = getProgramKeyMin();
    const int keyMax = getProgramKeyMax();
    if (keyMin < 0 || keyMax < 0 || keyMin > keyMax) {
        LOG_E(LOG_TAG, "Failed to retrieve the pitch range for the current program.");
        return -1;
    }

    // Verify the provided pitches work for the current program
    for (i = 0; i < numPitches; i++) {
        const uint8_t pitch = (uint8_t) pitches[i];

        if (pitch < keyMin || pitch > keyMax) {
            LOG_E(LOG_TAG, "Key %u is outside the range [%d, %d] of the current program.", pitch,
                  keyMin, keyMax);
            return -1;
        }
    }

    // Compute the recording lengths
    const int noteSamples = ms2Samples(noteDurationMs);
    const int recordingSamples = getRenderFrames(settings);
    const int decaySamples = recordingSamples - noteSamples;

    //------------------- RENDERING -------------------//

    // Mute all previous sounds
    if (muteSounds()) {
        LOG_E(LOG_TAG, "Failed to mute previous sounds.");
        return -1;
    }

    // Change the reverb settings
   if (setReverbPreset(settings.reverbPreset)) {
       LOG_E(LOG_TAG, "Error setting reverb preset %d", settings.reverbPreset);
       return -1;
   }

    // Send the note start messages
    for (i = 0; i < numPitches; i++) {
        const uint8_t pitch = (uint8_t) pitches[i];
        if (startNote(pitch, velocity)) {
            LOG_E(LOG_TAG, "Failed to start note (key %d velocity %d)", pitch, velocity);
            return -1;
        }
    }

    // Render the note attacks and sustains
    if ((noteEndPosition = renderSamples(noteSamples, buffer)) == NULL) {
        LOG_E(LOG_TAG, "Failed primary phase render");
        return -1;
    }

    // Send the note end messages
    for (i = 0; i < numPitches; i++) {
        const uint8_t pitch = (uint8_t) pitches[i];
        switch (endNote(pitch)) {
            case 0:
                break;
            case 1:
                LOG_W(LOG_TAG, "Failed to end the note at duration %d (program %d)",
                      (int) noteDurationMs, get_program());
                break;
            default:
                LOG_E(LOG_TAG, "Critical error ending note %d", i);
                return -1;
        }
    }

    // Render the note decays
    if ((decayEndPosition = renderSamples(decaySamples, noteEndPosition)) == NULL) {
        LOG_E(LOG_TAG, "Failed release phase render");
        return -1;
    }

    // Get the minimum pitch which is used
    uint8_t minPitch = UCHAR_MAX;
    for (i = 0; i < numPitches; i++) {
        const uint8_t pitch = (uint8_t) pitches[i];
        minPitch = MIN(pitch, minPitch);
    }

    // Optionally apply velocity-dependent DNR compression
    const size_t recordingLength = getNumPcm((size_t) recordingSamples);
    if (settings.volumeBoost) {
        const double minFrequency = pitch2frequency(minPitch);
        if (compressDNR(buffer, recordingLength, noteSamples, velocity, minFrequency)) {
            LOG_E(LOG_TAG, "Failed to apply DNR compression.");
            return -1;
        }
    }

    // Ramp down the audio at the end of the recording
    const size_t rampDownNumPcm = getNumPcm(ms2Samples(
            rampDownMs > noteDurationMs ? noteDurationMs : rampDownMs));
    float *const rampStartPosition = decayEndPosition - rampDownNumPcm;
    rampDown(rampStartPosition, rampDownNumPcm, rampDb);

    // Compute the maximum level based on the velocity
    const double maxLevel = (double) velocity / (double) velocityMax;

    // Normalize the audio and convert to the final recording representation
    if (normalize(buffer, recordingLength, maxLevel)) {
        LOG_E(LOG_TAG, "Failed normalization.");
        return -1;
    }

    return 0;
}

// Shut down fluid synth
static void shutdownFluid(void) {
    if (fluidSynth != NULL) {
        delete_fluid_synth(fluidSynth);
        fluidSynth = NULL;
    }
    if (fluidSettings != NULL) {
        delete_fluid_settings(fluidSettings);
    }
}

// Initialize the fluid synthesizer
static int initFluid(const int sampleRate) {

    // Destroy existing instances
    shutdownFluid();

    // Create the settings
    if ((fluidSettings = new_fluid_settings()) == NULL) {
        LOG_E(LOG_TAG, "Failed to create fluid settings");
        return -1;
    };

    // Configure the settings
    fluid_settings_setint(fluidSettings, "synth.polyphony", maxVoices);
    const int numStereoChannels = numChannels / 2;
    fluid_settings_setint(fluidSettings, "synth.audio-channels", numStereoChannels);
    fluid_settings_setnum(fluidSettings, "synth.sample-rate", sampleRate);
    fluid_settings_setint(fluidSettings, "synth.threadsafe-api", 0); // Turn off monitor

    // Initialize the synthesizer
    if ((fluidSynth = new_fluid_synth(fluidSettings)) == NULL) {
        LOG_E(LOG_TAG, "Failed to initialize fluid synthesizer");
        shutdownFluid();
        return -1;
    }

    return 0;
}

// Load a soundfont. Unloads whichever is currently loaded.
static int load_soundfont(const char *soundfontFilename) {

    if (!isInitialized("load_soundfont")) {
        return -1;
    }

    // Unload the current soundfont, if any
    if (soundfontId >= 0) {
        const int reset_presets = 1;
        if (fluid_synth_sfunload(fluidSynth, soundfontId, reset_presets)) {
            LOG_E(LOG_TAG, "Failed to unload soundfont ID %d", soundfontId);
            return -1;
        }
        soundfontId = -1;
    }

    // Load the soundfont
    soundfontId = fluid_synth_sfload(fluidSynth, soundfontFilename, 1);
    if (soundfontId < 0) {
        LOG_E(LOG_TAG, "Failed to load soundfont %s", soundfontFilename);
        return -1;
    }

    return 0;
}

/*
 * Choose a reverb preset. Preset 0 disables the reverb.
 *
 * Returns 0 on success, 1 if the preset doesn't exist, -1 on error.
 */
static int setReverbPreset(const int preset) {

    if (!isInitialized("set_reverb_preset")) {
        return -1;
    }

    // Check if the preset exists
    if (preset < 0 || preset > numReverbPresets) {
        LOG_W(LOG_TAG, "Preset does not exist: %d", preset);
        return 1;
    }

    // Preset zero disables the reverb
    if (preset == 0) {
        fluid_synth_set_reverb_on(fluidSynth, 0);
        return 0;
    }

    // Get the preset, offset by 1
    struct reverb_settings reverb = reverb_presets[preset - 1];

    // Enable reverb and change the settings
    fluid_synth_set_reverb_on(fluidSynth, 1);
    fluid_synth_set_reverb(fluidSynth, reverb.roomSize, reverb.damping,
            reverb.width, reverb.level);

    return 0;
}

// init midi driver
static int midi_init(const int deviceSampleRate) {

    // Save the sound parameters
    sampleRate = deviceSampleRate;

    // Initialize the synth
    return initFluid(sampleRate);
}

// Main initialization function
static
jboolean
initJNI(JNIEnv *env,
         jobject obj,
         jint deviceSampleRate) {

    // Initialize the synth
    return (midi_init(deviceSampleRate) == 0) ? JNI_TRUE : JNI_FALSE;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_com_bbrister_mididriver_MidiDriver_A(JNIEnv *env,
                                          jobject obj,
                                          jint deviceSampleRate) {
    return initJNI(env, obj, deviceSampleRate);
}

// Get the maximum number of concurrent voices
static
jint
getMaxVoicesJNI(void) {
    return maxVoices;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint
Java_com_bbrister_mididriver_MidiDriver_B(JNIEnv *env, jobject jobj) {
    return getMaxVoicesJNI();
}

// Get the minimum allowed key for the currently selected program
static
jint
getProgramKeyMinJNI(void) {
    return getProgramKeyMin();
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint Java_com_bbrister_mididriver_MidiDriver_D(JNIEnv *env,
                                                            jobject jobj) {
    return getProgramKeyMinJNI();
}

// Get the maximum allowed key for the currently selected program
static
jint
getProgramKeyMaxJNI(void) {
    return getProgramKeyMax();
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint Java_com_bbrister_mididriver_MidiDriver_E(JNIEnv *env,
                                                            jobject jobj) {
    return getProgramKeyMaxJNI();
}

// Render and then start looping
static
jfloatArray
renderJNI(JNIEnv *env,
          jobject obj,
          jbyteArray pitches,
          jlong noteDurationMs,
          jlong recordingDurationMs,
          jint reverbPreset,
          jbyte velocity,
          jboolean volumeBoost) {

    struct sound_settings settings;
    jboolean isCopy;

    // Get the primitive data
    settings.noteDurationMs = (long) noteDurationMs;
    settings.recordingDurationMs = (long) recordingDurationMs;
    settings.velocity = (uint8_t) velocity;
    settings.volumeBoost = (volumeBoost == JNI_TRUE);
    settings.reverbPreset = (int) reverbPreset;

    // Get the pitch array data
    settings.pitches = (*env)->GetByteArrayElements(env, pitches, &isCopy);
    settings.numPitches = (int) (*env)->GetArrayLength(env, pitches);

    // Get the required recording size
    const size_t renderFloats = getNumPcm(getRenderFrames(settings));

    // Create a java array to hold the recording
    jfloatArray jRecording = (*env)->NewFloatArray(env, renderFloats);
    jfloat *const jData = (*env)->GetFloatArrayElements(env, jRecording, &isCopy);

    // Render
    assert(sizeof(jfloat) == sizeof(float));
    const int result = render(settings, jData);

    // Release the output array (possibly) copy, writing back changes
    (*env)->ReleaseFloatArrayElements(env, jRecording, jData, 0);

    // Release the input arrays, without writing back changes
    (*env)->ReleaseByteArrayElements(env, pitches, settings.pitches, JNI_ABORT);

    // Return the output array, or NULL (will be GC'ed) on failure
    return result ? NULL : jRecording;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jfloatArray
Java_com_bbrister_mididriver_MidiDriver_F(JNIEnv *env,
                                               jobject obj,
                                               jbyteArray pitches,
                                               jlong noteDurationMs,
                                               jlong recordingDurationMs,
                                               jint reverbPreset,
                                               jbyte velocity,
                                               jboolean volumeBoost) {
    return renderJNI(
            env,
            obj,
            pitches,
            noteDurationMs,
            recordingDurationMs,
            reverbPreset,
            velocity,
            volumeBoost);
}

// Query if a program number is valid in the given soundfont. Returns 1 if valid, 0 if invalid, -1
// on error.
static
jint
queryProgramJNI(jbyte programNum) {
    int isAvailable;
    return (queryProgram(programNum, &isAvailable) == 0) ? isAvailable : -1;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint
Java_com_bbrister_mididriver_MidiDriver_G(JNIEnv *env,
                                               jobject obj,
                                               jbyte programNum) {
    return queryProgramJNI(programNum);
}

// Get the name of a program, given the program number. Returns an empty string on error.
static
jstring
getProgramNameJNI(JNIEnv *env,
                  jobject obj,
                  jbyte programNum) {
    const char *const name = getProgramName(programNum);
    return (*env)->NewStringUTF(env, name == NULL ? "" : name);
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jstring
Java_com_bbrister_mididriver_MidiDriver_H(JNIEnv *env,
                                               jobject obj,
                                               jbyte programNum) {
    return getProgramNameJNI(env, obj, programNum);
}
// Change the MIDI program
static
jboolean
changeProgramJNI(jbyte programNum) {
    return (changeProgram(programNum) == 0) ? JNI_TRUE : JNI_FALSE;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_com_bbrister_mididriver_MidiDriver_I(JNIEnv *env,
                                               jobject obj,
                                               jbyte programNum) {
    return changeProgramJNI(programNum);
}

// Get the current MIDI program number
static int get_program(void) {

    unsigned int soundfontReturn, bankReturn, programReturn;

    if (!isInitialized("get_program"))
        return -1;

    return fluid_synth_get_program(fluidSynth, midiChannel, &soundfontReturn, &bankReturn,
            &programReturn) ? -1 : programReturn;
}

// Get the current MIDI program, JNI wrapper
static
jint
getProgramJNI(void) {
    return get_program();
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint
Java_com_bbrister_mididriver_MidiDriver_J(JNIEnv *env,
                                               jobject obj) {
    return getProgramJNI();
}

// Shut down the library, freeing all resources
static
void shutdownJNI(JNIEnv *env) {
    shutdownFluid();
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_com_bbrister_mididriver_MidiDriver_K(JNIEnv *env,
                                               jobject obj) {
    shutdownJNI(env);
    return JNI_TRUE;
}

// Change the reverb settings
static
jint getNumReverbPresetsJNI(void) {
    return (jint) numReverbPresets;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint
Java_com_bbrister_mididriver_MidiDriver_L(JNIEnv *env,
                                               jobject obj) {
    return getNumReverbPresetsJNI();
}

// Load a soundfont
static
jboolean
loadSoundfontJNI(JNIEnv *env,
                 jobject obj,
                 jobject AAssetAdapter,
                 jstring soundfontAAssetName) {

    // Initialize the AAssets wrapper, so we can do file I/O
    if (init_AAssets(env, AAssetAdapter)) {
        LOG_E(LOG_TAG, "Failed to initialize AAssets.");
        return JNI_FALSE;
    }

    // Convert Java arguments
    const char *const soundfontName = (*env)->GetStringUTFChars(env, soundfontAAssetName, NULL);

    // Initialize the synth
    int result = load_soundfont(soundfontName);

    // Release Java arguments
    (*env)->ReleaseStringUTFChars(env, soundfontAAssetName, soundfontName);

    // Release AAssets
    release_AAssets(env);

    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_com_bbrister_mididriver_MidiDriver_M(JNIEnv *env,
                                          jobject obj,
                                          jobject AAssetAdapter,
                                          jstring soundfontAAssetName) {
    return loadSoundfontJNI(env, obj, AAssetAdapter, soundfontAAssetName);
}

#ifdef __cplusplus
}
#endif