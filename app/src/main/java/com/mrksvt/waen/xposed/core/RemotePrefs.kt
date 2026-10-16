package com.mrksvt.waen.xposed.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import com.mrksvt.waen.BuildConfig
import com.mrksvt.waen.provider.PrefsProvider
import de.robv.android.xposed.XposedBridge

class RemotePrefs(context: Context) : SharedPreferences {

    private val resolver = context.contentResolver

    private val lock = Any()
    private var generation = 0

    @Volatile
    private var snapshot: Map<String, Any?>? = null

    fun invalidate() {
        synchronized(lock) {
            generation++
            snapshot = null
        }
    }

    private fun data(): Map<String, Any?> {
        snapshot?.let { return it }
        val gen = synchronized(lock) { generation }
        val loaded = load() ?: return emptyMap()
        synchronized(lock) {
            if (gen == generation) snapshot = loaded
        }
        return loaded
    }

    private fun load(): Map<String, Any?>? = try {
        val result = resolver.call(URI, PrefsProvider.METHOD_GET_ALL, null, null)
        val map = HashMap<String, Any?>()
        result?.getBundle(PrefsProvider.KEY_VALUES)?.let { values ->
            @Suppress("DEPRECATION")
            for (key in values.keySet()) map[key] = values.get(key)
        }
        result?.getBundle(PrefsProvider.KEY_SETS)?.let { sets ->
            for (key in sets.keySet()) {
                map[key] = sets.getStringArrayList(key)?.toHashSet()
            }
        }
        if (result == null) null else map
    } catch (e: Throwable) {
        XposedBridge.log(e)
        null
    }

    override fun getAll(): Map<String, *> = HashMap(data())

    override fun getString(key: String, defValue: String?): String? =
        data()[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (data()[key] as? Set<String>)?.let { HashSet(it) } ?: defValues

    override fun getInt(key: String, defValue: Int): Int = data()[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = data()[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = data()[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        data()[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = data().containsKey(key)

    override fun edit(): SharedPreferences.Editor =
        throw UnsupportedOperationException("Module preferences are read-only")

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
    }

    companion object {
        private val URI: Uri = "content://${BuildConfig.APPLICATION_ID}.preferences".toUri()

        @Volatile
        var instance: RemotePrefs? = null
            internal set
    }
}
