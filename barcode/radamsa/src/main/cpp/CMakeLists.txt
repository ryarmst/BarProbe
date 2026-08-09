# Compiles the vendored, pre-generated libradamsa.c together with the JNI bridge
# into one shared object. libradamsa.c is a single dependency-free C file (Owl Lisp
# emits it offline; see third_party/radamsa/PINNED_VERSION.txt), so there is no
# subproject to add and no toolchain beyond a C compiler.
cmake_minimum_required(VERSION 3.22)
project(barcode_radamsa C)

get_filename_component(MODULE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../.." ABSOLUTE)
set(RADAMSA_SRC "${MODULE_ROOT}/third_party/radamsa")

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# The generated Owl runtime is not warning-clean and is not ours to fix; silence it
# so a real warning in radamsa_jni.c is not lost in the noise. -O2 mirrors the
# spike, which is where determinism and memory behaviour were measured.
set_source_files_properties(
    "${RADAMSA_SRC}/libradamsa.c"
    PROPERTIES COMPILE_OPTIONS "-w"
)

add_library(barcode_radamsa SHARED
    radamsa_jni.c
    "${RADAMSA_SRC}/libradamsa.c"
)
target_include_directories(barcode_radamsa PRIVATE "${RADAMSA_SRC}")

if(ANDROID)
    target_link_libraries(barcode_radamsa PRIVATE log)
else()
    find_package(JNI REQUIRED)
    target_include_directories(barcode_radamsa PRIVATE ${JNI_INCLUDE_DIRS})
endif()
