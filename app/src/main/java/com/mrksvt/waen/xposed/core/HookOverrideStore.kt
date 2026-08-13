package com.mrksvt.waen.xposed.core

import android.content.Context
import android.content.SharedPreferences
import com.mrksvt.waen.xposed.utils.Utils

/**
 * Singleton store for hook overrides backed by SharedPreferences.
 * Provides runtime-configurable resource and method overrides for Xposed hooks.
 *
 * Overrides are stored in private preferences accessible from Xposed context.
 */
object HookOverrideStore {
    private const val PREF_NAME = "wae_hook_overrides"
    private const val KEY_RESOURCE_PREFIX = "resource_"
    private const val KEY_METHOD_PREFIX = "method_"

    /**
     * Get SharedPreferences instance for hook overrides.
     * Uses the same context as WppCore.getPrivPrefs() for consistency.
     */
    @JvmStatic
    fun getSharedPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get resource name override for a specific hook key.
     *
     * @param context Android context
     * @param hookKey Unique identifier for the hook (e.g., "composer_send_btn")
     * @return Resource name override if set, null otherwise
     */
    @JvmStatic
    fun getResourceOverride(context: Context, hookKey: String): String? {
        return getSharedPrefs(context).getString(KEY_RESOURCE_PREFIX + hookKey, null)
    }

    /**
     * Set resource name override for a specific hook key.
     *
     * @param context Android context
     * @param hookKey Unique identifier for the hook
     * @param resourceName Resource name to use as override (e.g., "send_button")
     */
    @JvmStatic
    fun setResourceOverride(context: Context, hookKey: String, resourceName: String) {
        getSharedPrefs(context).edit().putString(KEY_RESOURCE_PREFIX + hookKey, resourceName).apply()
    }

    /**
     * Get method override (class + method name) for a specific hook key.
     *
     * @param context Android context
     * @param hookKey Unique identifier for the hook
     * @return Pair of (className, methodName) if set, null otherwise
     */
    @JvmStatic
    fun getMethodOverride(context: Context, hookKey: String): Pair<String, String>? {
        val prefs = getSharedPrefs(context)
        val className = prefs.getString(KEY_METHOD_PREFIX + hookKey + "_class", null)
        val methodName = prefs.getString(KEY_METHOD_PREFIX + hookKey + "_method", null)
        return if (className != null && methodName != null) Pair(className, methodName) else null
    }

    /**
     * Set method override (class + method name) for a specific hook key.
     *
     * @param context Android context
     * @param hookKey Unique identifier for the hook
     * @param className Fully qualified class name
     * @param methodName Method name
     */
    @JvmStatic
    fun setMethodOverride(context: Context, hookKey: String, className: String, methodName: String) {
        getSharedPrefs(context).edit().apply {
            putString(KEY_METHOD_PREFIX + hookKey + "_class", className)
            putString(KEY_METHOD_PREFIX + hookKey + "_method", methodName)
        }.apply()
    }

    /**
     * Get all overrides as a flat map of key -> value.
     *
     * @param context Android context
     * @return Map of all overrides (resource and method overrides)
     */
    @JvmStatic
    fun getAllOverrides(context: Context): Map<String, String> {
        val prefs = getSharedPrefs(context)
        val allEntries = prefs.all
        val result = mutableMapOf<String, String>()
        
        allEntries.forEach { (key, value) ->
            if (value is String) {
                result[key] = value
            }
        }
        
        return result
    }

    /**
     * Remove override for a specific hook key.
     *
     * @param context Android context
     * @param hookKey Unique identifier for the hook
     */
    @JvmStatic
    fun removeOverride(context: Context, hookKey: String) {
        getSharedPrefs(context).edit().apply {
            remove(KEY_RESOURCE_PREFIX + hookKey)
            remove(KEY_METHOD_PREFIX + hookKey + "_class")
            remove(KEY_METHOD_PREFIX + hookKey + "_method")
        }.apply()
    }

    /**
     * Clear all overrides.
     *
     * @param context Android context
     */
    @JvmStatic
    fun clearAll(context: Context) {
        getSharedPrefs(context).edit().clear().apply()
    }
}
