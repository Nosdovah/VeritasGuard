if(NOT TARGET hermes-engine::hermesvm)
add_library(hermes-engine::hermesvm SHARED IMPORTED)
set_target_properties(hermes-engine::hermesvm PROPERTIES
    IMPORTED_LOCATION "C:/Users/raiha/.gradle/caches/8.13/transforms/e9eb742bee16a281c6945b57b9ee6daa/transformed/jetified-hermes-android-0.14.0-release/prefab/modules/hermesvm/libs/android.armeabi-v7a/libhermesvm.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/raiha/.gradle/caches/8.13/transforms/e9eb742bee16a281c6945b57b9ee6daa/transformed/jetified-hermes-android-0.14.0-release/prefab/modules/hermesvm/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

