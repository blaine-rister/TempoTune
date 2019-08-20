# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES = \
	fluidlite_squash/fluid_chan.c \
	fluidlite_squash/fluid_chorus.c \
	fluidlite_squash/fluid_conv.c \
	fluidlite_squash/fluid_defsfont.c \
	fluidlite_squash/fluid_dsp_float.c \
	fluidlite_squash/fluid_gen.c \
	fluidlite_squash/fluid_hash.c \
	fluidlite_squash/fluid_list.c \
	fluidlite_squash/fluid_mod.c \
	fluidlite_squash/fluid_ramsfont.c \
	fluidlite_squash/fluid_rev.c \
	fluidlite_squash/fluid_settings.c \
	fluidlite_squash/fluid_synth.c \
	fluidlite_squash/fluid_sys.c \
	fluidlite_squash/fluid_tuning.c \
	fluidlite_squash/fluid_voice.c \
	fluidlite_squash/aasset_stdio_adapter.c

LOCAL_CFLAGS += -std=gnu99 -D UNIFIED_DEBUG_MESSAGES

LOCAL_C_INCLUDES := \
	${LOCAL_PATH}/fluidlite_squash

LOCAL_ARM_MODE := arm

LOCAL_MODULE := fluidlite

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := midi
LOCAL_SRC_FILES := midi.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/host_src ${LOCAL_PATH}/fluidlite_squash
LOCAL_STATIC_LIBRARIES := fluidlite
LOCAL_LDLIBS := -lOpenSLES -llog -landroid

include $(BUILD_SHARED_LIBRARY)
