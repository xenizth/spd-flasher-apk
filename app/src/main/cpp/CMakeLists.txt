cmake_minimum_required(VERSION 3.18.1)
project(spdbridge C)

set(CMAKE_C_STANDARD 99)
set(LIBUSB ${CMAKE_CURRENT_SOURCE_DIR}/libusb)

# ---- libusb 1.0.27, built from source with the upstream Android config ----
add_library(usb1 STATIC
    ${LIBUSB}/libusb/core.c
    ${LIBUSB}/libusb/descriptor.c
    ${LIBUSB}/libusb/hotplug.c
    ${LIBUSB}/libusb/io.c
    ${LIBUSB}/libusb/sync.c
    ${LIBUSB}/libusb/strerror.c
    ${LIBUSB}/libusb/os/linux_usbfs.c
    ${LIBUSB}/libusb/os/events_posix.c
    ${LIBUSB}/libusb/os/threads_posix.c
    ${LIBUSB}/libusb/os/linux_netlink.c)
target_include_directories(usb1 PRIVATE
    ${LIBUSB}/android          # config.h
    ${LIBUSB}/libusb
    ${LIBUSB}/libusb/os)
target_compile_options(usb1 PRIVATE -fvisibility=hidden -pthread -fPIC -w)

# spd_dump includes <libusb-1.0/libusb.h>
file(COPY ${LIBUSB}/libusb/libusb.h DESTINATION ${CMAKE_BINARY_DIR}/shim/libusb-1.0)

# ---- vendored TomKing spd_dump, with main/exit/atexit redirected so it can
#      run inside a forked child of the app process (see spd_compat.h) ----
add_library(spddump OBJECT
    spd_dump/common.c
    spd_dump/spd_dump.c)
target_include_directories(spddump PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/spd_dump
    ${CMAKE_BINARY_DIR}/shim)
target_compile_definitions(spddump PRIVATE USE_LIBUSB=1 _GNU_SOURCE)
target_compile_options(spddump PRIVATE -w -fPIC -Uunix -Ulinux
    -include ${CMAKE_CURRENT_SOURCE_DIR}/spd_compat.h)
set_source_files_properties(spd_dump/spd_dump.c PROPERTIES COMPILE_DEFINITIONS "main=spd_dump_main")

# ---- JNI bridge ----
add_library(spdbridge SHARED bridge.c $<TARGET_OBJECTS:spddump>)
target_link_libraries(spdbridge usb1 log m)
target_link_options(spdbridge PRIVATE -Wl,-z,max-page-size=16384)
