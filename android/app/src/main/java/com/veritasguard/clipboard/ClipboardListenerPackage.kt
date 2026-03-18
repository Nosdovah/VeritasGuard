package com.veritasguard.clipboard

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * ClipboardListenerPackage — React Native Package Registration
 *
 * Registers ClipboardListenerModule so it is accessible via
 * NativeModules.ClipboardListenerModule from JavaScript.
 */
class ClipboardListenerPackage : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(ClipboardListenerModule(reactContext))
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList()
    }
}
