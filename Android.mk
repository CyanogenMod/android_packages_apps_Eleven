LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := src/com/cyngn/eleven/IElevenService.aidl
LOCAL_SRC_FILES += $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v4 \
    android-support-v8-renderscript \
    android-common \
    nineoldandroids \
    palette

LOCAL_PACKAGE_NAME := Eleven
LOCAL_OVERRIDES_PACKAGES := Music

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

#Include nineoldadroids/palette jar

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    nineoldandroids:libs/nineoldandroids-2.4.0.jar \
    palette:libs/android-support-v7-palette.jar
include $(BUILD_MULTI_PREBUILT)
