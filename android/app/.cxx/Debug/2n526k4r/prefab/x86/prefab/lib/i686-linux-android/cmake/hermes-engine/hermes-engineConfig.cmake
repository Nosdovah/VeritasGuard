if(NOT TARGET hermes-engine::hermesvm)
add_library(hermes-engine::hermesvm SHARED IMPORTED)
set_target_properties(hermes-engine::hermesvm PROPERTIES
    IMPORTED_LOCATION "C:/Users/raiha/.gradle/caches/8.13/transforms/35d53287715db6b466239e4bd8c79d7c/transformed/jetified-hermes-android-0.14.0-debug/prefab/modules/hermesvm/libs/android.x86/libhermesvm.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/raiha/.gradle/caches/8.13/transforms/35d53287715db6b466239e4bd8c79d7c/transformed/jetified-hermes-android-0.14.0-debug/prefab/modules/hermesvm/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

