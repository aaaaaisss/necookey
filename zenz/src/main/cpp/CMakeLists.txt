cmake_minimum_required(VERSION 3.22.1)
project(necookey_zenz LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../vendor/sumire/zenz/src/main/cpp/llama.cpp")
set(ZENZ_BRIDGE "${CMAKE_CURRENT_LIST_DIR}/../../../../vendor/sumire/zenz/src/main/cpp/zenz_bridge.cpp")

set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_definitions(-D__ANDROID__)
add_subdirectory("${LLAMA_DIR}" llama-build)

add_library(zenz SHARED "${ZENZ_BRIDGE}")
target_include_directories(zenz PRIVATE
    "${LLAMA_DIR}/include"
    "${LLAMA_DIR}/ggml/include"
    "${LLAMA_DIR}/common"
)
find_library(log-lib log)
target_link_libraries(zenz PRIVATE llama ggml ${log-lib})
