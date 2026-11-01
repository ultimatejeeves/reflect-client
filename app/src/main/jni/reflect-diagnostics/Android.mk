LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := reflect-diagnostics
LOCAL_SRC_FILES := vulkan_probe.cpp
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra -Werror
LOCAL_LDLIBS := -ldl
LOCAL_BRANCH_PROTECTION := standard
include $(BUILD_SHARED_LIBRARY)
