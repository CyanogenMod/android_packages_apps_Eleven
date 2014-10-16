LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := src/com/cyngn/eleven/IElevenService.aidl
LOCAL_SRC_FILES += $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v8-renderscript \
    android-common \
    eleven_support_v4 \
    eleven_recyclerview

LOCAL_PACKAGE_NAME := Eleven
LOCAL_OVERRIDES_PACKAGES := Music

LOCAL_PROGUARD_ENABLED := obfuscation
LOCAL_PROGUARD_FLAGS := -include $(LOCAL_PATH)/proguard.cfg

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    eleven_recyclerview:libs/android-support-v7-recyclerview.jar \
    eleven_support_v4:libs/android-support-v4-21.jar
include $(BUILD_MULTI_PREBUILT)
