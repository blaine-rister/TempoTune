/**
 * Adapter to access Android assets using C stdio functions. This file defines the private
 * interface.
 *
 * Created by: Blaine Rister Aug 17 2019
 */

#ifndef _AASSET_STDIO_ADAPTER_H
#define _AASSET_STDIO_ADAPTER_H

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

typedef AAsset* fluid_file;

fluid_file fluid_system_fopen(const char * filename, const char * mode );
size_t fluid_system_fread(void *ptr, size_t size, size_t nmemb, fluid_file stream);
int fluid_system_fseek(fluid_file stream, long int offset, int whence);
size_t fluid_system_fread(void *ptr, size_t size, size_t nmemb, fluid_file stream);
int fluid_system_fseek(fluid_file stream, long int offset, int whence);
int fluid_system_fclose(fluid_file stream);
long int fluid_system_ftell(fluid_file stream);
void fluid_system_rewind(fluid_file stream);

#endif