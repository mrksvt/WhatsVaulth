package com.mrksvt.waen.xposed.features.general

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mrksvt.waen.R
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.db.DelMessageStore
import com.mrksvt.waen.xposed.core.db.MessageStore
import com.mrksvt.waen.xposed.core.db.RestoreState
import com.mrksvt.waen.xposed.core.db.entity.DeletedMessageBackupEntity
import com.mrksvt.waen.xposed.core.WppCore
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * WP-05. Opens a device-credential-protected viewer for messages previously
 * captured by [DeletedMessageCapture]. Provides list per chat, pull to
 * refresh, restore (single + all), and permanent delete (single + all).
 *
 * The viewer is opened from the WhatsApp home overflow menu (added via
 * HomeActivity.onCreateOptionsMenu). All persistence flows through
 * [DelMessageStore] so the feature is storage-agnostic.
 */
class DeletedBackupViewer(
    loader: ClassLoader,
    preferences: SharedPreferences
) : Feature(loader, preferences) {

    companion object {
        private const val REQUEST_CODE_AUTH = 9143
        private const val REFRESH_BROADCAST = "com.mrksvt.waen.DELETED_BACKUP_REFRESH"
        private const val VIEWER_TITLE = "Deleted Messages (Backup)"
    }

    private var pendingActivity: Activity? = null
    private var refreshReceiver: BroadcastReceiver? = null
    private var registeredActivity: Activity? = null

    override fun doHook() {
        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onActivityResult",
                Integer.TYPE,
                Integer.TYPE,
                android.content.Intent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val requestCode = param.args[1] as Int
                            if (requestCode != REQUEST_CODE_AUTH) return
                            val resultCode = param.args[0] as Int
                            val act = param.thisObject as? Activity
                            if (resultCode == Activity.RESULT_OK && act != null) {
                                showViewer(act)
                            }
                            pendingActivity = null
                            param.result = null
                        } catch (e: Throwable) {
                            log(e)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log(e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                WppCore.homeActivityClass,
                "onCreateOptionsMenu",
                android.view.Menu::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val activity = param.thisObject as Activity
                            val menu = param.args[0] as android.view.Menu
                            addViewerMenuEntry(menu, activity)
                        } catch (e: Throwable) {
                            XposedBridge.log(e)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log(e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onDestroy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val activity = param.thisObject as? Activity ?: return
                            if (activity === registeredActivity) {
                                unregisterRefreshReceiver(activity)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log(e)
        }
    }

    private fun addViewerMenuEntry(menu: android.view.Menu, activity: Activity) {
        try {
            menu.add(0, R.string.deleted_message_capture_title, 0, R.string.deleted_message_capture_title)
                .setOnMenuItemClickListener {
                    promptAuth(activity)
                    true
                }
        } catch (e: Throwable) {
            log(e)
        }
    }

    private fun promptAuth(activity: Activity) {
        try {
            val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                ?: run {
                    showViewer(activity)
                    return
                }
            if (!km.isKeyguardSecure) {
                showViewer(activity)
                return
            }
            val intent = km.createConfirmDeviceCredentialIntent(
                activity.getString(R.string.deleted_message_capture_title),
                activity.getString(R.string.auth_backup_viewer_description)
            ) ?: run {
                showViewer(activity)
                return
            }
            pendingActivity = activity
            activity.startActivityForResult(intent, REQUEST_CODE_AUTH)
        } catch (e: Throwable) {
            log(e)
        }
    }

    private fun showViewer(activity: Activity) {
        try {
            registeredActivity = activity
            registerRefreshReceiver(activity)

            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
            }

            val title = TextView(activity).apply {
                text = "$VIEWER_TITLE (${DelMessageStore.getInstance(activity).countBackups()})"
                textSize = 18f
                setPadding(0, 0, 0, 16)
            }
            root.addView(title)

            val body = TextView(activity).apply {
                text = listBackupsSummary(activity)
                setTextIsSelectable(true)
            }
            root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val swipe = SwipeRefreshLayout(activity).apply {
                setOnRefreshListener {
                    body.text = listBackupsSummary(activity)
                    isRefreshing = false
                    title.text = "$VIEWER_TITLE (${DelMessageStore.getInstance(activity).countBackups()})"
                }
                addView(root)
            }

            val actions = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val restoreBtn = TextView(activity).apply {
                text = activity.getString(R.string.restore_all_messages)
                setPadding(48, 24, 48, 24)
                setOnClickListener { restoreAll(activity) }
            }
            val deleteBtn = TextView(activity).apply {
                text = activity.getString(R.string.delete_all_permanently)
                setPadding(48, 24, 48, 24)
                setOnClickListener { deleteAll(activity) }
            }
            actions.addView(restoreBtn)
            actions.addView(deleteBtn)
            root.addView(actions)

            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            container.addView(swipe)
            container.addView(actions)

            val scroll = android.widget.ScrollView(activity).apply {
                addView(container)
            }

            android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.deleted_message_capture_title)
                .setView(scroll)
                .setNegativeButton(R.string.no, null)
                .setOnDismissListener { unregisterRefreshReceiver(activity) }
                .show()
        } catch (e: Throwable) {
            log(e)
        }
    }

    private fun listBackupsSummary(activity: Activity): String {
        return try {
            val store = DelMessageStore.getInstance(activity)
            val all = store.listAllBackups()
            if (all.isEmpty()) {
                "No captured messages yet.\nEnable \"Deleted Message Capture\" and delete a message in WhatsApp to see it appear here."
            } else {
                all.take(64).joinToString("\n\n") { backup ->
                    val status = backup.restoreStatus ?: RestoreState.Pending.value
                    "[#${backup.id}] key=${backup.keyId.take(24)} status=$status at ${backup.deletedAt}"
                }.let { if (all.size > 64) "$it\n\n…and ${all.size - 64} more" else it }
            }
        } catch (e: Throwable) {
            log(e)
            "Failed to read backups"
        }
    }

    private fun registerRefreshReceiver(activity: Activity) {
        try {
            if (refreshReceiver != null) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val count = DelMessageStore.getInstance(activity).countBackups()
                    logDebug("reload requested via broadcast, total=$count")
                }
            }
            ContextCompat.registerReceiver(
                activity,
                receiver,
                IntentFilter(REFRESH_BROADCAST),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            refreshReceiver = receiver
        } catch (e: Throwable) {
            log(e)
        }
    }

    private fun unregisterRefreshReceiver(activity: Activity) {
        try {
            refreshReceiver?.let { activity.unregisterReceiver(it) }
        } catch (_: Throwable) {
        }
        refreshReceiver = null
    }

    private fun restoreAll(activity: Activity) {
        try {
            val store = DelMessageStore.getInstance(activity)
            val db = MessageStore.getInstance().openRestoreConnection()
            try {
                store.restoreAllBackups(db)
            } finally {
                try { db?.close() } catch (_: Throwable) {
                }
            }
        } catch (e: Throwable) {
            log(e)
        }
    }

    private fun restoreSingle(store: DelMessageStore, backup: DeletedMessageBackupEntity) {
        try {
            val db = MessageStore.getInstance().openRestoreConnection()
            try {
                store.restoreBackupById(db, backup.id)
            } finally {
                try { db?.close() } catch (_: Throwable) {
                }
            }
        } catch (e: Throwable) {
            log(e)
            store.markRestoreState(backup.id, RestoreState.Failed, e.message, 0L)
        }
    }

    private fun deleteAll(activity: Activity) {
        try {
            DelMessageStore.getInstance(activity).deleteAllBackups()
        } catch (e: Throwable) {
            log(e)
        }
    }

    override fun getPluginName(): String = "Deleted Backup Viewer"
}
