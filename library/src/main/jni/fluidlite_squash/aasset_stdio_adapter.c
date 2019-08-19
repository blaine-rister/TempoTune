/**
 * Adapter to access Android assets using C stdio functions.
 *
 * Created by: Blaine Rister Aug 17 2019
 */

#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#include <string.h>

#include "fluidsynth_priv.h"

// Include stdio for SEEK_SET
#include <stdio.h>

#include "aasset_stdio_adapter_public.h"
#include "aasset_stdio_adapter.h"

// TODO: Call AAssetManager_fromJava() to get the manager. Need to somehow pass the asset
// manager in the midi.init() function. This needs to persist the whole time the MIDI player is
// active, since the soundfont actually retains a pointer to its file descriptor. Set to NULL when
// releasing it.
static AAssetManager *aasset_manager = NULL;
static jobject javaGlobalAssetManager = NULL;

// Initialize the asset manager. Must be called before fluid_system_fopen can be used.
// Note: In the current implementation, this does NOT prevent the asset manager from garbage
// collection. Thus this sh
int init_AAssets(JNIEnv *env, jobject javaAssetManager) {

    // Release any existing references
    release_AAssets(env);

    // Retain a global reference to the underlying jobject
    javaGlobalAssetManager = (*env)->NewGlobalRef(env, javaAssetManager);
    if (javaGlobalAssetManager == NULL) {
        FLUID_LOG(FLUID_ERR, "init_AAssets: out of memory");
        return -1;
    }

    // Get a C pointer to the underlying data
    aasset_manager = AAssetManager_fromJava(env, javaGlobalAssetManager);

    return 0;
}

// Releases control of the AAsset manager, enabling garbage collection. Must call init_AAssets
// before next use.
void release_AAssets(JNIEnv *env) {
    if (javaGlobalAssetManager != NULL) {
        (*env)->DeleteGlobalRef(env, javaGlobalAssetManager);
        javaGlobalAssetManager = NULL;
    }
    aasset_manager = NULL;
}

// See https://stackoverflow.com/questions/13317387/how-to-get-file-in-assets-from-android-ndk
fluid_file fluid_system_fopen(const char * filename, const char * mode ) {
    if (strcmp(mode, "rb")) {
        FLUID_LOG(FLUID_ERR, "fluid_system_fopen: only mode \"rb\" is supported for Android "
                             "assets");
        return NULL;
    }

    if (aasset_manager == NULL) {
        FLUID_LOG(FLUID_ERR, "fluid_system_fopen: must call init_AAssets before this");
        return NULL;
    }
    return AAssetManager_open(aasset_manager, filename, AASSET_MODE_RANDOM);
}

size_t fluid_system_fread(void *ptr, size_t size, size_t nmemb, fluid_file stream) {
    return AAsset_read(stream, ptr, size * nmemb) / size;
}

int fluid_system_fseek(fluid_file stream, long int offset, int whence) {
    return AAsset_seek(stream, offset, whence);
}

void fluid_system_rewind(fluid_file stream) {
    fluid_system_fseek(stream, 0, SEEK_SET);
}

int fluid_system_fclose(fluid_file stream) {
    AAsset_close(stream);
    return 0;
}

long int fluid_system_ftell(fluid_file stream) {
    return AAsset_getLength(stream) - AAsset_getRemainingLength(stream);
}