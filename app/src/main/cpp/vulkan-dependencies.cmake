# 固定头文件版本，使用 NDK 自带的着色器编译器；无需完整桌面 Vulkan SDK。
include(FetchContent)
FetchContent_Declare(vulkan_headers
    URL https://codeload.github.com/KhronosGroup/Vulkan-Headers/tar.gz/refs/tags/vulkan-sdk-1.4.341.0
    URL_HASH SHA256=d73bc5036b6556b741f6985ff600ca720308c5f2850e4a43ceb498bd3de069e7)
FetchContent_Declare(spirv_headers
    URL https://codeload.github.com/KhronosGroup/SPIRV-Headers/tar.gz/refs/tags/vulkan-sdk-1.4.341.0
    URL_HASH SHA256=cab0a654c4917e16367483296b44cdb1d614e3120c721beafcd37e3a8580486c)
set(SPIRV_HEADERS_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(SPIRV_HEADERS_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(vulkan_headers spirv_headers)
set(Vulkan_INCLUDE_DIR "${vulkan_headers_SOURCE_DIR}/include" CACHE PATH "" FORCE)
if(CMAKE_HOST_WIN32)
    set(_shader_host windows-x86_64)
    set(_shader_suffix .exe)
elseif(CMAKE_HOST_APPLE)
    set(_shader_host darwin-x86_64)
else()
    set(_shader_host linux-x86_64)
endif()
set(Vulkan_GLSLC_EXECUTABLE "${ANDROID_NDK}/shader-tools/${_shader_host}/glslc${_shader_suffix}" CACHE FILEPATH "" FORCE)
# 上游通过 find_package 获取 SPIR-V，导出已固定的构建树目标供其查找。
export(TARGETS SPIRV-Headers NAMESPACE SPIRV-Headers:: FILE "${CMAKE_CURRENT_BINARY_DIR}/spirv-package/SPIRV-HeadersTargets.cmake")
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/spirv-package/SPIRV-HeadersConfig.cmake"
    "if(NOT TARGET SPIRV-Headers::SPIRV-Headers)\n  include(\"\${CMAKE_CURRENT_LIST_DIR}/SPIRV-HeadersTargets.cmake\")\nendif()\n")
set(SPIRV-Headers_DIR "${CMAKE_CURRENT_BINARY_DIR}/spirv-package" CACHE PATH "" FORCE)
include_directories(SYSTEM "${spirv_headers_SOURCE_DIR}/include")
