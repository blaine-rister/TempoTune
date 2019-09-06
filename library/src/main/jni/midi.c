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
#include <semaphore.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>

#include <android/log.h>

#include <fluidlite.h>

// for native audio
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include "midi.h"
#include "../../../../../../AppData/Local/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/include/jni.h"

#ifdef __cplusplus
extern "C" {
#endif

#define LOG_TAG "MidiDriver"

// Logging macros
#ifdef NDEBUG
#define LOG(...) ;
#else
#define LOG __android_log_print
#endif
#define LOG_D(tag, ...) LOG(ANDROID_LOG_DEBUG, tag, __VA_ARGS__)
#define LOG_E(tag, ...) LOG(ANDROID_LOG_ERROR, tag, __VA_ARGS__)
#define LOG_I(tag, ...) LOG(ANDROID_LOG_INFO, tag, __VA_ARGS__)
#define LOG_W(tag, ...) LOG(ANDROID_LOG_WARN, tag, __VA_ARGS__)

// Math macros
#define MAX(x, y) ((x) > (y) ? (x) : (y))
#define MIN(x, y) ((x) < (y) ? (x) : (y))

// Internal fluid functions which are not in the public header
fluid_preset_t* fluid_synth_find_preset(fluid_synth_t* synth,
                                        unsigned int banknum,
                                        unsigned int prognum);
int fluid_synth_all_sounds_off(fluid_synth_t* synth, int chan);

// Internal functions
static int get_program(void);
static size_t ms2Samples(const size_t ms);
int delete_recording(void);

// Output audio datatype
typedef int16_t output_t;

// Struct to hold sound synthesis parameters
struct sound_settings {
    jbyte *pitches;
    long noteDurationMs;
    long recordingDurationMs;
    int numPitches;
    int volumeBoost;
    uint8_t velocity;
};

// MIDI info
const uint8_t velocityMax = 127; // Maximum allowed velocity in MIDI

// Constants
static const int midiChannel = 0;
static const int sfBank = 0;
static const int maxVoices = 8;
static const int numChannels = 2; // Stereo
static const int pauseDurationMs = 10;

// Sound parameters
int sampleRate;
int bufferSizeMono;

// semaphores
static sem_t is_idle;

// engine interfaces
static SLObjectItf engineObject = NULL;
static SLEngineItf engineEngine;

// output mix interfaces
static SLObjectItf outputMixObject = NULL;

// buffer queue player interfaces
static SLObjectItf bqPlayerObject = NULL;
static SLPlayItf bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;

// Fluid data
static fluid_synth_t *fluidSynth = NULL;
static fluid_settings_t *fluidSettings = NULL;

// Recording buffer
static enum State {
    PLAYING, STOPPING, IDLE
} state = IDLE;
static output_t *record_buffer = NULL; // Storage for the recording
static output_t *recording_position = NULL; // Current recording position
static output_t *playback_position = NULL; // Current playback position

// State for pausing the sound
static size_t pause_count; // Counts down to zero
static float pause_factor; // Ramp slope

// Function declarations
void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context);

// Check if the library is initialized. If not, return JNI_FALSE and print a message.
jboolean isInitialized(const char *functionName) {
    if (fluidSynth == NULL) {
        LOG_E(LOG_TAG, "Must initialize fluid before calling %s", functionName);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

// Computes the number of output_t elements needed to store a given number of samples. This depends
// on how many channels we have.
static size_t getNumPcm(const size_t numSamples) {
    return numSamples * numChannels;
}

// Set the player's state to playing
SLresult play(void) {

    SLresult result;

    // Set the playback pointer
    if (record_buffer == NULL) {
        LOG_E(LOG_TAG, "playback failed: no recording stored");
        return SL_RESULT_OPERATION_ABORTED;
    }
    playback_position = record_buffer;

    // Set the state to playing
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
    if (SL_RESULT_SUCCESS != result)
        return result;

    state = PLAYING;

    // call the callback to start playing
    bqPlayerCallback(bqPlayerBufferQueue, NULL);

    return SL_RESULT_SUCCESS;
}

// Set the player's state to paused. If currently playing, waits for the buffer to empty.
int idle(void) {

    // Do nothing if we're already idle
    if (state == IDLE)
        return 0;

    // Initialize the pausing parameters
    pause_count = ms2Samples(pauseDurationMs);
    pause_factor = 1.F / (float) pause_count;

    // Initiate the pausing phase
    state = STOPPING;

    // Block until we have confirmation that there are no more callbacks
    sem_wait(&is_idle);
    state = IDLE;

    // Tell OpenSL ES to stop playing sound. Note: this is non-blocking
    if ((*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED) != SL_RESULT_SUCCESS)
        return -1;

    // Delete the existing sound. It's corrupted by the pausing phase. Note: this will call idle()
    // itself so make sure state = IDLE before calling this.
    return delete_recording();
}


void enqueueBuffer(const output_t *const data) {

    SLresult result;

    result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, data,
            getNumPcm(bufferSizeMono) * sizeof(output_t));
    switch (result) {
        case SL_RESULT_SUCCESS:
        case SL_RESULT_OPERATION_ABORTED:
            return;
        default:
            /* Could get SL_RESULT_BUFFER_INSUFFICIENT (code 7) if the buffer is full. This
            * shouldn't happen because we are supposed to wait for the buffer to clear before
            * starting a new render. */
            LOG_E(LOG_TAG, "Error code from OpenSL ES enqueue buffer: %d", result);
            assert(0);
    }
    // the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
    // which for this code example would indicate a programming error
    assert(SL_RESULT_SUCCESS == result);

}

// this callback handler is called every time a buffer finishes
// playing
void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {

    int i, j;

    const size_t bufferSizePcm = getNumPcm(bufferSizeMono);

    assert(bq == bqPlayerBufferQueue);
    assert(NULL == context);

    // Update the playback position circularly
    playback_position += bufferSizePcm;
    if (playback_position >= recording_position) {
        playback_position = record_buffer + (playback_position - recording_position);
    }

    switch (state) {
        case IDLE:
            // This shouldn't happen
            assert(0);
        case STOPPING:
            // Quit playing once the ramp is done
            if (pause_count <= 0) {
                sem_post(&is_idle);
                return;
            }

            // Apply a linear ramp to the audio signal. Assumes interleaved channels
            for (i = 0; i < bufferSizeMono; i++) {
                const float rampFactor = pause_count > 0 ? (float) pause_count-- * pause_factor : 0;
                for (j = 0; j < numChannels; j++) {
                    const int sampleIdx = getNumPcm(i) + j;
                    const float sample = playback_position[sampleIdx];
                    const float ramped = sample * rampFactor;
                    playback_position[sampleIdx] = (output_t) ramped;
                }
            }

            // Falls through to playback
        case PLAYING:
            // Enqueue playback of a buffer's portion of the recording
            enqueueBuffer(playback_position);
            return;
    }
}

// Computes the number of samples needed. Does not take into account the number of channels
static size_t ms2Samples(const size_t ms) {
    const size_t msPerSecond = 1000;
    return (ms * (size_t) sampleRate) / msPerSecond;
}

// Deletes the existing recording and cancels playback
int delete_recording(void) {

    // Transition to idle state, wait until playback has ended
    if (idle())
        return -1;

    // Sweep up unused memory
    if (record_buffer != NULL) {
        free(record_buffer);
        record_buffer = NULL;
    }

    return 0;
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

// Return a uniformly distributed R.V. in the range [0,1]
static float uniform(void) {
    return ((float) rand()) / (float) RAND_MAX;
}

// Return triangularly-distributed dither in the range [-1, 1].
static float dither(void) {
    return (uniform() + uniform()) / 2;
}

// Normalize the audio so the maximum value is given by maxLevel, on a scale of 0-1. This is the
// final processing stage so it also converts the audio to fixed-point at the end.
static int normalize(const float *const inBuffer, output_t *const outBuffer,
                            const size_t bufferLength, const double maxLevel) {

    float maxBefore;
    size_t i;

    if (maxLevel < 0. || maxLevel > 1.) {
        LOG_E(LOG_TAG, "normalize: invalid maxLevel: %f", maxLevel);
        return -1;
    }

    assert(sizeof(short) == sizeof(output_t));
    const double maxPcm = (double) SHRT_MAX * maxLevel;

    // Get the maximum value of the un-normalized stream
    maxBefore = 0;
    for (i = 0; i < bufferLength; i++) {
        const float sampleLevel = fabsf(inBuffer[i]);
        maxBefore = sampleLevel > maxBefore ? sampleLevel : maxBefore;
    }

    // Compute the linear gain
    const float gain = (float) (maxPcm / (double) maxBefore);

    // Apply the gain and convert to fixed-point
    for (i = 0; i < bufferLength; i++) {
        // Apply gain and dither
        const float dithered = inBuffer[i] * gain + dither();

        // Convert with truncation to avoid overflow
        outBuffer[i] = (output_t) MIN(MAX(dithered, (float) SHRT_MIN), (float) SHRT_MAX);
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
    const double minCompressionRatio = 2.0; // This is reached at maximum velocity
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

// Render the data offline, then start looping it
int render(const struct sound_settings settings) {

    float *noteEndPosition, *decayEndPosition, *floatBuffer = NULL;
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
        goto render_quit;
    }
    if (settings.numPitches < 1) {
        LOG_E(LOG_TAG, "Invalid number of pitches: %d", settings.numPitches);
        goto render_quit;
    }
    if (settings.numPitches > maxVoices) {
        LOG_E(LOG_TAG, "Too many pitches: %d (max: %d)", settings.numPitches, maxVoices);
        goto render_quit;
    }
    if (settings.velocity > velocityMax) {
        LOG_E(LOG_TAG, "Velocity %d exceeds maximum value of %d", settings.velocity, velocityMax);
        goto render_quit;
    }

    // Verify initialization
    if (!isInitialized("render"))
        goto render_quit;

    // Get the range of allowable pitches
    const int keyMin = getProgramKeyMin();
    const int keyMax = getProgramKeyMax();
    if (keyMin < 0 || keyMax < 0 || keyMin > keyMax) {
        LOG_E(LOG_TAG, "Failed to retrieve the pitch range for the current program.");
        goto render_quit;
    }

    // Verify the provided pitches work for the current program
    for (i = 0; i < numPitches; i++) {
        const uint8_t pitch = (uint8_t) pitches[i];

        if (pitch < keyMin || pitch > keyMax) {
            LOG_E(LOG_TAG, "Key %u is outside the range [%d, %d] of the current program.", pitch,
                  keyMin, keyMax);
            goto render_quit;
        }
    }

    // Compute the recording lengths
    const int noteSamples = ms2Samples(noteDurationMs);
    const int recordingSamples = ms2Samples(recordingDurationMs);
    const int decaySamples = recordingSamples - noteSamples;

    //------------------- RENDERING -------------------//

    // Allocate a buffer for the internal floating-point representation
    const size_t recordingLength = getNumPcm(recordingSamples);
    if ((floatBuffer = malloc(recordingLength * sizeof(float))) == NULL) {
        LOG_E(LOG_TAG, "Insufficient memory for float buffer.");
        goto render_quit;
    }

    // Mute all previous sounds
    if (muteSounds()) {
        LOG_E(LOG_TAG, "Failed to mute previous sounds.");
        goto render_quit;
    }

    // Send the note start messages
    for (i = 0; i < numPitches; i++) {
        const uint8_t pitch = (uint8_t) pitches[i];
        if (startNote(pitch, velocity)) {
            LOG_E(LOG_TAG, "Failed to start note (key %d velocity %d)", pitch, velocity);
            goto render_quit;
        }
    }

    // Render the note attacks and sustains
    if ((noteEndPosition = renderSamples(noteSamples, floatBuffer)) == NULL) {
        LOG_E(LOG_TAG, "Failed primary phase render");
        goto render_quit;
    }

    // Send the note end messages
    for (i = 0; i < numPitches; i++) {
        switch (endNote(pitches[i])) {
            case 0:
                break;
            case 1:
                LOG_W(LOG_TAG, "Failed to end the note at duration %d (program %d)",
                      (int) noteDurationMs, get_program());
                break;
            default:
                LOG_E(LOG_TAG, "Critical error ending note %d", i);
                goto render_quit;
        }
    }

    // Render the note decays
    if ((decayEndPosition = renderSamples(decaySamples, noteEndPosition)) == NULL) {
        LOG_E(LOG_TAG, "Failed release phase render");
        goto render_quit;
    }

    // Get the minimum pitch which is used
    uint8_t minPitch = UCHAR_MAX;
    for (i = 0; i < numPitches; i++) {
        minPitch = MIN(pitches[i], minPitch);
    }

    // Optionally apply velocity-dependent DNR compression
    if (settings.volumeBoost) {
        const double minFrequency = pitch2frequency(minPitch);
        if (compressDNR(floatBuffer, recordingLength, noteSamples, velocity, minFrequency)) {
            LOG_E(LOG_TAG, "Failed to apply DNR compression.");
            goto render_quit;
        }
    }

    // Ramp down the audio at the end of the recording
    const size_t rampDownNumPcm = getNumPcm(ms2Samples(
            rampDownMs > noteDurationMs ? noteDurationMs : rampDownMs));
    float *const rampStartPosition = decayEndPosition - rampDownNumPcm;
    rampDown(rampStartPosition, rampDownNumPcm, rampDb);

    //------------------- CONVERSION TO FINAL RECORDING -------------------//

    // Free the old recording
    if (delete_recording() != SL_RESULT_SUCCESS) {
        LOG_E(LOG_TAG, "Failed to delete previous recording.");
        goto render_quit;
    }
    assert(record_buffer == NULL); // Should be freed by now
    assert(state == IDLE); // Shouldn't be playing anything

    // Allocate a new recording. Add an extra buffer at the end, to imitate looping behavior
    const size_t bufferLength = getNumPcm(recordingSamples + bufferSizeMono);
    if ((record_buffer = (output_t *) malloc(bufferLength * sizeof(output_t))) == NULL) {
        LOG_E(LOG_TAG, "Insufficient memory for recording buffer.");
        goto render_quit;
    }

    // Compute the maximum level based on the velocity
    const double maxLevel = (double) velocity / (double) velocityMax;

    // Normalize the audio and convert to the final recording representation
    if (normalize(floatBuffer, record_buffer, recordingLength, maxLevel)) {
        LOG_E(LOG_TAG, "Failed normalization.");
        goto render_quit;
    }

    // Clean up intermediates
    free(floatBuffer);
    floatBuffer = NULL;

    // Set the end of the recording
    recording_position = record_buffer + recordingLength;

    // Configure the loop imitation at the end of the recording. Might theoretically need multiple
    // copies if the buffer size exceeds the recording size
    output_t *copySrcPosition, *copyDstPosition;
    const output_t *const bufferEndPosition = record_buffer + bufferLength;
    for (copySrcPosition = record_buffer, copyDstPosition = recording_position;
        copyDstPosition < bufferEndPosition;
        ) {
        const size_t paddingCopyRemaining = bufferEndPosition - copyDstPosition;
        const size_t amountToCopy = paddingCopyRemaining > recordingLength ? recordingLength :
                paddingCopyRemaining;
        // Copy the recording circularly
        memcpy(copyDstPosition, copySrcPosition, amountToCopy * sizeof(output_t));
        copyDstPosition += amountToCopy;
        copySrcPosition += amountToCopy;
        if (copySrcPosition >= recording_position) {
            assert(copySrcPosition == recording_position);
            copySrcPosition = record_buffer;
        }
    }

    // Start playing the recording
    play();
    return 0;

    render_quit:
    if (floatBuffer != NULL)
        free(floatBuffer);
    return -1;
}

// create the engine and output mix objects
SLresult createEngine() {
    SLresult result;

    // create engine
    result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    if (SL_RESULT_SUCCESS != result)
        return result;

    // LOG_D(LOG_TAG, "Engine created");

    // realize the engine
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result)
        return result;

    // LOG_D(LOG_TAG, "Engine realised");

    // get the engine interface, which is needed in order to create
    // other objects
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE,
                                           &engineEngine);
    if (SL_RESULT_SUCCESS != result)
        return result;

    // LOG_D(LOG_TAG, "Engine Interface retrieved");

    // create output mix
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject,
                                              0, NULL, NULL);
    if (SL_RESULT_SUCCESS != result)
        return result;

    // LOG_D(LOG_TAG, "Output mix created");

    // realize the output mix
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result)
        return result;

    // LOG_D(LOG_TAG, "Output mix realised");

    return SL_RESULT_SUCCESS;
}

// create buffer queue audio player
SLresult createBufferQueueAudioPlayer(void) {

    SLAndroidConfigurationItf configItf;
    SLVolumeItf volumeItf;
    SLresult result;

    // configure audio source
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq =
            {
                    SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2
            };
    SLDataFormat_PCM format_pcm =
            {
                    SL_DATAFORMAT_PCM, (SLuint32)(numChannels),
                    (SLuint32)(sampleRate * 1000),
                    SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                    SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
                    SL_BYTEORDER_LITTLEENDIAN
            };
    SLDataSource audioSrc = {&loc_bufq, &format_pcm};

    // configure audio sink
    SLDataLocator_OutputMix loc_outmix =
            {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&loc_outmix, NULL};

    // create audio player
    const SLInterfaceID ids[] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME, SL_IID_ANDROIDCONFIGURATION};
    const SLboolean req[] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_FALSE, SL_BOOLEAN_FALSE};
    const size_t numIds = sizeof(ids) / sizeof(SLInterfaceID);

    result = (*engineEngine)->CreateAudioPlayer(engineEngine,
                                                &bqPlayerObject,
                                                &audioSrc, &audioSnk,
                                                numIds, ids, req);
    if (SL_RESULT_SUCCESS != result)
        return result;

    LOG_I(LOG_TAG, "Initialized audio player with sample rate: %d buffer size: %d", sampleRate,
          bufferSizeMono);

    // LOG_D(LOG_TAG, "Audio player created");

    // realize the player
    result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result)
        return result;

    // LOG_D(LOG_TAG, "Audio player realised");

    // get the play interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY,
                                             &bqPlayerPlay);
    if (SL_RESULT_SUCCESS != result)
        return result;

    // Get the volume interface, if it exists. If so, set the volume to max.
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &volumeItf);
    if (result == SL_RESULT_SUCCESS) {

        SLmillibel maxVolume;

        // Get the maximum volume level
        result = (*volumeItf)->GetMaxVolumeLevel(volumeItf, &maxVolume);
        if (result != SL_RESULT_SUCCESS) {
            LOG_E(LOG_TAG, "failed to get the maximum volume");
            return result;
        }

        // Set the volume to max
        result = (*volumeItf)->SetVolumeLevel(volumeItf, maxVolume);
        if (result != SL_RESULT_SUCCESS) {
            LOG_E(LOG_TAG, "failed to set the volume level");
            return result;
        }

        LOG_I(LOG_TAG, "Set volume level to the maximum.");

    } else {
        LOG_W(LOG_TAG, "Failed to get the volume controls (code %d). The app will not control the "
                       "OpenSL ES player volume.", result);
    }

#if 0
    // Get the Android configuration interface, if it exists. If so, enable low power mode. This
    // will fail on Android versions prior to API level 25
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_ANDROIDCONFIGURATION,
                                             &configItf);
    if (result == SL_RESULT_SUCCESS) {

        const char *modeStr;
        SLuint32 defaultMode;

        const SLuint32 desiredMode = SL_ANDROID_PERFORMANCE_POWER_SAVING;

        // Get the current (default) performance mode
        (*configItf)->GetConfiguration(configItf, SL_ANDROID_KEY_PERFORMANCE_MODE,
                                       &defaultMode, sizeof(defaultMode));
        switch (defaultMode) {
            case SL_ANDROID_PERFORMANCE_NONE:
                modeStr = "none";
            case SL_ANDROID_PERFORMANCE_LATENCY:
                modeStr = "latency";
            case SL_ANDROID_PERFORMANCE_LATENCY_EFFECTS:
                modeStr = "latency_effects";
            case SL_ANDROID_PERFORMANCE_POWER_SAVING:
                modeStr = "power_saving";
            default:
                modeStr = "unrecognized"; // This happens on API level < 25
        }

        // Set the performance mode.
        result = (*configItf)->SetConfiguration(configItf, SL_ANDROID_KEY_PERFORMANCE_MODE,
                                                &desiredMode, sizeof(desiredMode));
        if (result == SL_RESULT_SUCCESS) {
            LOG_I(LOG_TAG, "Set audio to low-power mode.");
        } else {
            LOG_W(LOG_TAG, "Failed to set audio to low-power mode (code %d).", result);
            LOG_I(LOG_TAG, "Audio defaulted to performance mode: %s", modeStr);
        }
    } else {
        LOG_W(LOG_TAG, "Failed to get the configuration controls (code %d). Low-power audio mode "
                       "will not be enabled.", result);
    }
#endif

    // get the buffer queue interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,
                                             &bqPlayerBufferQueue);
    if (SL_RESULT_SUCCESS != result)
        return result;

    // LOG_D(LOG_TAG, "Buffer queue interface retrieved");

    // register callback on the buffer queue
    result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue,
                                                      bqPlayerCallback, NULL);
    if (SL_RESULT_SUCCESS != result)
        return result;

    // LOG_D(LOG_TAG, "Callback registered");

    // Set play state to paused
    return idle();
    // LOG_D(LOG_TAG, "Audio player set playing");
}

// shut down the native audio system
void shutdownAudio(void) {
    // destroy buffer queue audio player object, and invalidate all
    // associated interfaces
    if (bqPlayerObject != NULL) {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = NULL;
        bqPlayerPlay = NULL;
        bqPlayerBufferQueue = NULL;
    }

    // destroy output mix object, and invalidate all associated interfaces
    if (outputMixObject != NULL) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
    }

    // destroy engine object, and invalidate all associated interfaces
    if (engineObject != NULL) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineEngine = NULL;
    }
}

// Shut down fluid synth
void shutdownFluid(void) {
    if (fluidSynth != NULL) {
        delete_fluid_synth(fluidSynth);
        fluidSynth = NULL;
    }
    if (fluidSettings != NULL) {
        delete_fluid_settings(fluidSettings);
    }
}

// Initialize the fluid synthesizer, using the given soundfont.
int initFluid(const char *soundfontFilename) {

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
        return -1;
    }

    // Load the soundfont
    if (fluid_synth_sfload(fluidSynth, soundfontFilename, 1) < 0) {
        LOG_E(LOG_TAG, "Failed to load soundfont %s", soundfontFilename);
        return -1;
    }

    return 0;
}

// init midi driver
jboolean midi_init(const char *soundfontFilename, const int deviceSampleRate,
        const int deviceBufferSizeMono) {
    int result;

    // Save the sound parameters
    sampleRate = deviceSampleRate;
    bufferSizeMono = deviceBufferSizeMono;

    // Initialize the synth
    if ((result = initFluid(soundfontFilename))) {
        shutdownFluid();

        LOG_E(LOG_TAG, "Init fluid failed: %d", result);

        return JNI_FALSE;
    }

    // create the engine and output mix objects
    if ((result = createEngine()) != SL_RESULT_SUCCESS) {
        shutdownFluid();
        shutdownAudio();

        LOG_E(LOG_TAG, "Create engine failed: %d", result);

        return JNI_FALSE;
    }

    // create buffer queue audio player
    if ((result = createBufferQueueAudioPlayer()) != SL_RESULT_SUCCESS) {
        shutdownFluid();
        shutdownAudio();

        LOG_E(LOG_TAG, "Create buffer queue audio player failed: %d", result);

        return JNI_FALSE;
    }

    // Initialize recording state
    state = IDLE;
    record_buffer = NULL;
    recording_position = NULL;
    playback_position = NULL;

    // Initialize semaphor
    const int shared_processes = 0;
    const int sem_initial_value = 0;
    sem_init(&is_idle, shared_processes, sem_initial_value);

    return JNI_TRUE;
}

// Main initialization function
jboolean
initJNI(JNIEnv *env,
         jobject obj,
         jobject AAssetAdapter,
         jstring soundfontAAssetName,
         jint deviceSampleRate,
         jint deviceBufferSize) {

    jboolean result;

    // Initialize the AAssets wrapper, so we can do file I/O
    if (init_AAssets(env, AAssetAdapter)) { LOG_E(LOG_TAG, "Failed to initialize AAssets.");
        return JNI_FALSE;
    }

    // Convert Java arguments
    const char *const soundfontName = (*env)->GetStringUTFChars(env, soundfontAAssetName, NULL);

    // Initialize the synth
    result = midi_init(soundfontName, deviceSampleRate, deviceBufferSize);

    // Release Java arguments
    (*env)->ReleaseStringUTFChars(env, soundfontAAssetName, soundfontName);

    return result;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_A(JNIEnv *env,
                                               jobject obj,
                                               jobject AAssetAdapter,
                                               jstring soundfontAAssetName,
                                               jint deviceSampleRate,
                                               jint deviceBufferSize) {
    return initJNI(env, obj, AAssetAdapter, soundfontAAssetName, deviceSampleRate,
            deviceBufferSize);
}

// Get the maximum number of concurrent voices
jint
getMaxVoicesJNI(void) {
    return maxVoices;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint
Java_org_billthefarmer_mididriver_MidiDriver_B(JNIEnv *env, jobject jobj) {
    return getMaxVoicesJNI();
}

jboolean
pauseJNI(void) {
    return (jboolean) (delete_recording() == SL_RESULT_SUCCESS ? JNI_TRUE : JNI_FALSE);
}

// Stop looping, delete the recording
// Formerly: pauseJNI();
JNIEXPORT
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_C(JNIEnv *env,
                                               jobject jobj) {
   return pauseJNI();
}

// Get the minimum allowed key for the currently selected program
jint
getProgramKeyMinJNI(void) {
    return getProgramKeyMin();
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint Java_org_billthefarmer_mididriver_MidiDriver_D(JNIEnv *env,
                                                            jobject jobj) {
    return getProgramKeyMinJNI();
}

// Get the maximum allowed key for the currently selected program
jint
getProgramKeyMaxJNI(void) {
    return getProgramKeyMax();
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint Java_org_billthefarmer_mididriver_MidiDriver_E(JNIEnv *env,
                                                            jobject jobj) {
    return getProgramKeyMaxJNI();
}

// Render and then start looping
jboolean
renderJNI(JNIEnv *env,
          jobject obj,
          jbyteArray pitches,
          jlong noteDurationMs,
          jlong recordingDurationMs,
          jbyte velocity,
          jboolean volumeBoost) {

    struct sound_settings settings;
    int result;
    jboolean isCopy;

    // Get the primitive data
    settings.noteDurationMs = (long) noteDurationMs;
    settings.recordingDurationMs = (long) recordingDurationMs;
    settings.velocity = (uint8_t) velocity;
    settings.volumeBoost = (volumeBoost == JNI_TRUE);

    // Get the pitch array data
    settings.pitches = (*env)->GetByteArrayElements(env, pitches, &isCopy);
    settings.numPitches = (int) (*env)->GetArrayLength(env, pitches);

    // Render
    result = render(settings);

    // Release arrays
    (*env)->ReleaseByteArrayElements(env, pitches, settings.pitches, 0);

    return (jboolean) (result == 0);
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_F(JNIEnv *env,
                                               jobject obj,
                                               jbyteArray pitches,
                                               jlong noteDurationMs,
                                               jlong recordingDurationMs,
                                               jbyte velocity,
                                               jboolean volumeBoost) {
    return renderJNI(
            env,
            obj,
            pitches,
            noteDurationMs,
            recordingDurationMs,
            velocity,
            volumeBoost);
}

// Query if a program number is valid in the given soundfont. Returns 1 if valid, 0 if invalid, -1
// on error.
jint
queryProgramJNI(jbyte programNum) {
    int isAvailable;
    return (queryProgram(programNum, &isAvailable) == 0) ? isAvailable : -1;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint
Java_org_billthefarmer_mididriver_MidiDriver_G(JNIEnv *env,
                                               jobject obj,
                                               jbyte programNum) {
    return queryProgramJNI(programNum);
}

// Get the name of a program, given the program number. Returns an empty string on error.
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
Java_org_billthefarmer_mididriver_MidiDriver_H(JNIEnv *env,
                                               jobject obj,
                                               jbyte programNum) {
    return getProgramNameJNI(env, obj, programNum);
}
// Change the MIDI program
jboolean
changeProgramJNI(jbyte programNum) {
    return (changeProgram(programNum) == 0) ? JNI_TRUE : JNI_FALSE;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_I(JNIEnv *env,
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
jint
getProgramJNI(void) {
    return get_program();
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jint
Java_org_billthefarmer_mididriver_MidiDriver_J(JNIEnv *env,
                                               jobject obj) {
    return getProgramJNI();
}


// shutdown midi
jboolean midi_shutdown() {
    int result;

    shutdownAudio();
    shutdownFluid();

    if (delete_recording() != SL_RESULT_SUCCESS)
        return JNI_FALSE;
    sem_destroy(&is_idle);

    return JNI_TRUE;
}

// Shut down the library, freeing all resources
jboolean shutdownJNI(JNIEnv *env) {
    // Delete the synth
    if (midi_shutdown() != JNI_TRUE)
        return JNI_FALSE;

    // Release AAssets, enabling garbage collection
    release_AAssets(env);

    return JNI_TRUE;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_K(JNIEnv *env,
                                               jobject obj) {
    return shutdownJNI(env);
}

// Set the reverb room size
jboolean setReverbRoomSizeJNI(const jdouble roomSize) {
//    return setReverbRoomSize((double) roomSize); //TODO implement this
return JNI_FALSE; //XXX
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_L(JNIEnv *env,
                                               jobject obj,
                                               jdouble roomSize) {
    return setReverbRoomSizeJNI(roomSize);
}

#ifdef __cplusplus
}
#endif