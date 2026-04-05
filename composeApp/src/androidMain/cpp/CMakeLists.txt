# Phase 3.1 — CMakeLists.txt: Build configuration for whisper.cpp JNI bridge

cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

# Set language standards
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_C_STANDARD 11)

# Optimization flags for Android
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Build whisper.cpp as a static library
add_library(whisper STATIC
    whisper.cpp
    ggml.c
    ggml-alloc.c
    ggml-backend.c
    ggml-quants.c
)

# Target properties for whisper library
target_include_directories(whisper PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Set compiler flags for Android ARM
target_compile_options(whisper PRIVATE
    -fPIC
    -fvisibility=hidden
)

# Create the JNI shared library
add_library(whisper_jni SHARED
    whisper_jni.cpp
)

# Link the JNI library against whisper static library
target_link_libraries(whisper_jni PRIVATE
    whisper
)

# Include directories for JNI
target_include_directories(whisper_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ANDROID_PLATFORM_LEVEL}
)

# Set visibility
target_compile_options(whisper_jni PRIVATE
    -fvisibility=hidden
    -fvisibility-inlines-hidden
)

# Android-specific linking
if(ANDROID)
    target_link_libraries(whisper_jni PRIVATE
        log  # for android logging
    )
endif()

# Enable fast math for whisper (no -march=native for cross-compilation)
target_compile_options(whisper PRIVATE
    -O3
)

# Strip symbols for smaller binary
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set_target_properties(whisper_jni PROPERTIES LINK_FLAGS_RELEASE -s)
endif()
