LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := src/com/cyanogenmod/eleven/IElevenService.aidl
LOCAL_SRC_FILES += $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, res) \
frameworks/support/v7/cardview/res

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v8-renderscript \
    android-support-v7-palette \
    android-support-v7-cardview \
    android-common \
    eleven_support_v4 \
    eleven_recyclerview \
    guava

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.support.v7.cardview

LOCAL_PACKAGE_NAME := Eleven
LOCAL_OVERRIDES_PACKAGES := Music

LOCAL_PROGUARD_FLAGS := -include $(LOCAL_PATH)/proguard.cfg
ifeq ($(TARGET_BUILD_VARIANT),user)
    LOCAL_PROGUARD_ENABLED := obfuscation
else
    LOCAL_PROGUARD_ENABLED := disabled
endif

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    eleven_recyclerview:libs/android-support-v7-recyclerview.jar \
    eleven_support_v4:libs/android-support-v4-21.jar
include $(BUILD_MULTI_PREBUILT)
