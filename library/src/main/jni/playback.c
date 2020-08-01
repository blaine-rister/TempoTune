/*
 * Code for efficiently looping an audio track using OpenSL ES.
 *
 * Created by Blaine Rister on 9/13/2019.
 */

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <semaphore.h>
#include <malloc.h>
#include <string.h>
#include <assert.h>
#include <limits.h>
#include <stdlib.h>

#include "global.h"

// Constants
const int bufferQueueSize = 2;

// Static function declarations
static SLresult enqueueBuffer(void);
static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context);
static int idle(void);
static void shutdownAudio(void);
static int isPlaying(void);

// engine interfaces
static SLObjectItf engineObject = NULL;
static SLEngineItf engineEngine;

// output mix interfaces
static SLObjectItf outputMixObject = NULL;

// buffer queue player interfaces
static SLObjectItf bqPlayerObject = NULL;
static SLPlayItf bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;

// Sound parameters
int bufferSizeMono;

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

// semaphores
static sem_t is_idle;

// create the engine and output mix objects
static SLresult createEngine() {
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
static SLresult createBufferQueueAudioPlayer(int sampleRate, int deviceBufferSizeMono) {

    SLAndroidConfigurationItf configItf;
    SLVolumeItf volumeItf;
    SLresult result;

    // Store the sound parameters
    bufferSizeMono = deviceBufferSizeMono;

    // configure audio source
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq =
            {
                    SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, bufferQueueSize
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

    // Get the Android configuration interface, if it exists. If so, enable low power mode. This
    // will fail on Android versions prior to API level 25
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_ANDROIDCONFIGURATION,
                                             &configItf);
    if (result == SL_RESULT_SUCCESS) {

        const SLuint32 desiredMode = SL_ANDROID_PERFORMANCE_POWER_SAVING;

        LOG_I(LOG_TAG, "Successfully received Android audio configItf");

        // Set the performance mode
        result = (*configItf)->SetConfiguration(configItf, SL_ANDROID_KEY_PERFORMANCE_MODE,
                                                &desiredMode, sizeof(desiredMode));
        if (result == SL_RESULT_SUCCESS) {
            LOG_I(LOG_TAG, "Set audio performance mode.");
        } else {
            LOG_W(LOG_TAG, "Failed to set audio performance mode (code %d).", result);
        }
    } else {
        LOG_W(LOG_TAG, "Failed to get the configuration controls (code %d). Audio performance mode "
                       "will not be set.", result);
    }

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

    return SL_RESULT_SUCCESS;
    // LOG_D(LOG_TAG, "Audio player set playing");
}

// Main initialization routine
int init(const int sampleRate, const int bufferSizeMono) {

    SLresult  result;

    // create the engine and output mix objects
    if ((result = createEngine()) != SL_RESULT_SUCCESS) {
        shutdownAudio();

        LOG_E(LOG_TAG, "Create engine failed: %d", result);

        return JNI_FALSE;
    }

    // create buffer queue audio player
    if ((result = createBufferQueueAudioPlayer(sampleRate, bufferSizeMono)) != SL_RESULT_SUCCESS) {
        shutdownAudio();

        LOG_E(LOG_TAG, "Create buffer queue audio player failed: %d", result);

        return JNI_FALSE;
    }

    // Initialize semaphor
    const int shared_processes = 0;
    const int sem_initial_value = 0;
    sem_init(&is_idle, shared_processes, sem_initial_value);

    return result;
}

// Initialize everything and play sound
static int play(int sampleRate, int bufferSizeMono) {

    SLresult result;
    int i;

    // Set the playback pointer
    if (record_buffer == NULL) {
        LOG_E(LOG_TAG, "playback failed: no recording stored");
        return -1;
    }
    playback_position = record_buffer;

    // Initialize the sound player
    if (init(sampleRate, bufferSizeMono))
        return -1;

    // Set the state to playing
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
    if (SL_RESULT_SUCCESS != result) {
        LOG_E(LOG_TAG, "playback failed: failed to set play state");
        return -1;
    }

    state = PLAYING;

    // Fill the queue with buffers, starting playback
    for (i = 0; i < bufferQueueSize; i++) {
        if (enqueueBuffer() != SL_RESULT_SUCCESS) {
            LOG_E(LOG_TAG, "playback failed: failed to enqueue buffer");
            return -1;
        }
    }
    LOG_I(LOG_TAG, "created %d audio buffers", bufferQueueSize);

    return 0;
}

// shut down the native audio system
static void shutdownAudio(void) {
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

// Deletes all data
static void cleanup(void) {
    shutdownAudio();
    sem_destroy(&is_idle);
    if (record_buffer != NULL) {
        free(record_buffer);
        record_buffer = NULL;
    }
}

// Set the player's state to paused. If currently playing, waits for the buffer to empty.
static int idle(void) {

    switch (state) {
        case IDLE:
            // Do nothing if we're already idle
            return 0;
        case PLAYING:

            // Initialize the pausing parameters
            pause_count = bufferSizeMono;
            pause_factor = 1.F / (float) pause_count;

            // Initiate the pausing phase
            state = STOPPING;

            // Fall-through to stopping
        case STOPPING:
            // Block until we have confirmation that there are no more callbacks
            sem_wait(&is_idle);
            state = IDLE;
    }

    // Tell OpenSL ES to stop playing sound. Note: this is non-blocking
    if ((*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED) != SL_RESULT_SUCCESS)
        return -1;

    // Delete all data
    cleanup();
    return 0;
}


static SLresult enqueueBuffer(void) {

    SLresult result;

    const size_t bufferSizePcm = getNumPcm(bufferSizeMono);

    // Enqueue playback
    result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, playback_position,
                                             bufferSizePcm * sizeof(output_t));

    // Update the playback position circularly
    playback_position += bufferSizePcm;
    if (playback_position >= recording_position) {
        playback_position = record_buffer + (playback_position - recording_position);
    }

    return result;
}

// this callback handler is called every time a buffer finishes
// playing
static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {

    SLresult result;
    int i, j;

    assert(bq == bqPlayerBufferQueue);
    assert(NULL == context);

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
            result = enqueueBuffer();
            switch (result) {
                case SL_RESULT_SUCCESS:
                case SL_RESULT_OPERATION_ABORTED:
                    return;
                default:
                    /* Could get SL_RESULT_BUFFER_INSUFFICIENT (code 7) if the buffer is full. This
                     * shouldn't happen because we are supposed to wait for the buffer to clear
                     * before starting a new render. */
                    LOG_E(LOG_TAG, "Error code from OpenSL ES enqueue buffer: %d", result);
                    assert(0);
            }
            return;
    }
}

// Return a uniformly distributed R.V. in the range [0,1]
static float uniform(void) {
    return ((float) rand()) / (float) RAND_MAX;
}

// Return triangularly-distributed dither in the range [-1, 1].
static float dither(void) {
    return (uniform() + uniform()) / 2;
}

// Convert float audio to the desired output type, with dithering.
static int finalizeAudio(const float *const inBuffer, output_t *const outBuffer,
                     const size_t bufferLength) {

    float maxBefore;
    size_t i;

    assert(sizeof(int16_t) == sizeof(output_t));
    const double maxPcm = (double) INT16_MAX;

    // Compute the linear gain
    const float gain = (float) (maxPcm / (double) maxFloatLevel);

    // Apply the gain and convert to fixed-point
    for (i = 0; i < bufferLength; i++) {
        // Apply gain and dither
        const float dithered = inBuffer[i] * gain + dither();

        // Convert with truncation to avoid overflow
        outBuffer[i] = (output_t) MIN(MAX(dithered, (float) INT16_MIN), (float) INT16_MAX);
    }

    return 0;
}

// Make a copy of the recording and add padding
int formatRecording(const int bufferSizeMono, const jfloat *const floatBuffer,
        const size_t recordingSizeMono) {

    // Allocate a new recording. Add an extra buffer at the end, to imitate looping behavior
    const size_t bufferLength = getNumPcm(recordingSizeMono + bufferSizeMono);
    if ((record_buffer = (output_t *) malloc(bufferLength * sizeof(output_t))) == NULL) {
        LOG_E(LOG_TAG, "Insufficient memory for recording buffer.");
        return -1;
    }

    // Convert to the output format with dithering
    const size_t recordingLength = getNumPcm(recordingSizeMono);
    finalizeAudio(floatBuffer, record_buffer, recordingLength);

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

    return 0;
}

// Tell whether we are playing sound. For our purposes, stopping does not count.
static int isPlaying(void) {
    return state == PLAYING;
}

/* ------------------ JNI functions ---------------------- */

// Play the given recording
static
jboolean
playJNI(JNIEnv *env,
        jobject obj,
        jint deviceSampleRate,
        jint deviceBufferSizeMono,
        jfloatArray jArray) {

    int result;

    // Stop playing, in case we previously were
    if (idle())
        return JNI_FALSE;

    // Get the recording data
    jboolean isCopy;
    jfloat *const jData = (*env)->GetFloatArrayElements(env, jArray, &isCopy);
    const size_t recordingSizeMono = (size_t) (*env)->GetArrayLength(env, jArray) / numChannels;

    // Make a copy for our own purposes, with padding
    result = formatRecording(deviceBufferSizeMono, jData, recordingSizeMono);

    // Release the input recording, without writing back changes
    (*env)->ReleaseFloatArrayElements(env, jArray, jData, JNI_ABORT);

    // If recording failed to format, quit early
    if (result)
        return JNI_FALSE;

    // Play sound
    if (play(deviceSampleRate, deviceBufferSizeMono)) {
        cleanup();
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_com_bbrister_mididriver_PlaybackDriver_B(JNIEnv *env,
                                          jobject obj,
                                          jint deviceSampleRate,
                                          jint deviceBufferSizeMono,
                                          jfloatArray jArray) {
    return playJNI(env, obj, deviceSampleRate, deviceBufferSizeMono, jArray);
}

// Stop looping, delete the recording
static
jboolean
pauseJNI(JNIEnv *env,
         jobject obj) {
    return (idle() == 0) ? JNI_TRUE : JNI_FALSE;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_com_bbrister_mididriver_PlaybackDriver_C(JNIEnv *env,
                                          jobject obj) {
    return pauseJNI(env, obj);
}

// Tell whether we are playing sound. For our purposes, stopping does not count.
static
jboolean
isPlayingJNI(JNIEnv *env,
             jobject obj) {
    return isPlaying() ? JNI_TRUE : JNI_FALSE;
}

// Obfuscated JNI wrapper for the former
JNIEXPORT
jboolean
Java_com_bbrister_mididriver_PlaybackDriver_D(JNIEnv *env,
                                              jobject obj) {
    return isPlayingJNI(env, obj);
}


