package com.mrksvt.waen.xposed.features.general

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Bundle
import com.mrksvt.waen.xposed.core.Feature
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * Port of upstream WaEnhancer v1.6.1 (#712).
 *
 * Android 14+ (and some OEM builds) strip the ML Kit component-discovery metadata from
 * PackageManager, so WhatsApp's document scanner cannot initialize text recognition. This
 * intercepts getServiceInfo(...) for the ML Kit discovery service and returns a ServiceInfo
 * carrying the missing registrar metadata.
 *
 * The hooked class is `android.app.ApplicationPackageManager`, a stable framework class, not
 * a WhatsApp class; the ML Kit constants are published library identifiers. No Unobfuscator
 * resolution applies here.
 */
class MinorFixes(classLoader: ClassLoader, prefs: SharedPreferences) :
    Feature(classLoader, prefs) {

    override fun doHook() {
        try {
            hookMlKitDiscoveryMetadata()
        } catch (e: Throwable) {
            log("Error in MinorFixes: ${e.message}")
        }
    }

    @SuppressLint("PrivateApi")
    private fun hookMlKitDiscoveryMetadata() {
        try {
            val pmClass = Class.forName("android.app.ApplicationPackageManager")
            var hookedCount = 0
            for (method in pmClass.declaredMethods) {
                if (method.name != "getServiceInfo") continue
                val parameterTypes = method.parameterTypes
                if (parameterTypes.isEmpty() || parameterTypes[0] != ComponentName::class.java) continue
                try {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val component = param.args.getOrNull(0) as? ComponentName
                                    ?: return
                                if (component.className != ML_KIT_DISCOVERY_SERVICE) return
                                param.result = ServiceInfo().apply {
                                    packageName = component.packageName
                                    name = component.className
                                    metaData = Bundle().apply {
                                        putString(
                                            ML_KIT_REGISTRAR_PREFIX + ML_KIT_COMMON_REGISTRAR,
                                            ML_KIT_COMPONENT_REGISTRAR
                                        )
                                    }
                                }
                                log("Restored missing ML Kit registrar metadata")
                            } catch (e: Throwable) {
                                log("Error in MinorFixes ML Kit hook: ${e.message}")
                            }
                        }
                    })
                    hookedCount++
                } catch (e: Throwable) {
                    // Individual overload may not be hookable; never fail the whole feature.
                    log("Error hooking getServiceInfo overload: ${e.message}")
                }
            }
            log("MinorFixes: hooked $hookedCount getServiceInfo overload(s)")
        } catch (e: Throwable) {
            log("Error in MinorFixes hookMlKitDiscoveryMetadata: ${e.message}")
        }
    }

    override fun getPluginName(): String = "minor_fixes"

    private companion object {
        const val ML_KIT_DISCOVERY_SERVICE =
            "com.google.mlkit.common.internal.MlKitComponentDiscoveryService"
        const val ML_KIT_REGISTRAR_PREFIX = "com.google.firebase.components:"
        const val ML_KIT_COMMON_REGISTRAR =
            "com.google.mlkit.common.internal.CommonComponentRegistrar"
        const val ML_KIT_COMPONENT_REGISTRAR =
            "com.google.firebase.components.ComponentRegistrar"
    }
}
