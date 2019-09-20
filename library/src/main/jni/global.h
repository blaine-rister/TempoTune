/*
 * Private header for definitions which are shared across the various source files.
 *
 * Created by Blaine Rister on 9/13/2019.
 */

#ifndef METRODRONE_GLOBAL_H
#define METRODRONE_GLOBAL_H

#define LOG_TAG "MidiDriver"

// Logging macros
#ifdef NDEBUG
#define LOG(...) ;
#else
#include <android/log.h>
#define LOG __android_log_print
#endif
#define LOG_D(tag, ...) LOG(ANDROID_LOG_DEBUG, tag, __VA_ARGS__)
#define LOG_E(tag, ...) LOG(ANDROID_LOG_ERROR, tag, __VA_ARGS__)
#define LOG_I(tag, ...) LOG(ANDROID_LOG_INFO, tag, __VA_ARGS__)
#define LOG_W(tag, ...) LOG(ANDROID_LOG_WARN, tag, __VA_ARGS__)

// Math macros
#define MAX(x, y) ((x) > (y) ? (x) : (y))
#define MIN(x, y) ((x) < (y) ? (x) : (y))

// Output audio datatype
typedef int16_t output_t;

// Constants
const int numChannels = 2; // Stereo
const float maxFloatLevel = 1.0; // Audio normalization

/* ------------------- Utility (inline) functions -------------------- */

// Computes the number of output_t elements needed to store a given number of samples. This depends
// on how many channels we have.
static size_t getNumPcm(const size_t numSamples) {
    return numSamples * numChannels;
}

#endif //METRODRONE_GLOBAL_H
