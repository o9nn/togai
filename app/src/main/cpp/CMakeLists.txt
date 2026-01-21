# CMake configuration for Neural-Symbolic Kernel Native Library
# 
# Builds custom ggml kernels for neural-symbolic synthesis
# Targets Android arm64-v8a architecture with NDK

cmake_minimum_required(VERSION 3.18.1)

project(neural-symbolic-kernel)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 99)

# Compiler optimizations
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -funroll-loops")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math -funroll-loops")

# Android-specific settings
if(ANDROID)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC")
    
    # Enable NEON on ARM
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+fp+simd")
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8-a+fp+simd")
    endif()
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Find Android log library
find_library(log-lib log)

# Source files
set(KERNEL_SOURCES
    neural_symbolic_kernel.cpp
    jni_bridge.cpp
)

# Create shared library
add_library(
    ggml-neural-symbolic
    SHARED
    ${KERNEL_SOURCES}
)

# Link libraries
target_link_libraries(
    ggml-neural-symbolic
    ${log-lib}
    m  # Math library
)

# Set library properties
set_target_properties(
    ggml-neural-symbolic
    PROPERTIES
    VERSION 1.0.0
    SOVERSION 1
    OUTPUT_NAME "ggml-neural-symbolic"
)

# Installation (optional)
if(ANDROID)
    install(
        TARGETS ggml-neural-symbolic
        LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    )
endif()

# Generate compile_commands.json for IDE support
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Performance targets
# - Forward pass: ≤5µs
# - Memory operations: ≤100ns
# - Context switch: ≤5µs

# Print configuration
message(STATUS "Neural-Symbolic Kernel Build Configuration:")
message(STATUS "  Target ABI: ${ANDROID_ABI}")
message(STATUS "  C++ Standard: ${CMAKE_CXX_STANDARD}")
message(STATUS "  C Standard: ${CMAKE_C_STANDARD}")
message(STATUS "  Build Type: ${CMAKE_BUILD_TYPE}")
message(STATUS "  CXX Flags: ${CMAKE_CXX_FLAGS}")
message(STATUS "  C Flags: ${CMAKE_C_FLAGS}")
