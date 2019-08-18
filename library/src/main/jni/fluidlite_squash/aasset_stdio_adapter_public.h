/**
 * Adapter to access Android assets using C stdio functions. This file gives the public interface.
 *
 * Created by: Blaine Rister Aug 17 2019
 */

#ifndef _AASSET_STDIO_ADAPTER_PUBLIC_H
#define _AASSET_STDIO_ADAPTER_PUBLIC_H

#include <jni.h>

int init_AAssets(JNIEnv *env, jobject javaAssetManager);
void release_AAssets(JNIEnv *env);

#endif