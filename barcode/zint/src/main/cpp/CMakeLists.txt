# Builds libzint's backend as a static library and links it into a single
# JNI shared object. Works for both the Android (NDK) and host (JVM test) builds.
cmake_minimum_required(VERSION 3.22)
project(barcode_zint C)

get_filename_component(MODULE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../.." ABSOLUTE)
set(ZINT_BACKEND "${MODULE_ROOT}/third_party/zint/backend")

# zint's backend/CMakeLists.txt relies on these being included by its parent
# project, which we bypass by adding the backend directory directly.
include(GNUInstallDirs)
include(CheckCCompilerFlag)
include(CheckFunctionExists)

# The static backend gets linked into our shared object, so it must be PIC.
# The NDK does this by default; host builds do not.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Trim libzint down to just the encoder: no PNG/zlib output, no CLI, no Qt,
# no GS1 Syntax Engine. We render from the module matrix ourselves.
set(ZINT_SHARED    OFF CACHE BOOL "" FORCE)
set(ZINT_STATIC    ON  CACHE BOOL "" FORCE)
set(ZINT_FRONTEND  OFF CACHE BOOL "" FORCE)
set(ZINT_USE_PNG   OFF CACHE BOOL "" FORCE)
set(ZINT_USE_QT    OFF CACHE BOOL "" FORCE)
set(ZINT_USE_GS1SE OFF CACHE BOOL "" FORCE)
set(ZINT_UNINSTALL OFF CACHE BOOL "" FORCE)
set(ZINT_TEST      OFF CACHE BOOL "" FORCE)

add_subdirectory("${ZINT_BACKEND}" zint_backend)

add_library(barcode_zint SHARED zint_jni.c)
target_include_directories(barcode_zint PRIVATE "${ZINT_BACKEND}")
target_link_libraries(barcode_zint PRIVATE zint-static)

if(ANDROID)
    target_link_libraries(barcode_zint PRIVATE log)
else()
    # Host build for JVM-side verification tests.
    find_package(JNI REQUIRED)
    target_include_directories(barcode_zint PRIVATE ${JNI_INCLUDE_DIRS})
endif()
