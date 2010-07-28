ifneq ($(strip $(libdspengineiface_already_built)),)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	main_mediaserver.cpp 

LOCAL_SHARED_LIBRARIES := \
	libaudioflinger \
	libcameraservice \
	libmediaplayerservice \
	libutils \
	libbinder

base := $(LOCAL_PATH)/../..

LOCAL_C_INCLUDES := \
    $(base)/libs/audioflinger \
    $(base)/camera/libcameraservice \
    $(base)/media/libmediaplayerservice

LOCAL_MODULE:= mediaserver

LOCAL_LDFLAGS += -ldspengineiface

include $(BUILD_EXECUTABLE)

endif
