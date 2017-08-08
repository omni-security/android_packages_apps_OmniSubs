#
# Copyright (C) 2008 The Android Open Source Project
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

ifeq ($(TARGET_BUILD_APPS),)
support_library_root_dir := frameworks/support
else
support_library_root_dir := prebuilts/sdk/current/support
endif

LOCAL_STATIC_JAVA_LIBRARIES += android-support-compat \
    android-support-core-utils \
    android-support-core-ui \
    android-support-fragment \
    android-support-design \
    android-support-v7-palette \
    android-support-v7-appcompat \
    android-support-v7-gridlayout \
    android-support-v7-cardview \
    android-support-v7-recyclerview \
    apksig \
    bcprov-jdk16 \
    overlay-manager-service

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res \
                      $(support_library_root_dir)/v7/appcompat/res \
                      $(support_library_root_dir)/v7/recyclerview/res \
                      $(support_library_root_dir)/v7/gridlayout/res \
                      $(support_library_root_dir)/v7/cardview/res \
                      $(support_library_root_dir)/design/res

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages android.support.v7.appcompat \
    --extra-packages android.support.v7.recyclerview \
    --extra-packages android.support.v7.gridlayout \
    --extra-packages android.support.v7.cardview \
    --extra-packages android.support.design

LOCAL_PROGUARD_FLAG_FILES := ../../proguard-rules.pro
LOCAL_JAR_EXCLUDE_FILES := none
LOCAL_SRC_FILES += $(call all-java-files-under, java) $(call all-Iaidl-files-under, aidl)
LOCAL_PACKAGE_NAME := OmniSubs
LOCAL_SDK_VERSION := current
LOCAL_MODULE_TAGS := optional
include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    apksig:libs/apksig-2.3.0.jar \
    bcprov-jdk16:libs/bcprov-jdk16-1.45.jar \
    overlay-manager-service:libs/overlay-manager-service.jar

include $(BUILD_MULTI_PREBUILT)
