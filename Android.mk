LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := src/com/cyanogenmod/eleven/IElevenService.aidl
LOCAL_SRC_FILES += $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, res) \
    $(TOP)/frameworks/support/v7/cardview/res \
    $(TOP)/frameworks/support/v7/recyclerview/res \
    $(TOP)/frameworks/support/v7/appcompat/res \
    $(TOP)/frameworks/support/v7/appcompat/res-public

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-common \
    android-support-annotations \
    android-support-v4 \
    android-support-v7-appcompat \
    android-support-v7-cardview \
    android-support-v7-palette \
    android-support-v7-recyclerview \
    android-support-v8-renderscript \
    guava

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.support.v7.cardview \
    --extra-packages android.support.v7.recyclerview \

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

LOCAL_MAVEN_GROUP    := com.github.paolorotolo
LOCAL_MAVEN_ARTIFACT := appintro
LOCAL_MAVEN_VERSION  := 3.2.0
LOCAL_MAVEN_REPO     := com.github.paolorotolo.appintro

include $(BUILD_MULTI_PREBUILT)
