cmake_minimum_required(VERSION 3.22.1)
project(sotc_android_bridge LANGUAGES CXX)

add_library(sotc_android_bridge SHARED native_bridge.cpp)
target_compile_features(sotc_android_bridge PRIVATE cxx_std_17)
target_compile_options(sotc_android_bridge PRIVATE -Wall -Wextra -Werror=return-type)
find_library(android_log log)
target_link_libraries(sotc_android_bridge PRIVATE ${android_log})
