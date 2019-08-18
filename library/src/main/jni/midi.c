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

#include <android/log.h>

#include <fluidlite.h>

// for native audio
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

// for EAS midi
#include "eas.h"
#include "eas_reverb.h"

#include "org_billthefarmer_mididriver_MidiDriver.h"
#include "midi.h"

#ifdef __cplusplus
extern "C" {
#endif

#define LOG_TAG "MidiDriver"

#define LOG_D(tag, ...) __android_log_print(ANDROID_LOG_DEBUG, tag, __VA_ARGS__)
#define LOG_E(tag, ...) __android_log_print(ANDROID_LOG_ERROR, tag, __VA_ARGS__)
#define LOG_I(tag, ...) __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)

// determines how many EAS buffers to fill a host buffer
#define NUM_BUFFERS 4

// Constants
const int midiChannel = 0;

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
static SLVolumeItf bqPlayerVolume;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;

// Fluid data
fluid_synth_t *fluidSynth = NULL;
fluid_settings_t *fluidSettings = NULL;
int soundFont = -1;

// EAS data
static EAS_DATA_HANDLE pEASData;
const S_EAS_LIB_CONFIG *pLibConfig;
static EAS_PCM *buffer;
static EAS_I32 bufferSize;
static EAS_HANDLE midiHandle;

// Recording buffer
static enum State {
    PLAYING, STOPPING, IDLE
} state = IDLE;
static EAS_PCM *record_buffer = NULL; // Storage for the recording
static EAS_PCM *recording_position = NULL; // Current recording position
static EAS_PCM *playback_position = NULL; // Current playback position

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

// Set the player's state to playing
SLresult play() {

    SLmillibel maxVolume;
    SLresult result;

    // Get the maximum volume level
    result = (*bqPlayerVolume)->GetMaxVolumeLevel(bqPlayerVolume, &maxVolume);
    if (result != SL_RESULT_SUCCESS) {
        LOG_E(LOG_TAG, "failed to get the maximum volume");
        return result;
    }

    // Set the volume to max
    result = (*bqPlayerVolume)->SetVolumeLevel(bqPlayerVolume, maxVolume);
    if (result != SL_RESULT_SUCCESS) {
        LOG_E(LOG_TAG, "failed to set the volume level");
        return result;
    }

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
SLresult idle(void) {

    // Do nothing if we're already idle
    if (state == IDLE)
        return SL_RESULT_SUCCESS;

    // Stop playing
    state = STOPPING;

    // Block until we have confirmation that there are no more callbacks
    sem_wait(&is_idle);
    state = IDLE;

    // Tell OpenSL ES to stop playing sound. Note: this is non-blocking
    return (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED);
}


void enqueueBuffer(SLAndroidSimpleBufferQueueItf bq) {

    SLresult result;

    result = (*bqPlayerBufferQueue)->Enqueue(bq, buffer, bufferSize * sizeof(EAS_PCM));
    switch (result) {
        case SL_RESULT_SUCCESS:
            return;
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
    int i;

    EAS_PCM *next_buffer;

    EAS_RESULT result;
    EAS_I32 numGenerated;
    EAS_I32 count;

    assert(bq == bqPlayerBufferQueue);
    assert(NULL == context);

    switch (state) {
        case IDLE:
            // This shouldn't happen
            assert(0);
        case STOPPING:
            // Tell the main thread that we are done playing
            sem_post(&is_idle);
            return;
        case PLAYING:
            // Read from the recording circularly and write into the buffer
            for (i = 0; i < bufferSize; i++) {
                buffer[i] = *playback_position++;
                if (playback_position == recording_position) {
                    playback_position = record_buffer;
                }
            }

            // Send the buffer
            enqueueBuffer(bq);
            return;
    }
}

// Computes the number of samples needed. Does not take into account the number of channels
static size_t ms2Samples(const size_t ms) {
    const size_t msPerSecond = 1000;
    return (ms * (size_t) pLibConfig->sampleRate) / msPerSecond;
}


// Computes the number of EAS_PCM elements needed to store a given number of samples
static size_t getNumPcm(const EAS_I32 numSamples) {
    return (size_t) numSamples * pLibConfig->numChannels;
}

// Deletes the existing recording and cancels playback
SLresult delete_recording() {

    SLresult result;

    // Transition to idle state, wait until playback has ended
    result = idle();
    if (result != SL_RESULT_SUCCESS)
        return result;

    // Sweep up unused memory
    if (record_buffer != NULL) {
        free(record_buffer);
        record_buffer = NULL;
    }

    return SL_RESULT_SUCCESS;
}

// Change the program
static int changeProgram(const EAS_U8 programNum) {

    int result;

    if (!isInitialized("changeProgram"))
        return -1;

    result = fluid_synth_program_change(fluidSynth, midiChannel, programNum);
    if (result != 0) {
        LOG_E(LOG_TAG, "Failed to change fluid program to %uc.", programNum);
    }

    return result;
}

// Start a note
static EAS_RESULT startNote(const EAS_U8 pitch, const EAS_U8 velocity) {
    return !isInitialized("startNote") || fluid_synth_noteon(fluidSynth, midiChannel, pitch,
                                                             velocity);

}

// End a note
static EAS_RESULT endNote(const EAS_U8 pitch) {
    return !isInitialized("endNote") || fluid_synth_noteoff(fluidSynth, midiChannel, pitch);
}

// Normalize the audio so the maximum value is given by maxLevel, on a scale of 0-1. This is the
// final processing stage so it also converts the audio to fixed-point at the end.
static EAS_RESULT normalize(const float *const inBuffer, EAS_PCM *const outBuffer,
                            const size_t bufferLength, const double maxLevel) {

    float maxBefore;
    size_t i;

    assert(sizeof(short) == sizeof(EAS_PCM));
    if (maxLevel < 0. || maxLevel > 1.) {
        LOG_E(LOG_TAG, "normalize: invalid maxLevel: %f", maxLevel);
        return EAS_FAILURE;
    }

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
        // TODO also perform dithering here
        outBuffer[i] = (EAS_PCM) (inBuffer[i] * gain);
    }

    return EAS_SUCCESS;
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
static float *renderSamples(const EAS_I32 numSamples, float *buffer) {



    // Render samples
    if (fluid_synth_write_float(fluidSynth, numSamples, buffer, 0, 2, buffer, 1, 2)) {
        LOG_E(LOG_TAG, "Fluid render failed");
        return NULL;
    }

    return buffer + getNumPcm(numSamples);
}

// Render the data offline, then start looping it
jboolean render(const EAS_U8 *const pitchBytes, const jint numPitches,
                const EAS_U8 velocity,
                const jlong noteDurationMs,
                const jlong recordingDurationMs) {

    float *noteEndPosition, *decayEndPosition, *floatBuffer = NULL;
    int i;

    // MIDI info
    const EAS_U8 velocityMax = 127; // Maximum allowed velocity in MIDI

    // Internal parameters
    const long rampDownMs = 15; // Time for the ramp-down of a note
    const double rampDb = -40; // Amount of ramping down
    const double minLevel = rampDb; // The sound level corresponding to zero velocity

    // Verify parameters
    if (recordingDurationMs < noteDurationMs) {
        LOG_E(LOG_TAG, "Recording duration less than note duration.");
        goto render_quit;
    }
    if (numPitches < 1) {
        LOG_E(LOG_TAG, "Invalid number of pitches: %d", numPitches);
        goto render_quit;
    }
    if (numPitches > pLibConfig->maxVoices) {
        LOG_E(LOG_TAG, "Too many pitches: %d (max: %ld)", numPitches, pLibConfig->maxVoices);
        goto render_quit;
    }
    if (velocity > velocityMax) {
        LOG_E(LOG_TAG, "Velocity %d exceeds maximum value of %d", velocity, velocityMax);
        goto render_quit;
    }

    // Verify initialization
    if (!isInitialized("render"))
        goto render_quit;

    // Compute the recording lengths
    const EAS_I32 noteSamples = ms2Samples(noteDurationMs);
    const EAS_I32 recordingSamples = ms2Samples(recordingDurationMs);
    const EAS_I32 decaySamples = recordingSamples - noteSamples;

    // Free the old recording
    if (delete_recording() != SL_RESULT_SUCCESS) {
        LOG_E(LOG_TAG, "Failed to delete previous recording.");
        goto render_quit;
    }
    assert(record_buffer == NULL); // Should be freed by now
    assert(state == IDLE); // Shouldn't be playing anything

    // Allocate a new recording. Put room for an extra two mix buffer at the end, to prevent writing
    // past the end of the buffer during rendering.
    // TODO: Don't need extra padding for fluid
    const size_t numBufferMonoSamples = recordingSamples + 2 * pLibConfig->mixBufferSize;
    const size_t recordBufferLength = getNumPcm(numBufferMonoSamples);
    if ((record_buffer = (EAS_PCM *) malloc(recordBufferLength * sizeof(EAS_PCM))) == NULL) {
        LOG_E(LOG_TAG, "Insufficient memory for recording buffer.");
        goto render_quit;
    }

    // Allocate a buffer for the internal floating-point representation
    if ((floatBuffer = malloc(recordBufferLength * sizeof(float))) == NULL) {
        LOG_E(LOG_TAG, "Insufficient memory for float buffer.");
        goto render_quit;
    }

    // Send the note start messages
    for (i = 0; i < numPitches; i++) {
        if (startNote(pitchBytes[i], velocity) != EAS_SUCCESS)
            goto render_quit;
    }

    // Render the note attacks and sustains
    if ((noteEndPosition = renderSamples(noteSamples, floatBuffer)) == NULL)
        goto render_quit;

    // Send the note end messages
    for (i = 0; i < numPitches; i++) {
        if (endNote(pitchBytes[i]) != EAS_SUCCESS)
            goto render_quit;
    }

    // Render the note decays
    if ((decayEndPosition = renderSamples(decaySamples, noteEndPosition)) == NULL)
        goto render_quit;

    // Ramp down the audio at the end of the recording
    const size_t rampDownNumPcm = getNumPcm(ms2Samples(
            rampDownMs > noteDurationMs ? noteDurationMs : rampDownMs));
    float *const rampStartPosition = decayEndPosition - rampDownNumPcm;
    rampDown(rampStartPosition, rampDownNumPcm, rampDb);

    // Compute the maximum level based on the velocity
    const double maxLevel = (double) velocity / (double) velocityMax;

    // Normalize the audio and convert to the final recording representation
    if (normalize(floatBuffer, record_buffer, recordBufferLength, maxLevel))
        goto render_quit;

    // Clean up intermediates
    free(floatBuffer);

    // Set the end of the recording
    recording_position = record_buffer + getNumPcm(recordingSamples);

    // Start playing the recording
    play();
    return JNI_TRUE;

    render_quit:
    if (floatBuffer != NULL)
        free(floatBuffer);
    return JNI_FALSE;
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
SLresult createBufferQueueAudioPlayer() {
    SLresult result;

    // configure audio source
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq =
            {
                    SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2
            };
    SLDataFormat_PCM format_pcm =
            {
                    SL_DATAFORMAT_PCM, (SLuint32) (pLibConfig->numChannels),
                    (SLuint32) (pLibConfig->sampleRate * 1000),
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
    const SLInterfaceID ids[] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME};
    const SLboolean req[] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    const size_t numIds = sizeof(ids) / sizeof(SLInterfaceID);

    result = (*engineEngine)->CreateAudioPlayer(engineEngine,
                                                &bqPlayerObject,
                                                &audioSrc, &audioSnk,
                                                numIds, ids, req);
    if (SL_RESULT_SUCCESS != result)
        return result;

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

    // Get the volume interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
    if (result != SL_RESULT_SUCCESS) {
        return result;
    }

    // LOG_D(LOG_TAG, "Play interface retrieved");

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
void shutdownAudio() {
    // destroy buffer queue audio player object, and invalidate all
    // associated interfaces
    if (bqPlayerObject != NULL) {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = NULL;
        bqPlayerPlay = NULL;
        bqPlayerVolume = NULL;
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
void shutdownFluid() {
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

    // get the library configuration
    pLibConfig = EAS_Config();
    if (pLibConfig == NULL || pLibConfig->libVersion != LIB_VERSION)
        return -1;

    // calculate buffer size
    bufferSize = pLibConfig->mixBufferSize * pLibConfig->numChannels * NUM_BUFFERS;

    // Create the settings
    if ((fluidSettings = new_fluid_settings()) == NULL) {
        LOG_E(LOG_TAG, "Failed to create fluid settings");
        return -1;
    };

    // Get the EAS configuration, since we are copying the settings over
    pLibConfig = EAS_Config();

    // Configure the settings
    fluid_settings_setint(fluidSettings, "synth.polyphony", pLibConfig->maxVoices);
    const int numStereoChannels = pLibConfig->numChannels / 2;
    fluid_settings_setint(fluidSettings, "synth.audio-channels", numStereoChannels);
    fluid_settings_setnum(fluidSettings, "synth.sample-rate", pLibConfig->sampleRate);
    fluid_settings_setint(fluidSettings, "synth.threadsafe-api", 0); // Turn off monitor
    fluid_settings_setint(fluidSettings, "synth.chorus.active",
                          0); // Turn off chorus (could be enabled as an interesting feature)

    // Initialize the synthesizer
    if ((fluidSynth = new_fluid_synth(fluidSettings)) == NULL) {
        LOG_E(LOG_TAG, "Failed to initialize fluid synthesizer");
        return -1;
    }

    // Load the soundfont
    if ((soundFont = fluid_synth_sfload(fluidSynth, soundfontFilename, 1)) < 0) {
        LOG_E(LOG_TAG, "Failed to load soundfont %s", soundfontFilename);
        return -1;
    }

    return 0;
}

// init EAS midi
EAS_RESULT initEAS() {
    EAS_RESULT result;

    // get the library configuration
    pLibConfig = EAS_Config();
    if (pLibConfig == NULL || pLibConfig->libVersion != LIB_VERSION)
        return EAS_FAILURE;

    // calculate buffer size
    bufferSize = pLibConfig->mixBufferSize * pLibConfig->numChannels * NUM_BUFFERS;

    // init library
    if ((result = EAS_Init(&pEASData)) != EAS_SUCCESS)
        return result;

    // select reverb preset and enable
    EAS_SetParameter(pEASData, EAS_MODULE_REVERB, EAS_PARAM_REVERB_PRESET,
                     EAS_PARAM_REVERB_CHAMBER);
    EAS_SetParameter(pEASData, EAS_MODULE_REVERB, EAS_PARAM_REVERB_BYPASS,
                     EAS_FALSE);

    // open midi stream
    if ((result = EAS_OpenMIDIStream(pEASData, &midiHandle, NULL)) != EAS_SUCCESS)
        return result;

    return EAS_SUCCESS;
}

// shutdown EAS midi
void shutdownEAS() {

    if (midiHandle != NULL) {
        EAS_CloseMIDIStream(pEASData, midiHandle);
        midiHandle = NULL;
    }

    if (pEASData != NULL) {
        EAS_Shutdown(pEASData);
        pEASData = NULL;
    }
}

// init mididriver
jboolean midi_init(const char *soundfontFilename) {
    EAS_RESULT result;

    if ((result = initFluid(soundfontFilename))) {
        shutdownFluid();

        LOG_E(LOG_TAG, "Init fluid failed: %ld", result);

        return JNI_FALSE;
    }

    // LOG_D(LOG_TAG, "Init EAS success, buffer: %ld", bufferSize);

    // allocate buffer in bytes
    buffer = (EAS_PCM *) malloc(bufferSize * sizeof(EAS_PCM));
    if (buffer == NULL) {
        shutdownFluid();

        LOG_E(LOG_TAG, "Allocate buffer failed");

        return JNI_FALSE;
    }

    // create the engine and output mix objects
    if ((result = createEngine()) != SL_RESULT_SUCCESS) {
        shutdownFluid();
        shutdownAudio();
        free(buffer);
        buffer = NULL;

        LOG_E(LOG_TAG, "Create engine failed: %ld", result);

        return JNI_FALSE;
    }

    // create buffer queue audio player
    if ((result = createBufferQueueAudioPlayer()) != SL_RESULT_SUCCESS) {
        shutdownFluid();
        shutdownAudio();
        free(buffer);
        buffer = NULL;

        LOG_E(LOG_TAG, "Create buffer queue audio player failed: %ld", result);

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

// Write a MIDI message
jboolean midi_write(EAS_U8 *bytes, jint length) {
    EAS_RESULT result;

    // Verify initialization
    if (!isInitialized("write"))
        return JNI_FALSE;

    return (EAS_WriteMIDIStream(pEASData, midiHandle, bytes, length) == EAS_SUCCESS) ?
           JNI_TRUE : JNI_FALSE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_init(JNIEnv *env,
                                                  jobject obj,
                                                  jobject AAssetAdapter,
                                                  jstring soundfontAAssetName) {
    // Initialize the AAssets wrapper, so we can do file I/O
    if (init_AAssets(env, AAssetAdapter)) {
        LOG_E(LOG_TAG, "Failed to initialize AAssets.");
        return JNI_FALSE;
    }

    // Initialize the synth
    return midi_init((*env)->GetStringUTFChars(env, soundfontAAssetName, NULL));
}

// midi config
jintArray
Java_org_billthefarmer_mididriver_MidiDriver_config(JNIEnv *env,
                                                    jobject obj) {
    jboolean isCopy;

    if (pLibConfig == NULL)
        return NULL;

    jintArray configArray = (*env)->NewIntArray(env, 4);

    jint *config = (*env)->GetIntArrayElements(env, configArray, &isCopy);

    config[0] = pLibConfig->maxVoices;
    config[1] = pLibConfig->numChannels;
    config[2] = pLibConfig->sampleRate;
    config[3] = pLibConfig->mixBufferSize;

    (*env)->ReleaseIntArrayElements(env, configArray, config, 0);

    return configArray;
}

// Stop looping, delete the recording
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_pauseJNI(JNIEnv *env,
                                                      jobject jobj) {
    return delete_recording() == SL_RESULT_SUCCESS ? JNI_TRUE : JNI_FALSE;
}


// Render and then start looping
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_render(JNIEnv *env,
                                                    jobject obj,
                                                    jbyteArray pitches,
                                                    jbyte velocity,
                                                    jlong noteDurationMs,
                                                    jlong recordingDurationMs) {

    EAS_RESULT result;
    jboolean isCopy;

    const EAS_U8 *const pitchBytes = (EAS_U8 *) (*env)->GetByteArrayElements(env, pitches, &isCopy);
    const jint numPitches = (*env)->GetArrayLength(env, pitches);

    result = render(pitchBytes, numPitches, velocity, noteDurationMs, recordingDurationMs);

    (*env)->ReleaseByteArrayElements(env, pitches, (jbyte *) pitchBytes, 0);

    return result;
}

// Change the MIDI program
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_changeProgramJNI(JNIEnv *env,
                                                              jobject obj,
                                                              jbyte programNum) {
    return (changeProgram(programNum) == 0) ? JNI_TRUE : JNI_FALSE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_write(JNIEnv *env,
                                                   jobject obj,
                                                   jbyteArray byteArray) {
    EAS_RESULT result;
    jboolean isCopy;
    jint length;
    EAS_U8 *bytes;

    bytes = (EAS_U8 *) (*env)->GetByteArrayElements(env, byteArray, &isCopy);
    length = (*env)->GetArrayLength(env, byteArray);

    result = midi_write(bytes, length);

    (*env)->ReleaseByteArrayElements(env, byteArray, (jbyte *) bytes, 0);

    return result;
}

// shutdown midi
jboolean midi_shutdown() {
    EAS_RESULT result;

    shutdownAudio();

    if (buffer != NULL)
        free(buffer);
    buffer = NULL;

    shutdownFluid();

    if (delete_recording() != SL_RESULT_SUCCESS)
        return JNI_FALSE;
    sem_destroy(&is_idle);

    return JNI_TRUE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_shutdown(JNIEnv *env,
                                                      jobject obj) {
    // Delete the synth
    if (midi_shutdown() != JNI_TRUE)
        return JNI_FALSE;

    // Release AAssets, enabling garbage collection
    release_AAssets(env);

    return JNI_TRUE;
}

#ifdef __cplusplus
}
#endif