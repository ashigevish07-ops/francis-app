cmake_minimum_required(VERSION 3.22.1)
project("francisgl")

add_library(
    francisgl
    SHARED
    francis_gl_hook.cpp
)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(
    francisgl
    ${log-lib}
    ${dl-lib}
)
