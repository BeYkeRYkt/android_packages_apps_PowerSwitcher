LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PACKAGE_NAME := PowerSwitcher
LOCAL_SRC_FILES := \
    $(call all-java-files-under, java)

LOCAL_STATIC_ANDROID_LIBRARIES := \
    android-support-v4 \
    android-support-v7-appcompat \
    android-support-v7-preference \
    android-support-v7-recyclerview \
    android-support-v13 \
    android-support-v14-preference

LOCAL_STATIC_JAVA_LIBRARIES := \
    org.lineageos.platform.internal

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/res

LOCAL_USE_AAPT2 := true

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_MODULE_TAGS := optional
LOCAL_PROGUARD_FLAG_FILES := ../../proguard-rules.pro

include $(BUILD_PACKAGE)
