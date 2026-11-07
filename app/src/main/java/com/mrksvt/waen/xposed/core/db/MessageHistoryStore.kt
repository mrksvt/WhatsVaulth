package com.mrksvt.waen.xposed.core.db

import android.content.Context
import android.util.LruCache
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.core.db.entity.HideSeenEntity
import com.mrksvt.waen.xposed.core.db.entity.MessageEntity
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge

class MessageHistoryStore private constructor(context: Context) {

    enum class ReceiptType {
        READ,
        PLAYED
    }

    interface HideSeenChangeListener {
        fun onHideSeenChanged(jid: String, messageId: String, type: ReceiptType, viewed: Boolean)
    }

    data class MessageItem(
        @JvmField val id: Long,
        @JvmField val message: String,
        @JvmField val timestamp: Long
    )

    class MessageSeenItem(
        @JvmField val jid: String?,
        @JvmField val userjid: String?,
        @JvmField val message: String,
        @JvmField val viewed: Boolean
    ) {
        private var fMessageWpp: FMessageWpp? = null

        val fMessage: FMessageWpp?
            get() {
                if (fMessageWpp == null) {
                    try {
                        val userJid = FMessageWpp.UserJid(userjid ?: jid)
                        if (userJid.isNull) return null
                        fMessageWpp = FMessageWpp.Key(message, userJid, false).fMessage
                    } catch (_: Exception) {
                    }
                }
                return fMessageWpp
            }
    }

    private val messagesCache = LruCache<Long, ArrayList<MessageItem>>(MESSAGE_CACHE_SIZE)
    private val seenMessageCache = LruCache<String, MessageSeenItem>(SEEN_MESSAGE_CACHE_SIZE)
    private val seenMessagesListCache =
        LruCache<String, List<MessageSeenItem>>(SEEN_MESSAGES_LIST_CACHE_SIZE)

    private val db: MessageHistoryDatabase = Room.databaseBuilder(
        context.applicationContext,
        MessageHistoryDatabase::class.java,
        "MessageHistory.db"
    )
        .allowMainThreadQueries()
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(MessageHistoryDatabase.MIGRATION_6_7, MessageHistoryDatabase.MIGRATION_7_8, MessageHistoryDatabase.MIGRATION_8_9, MessageHistoryDatabase.MIGRATION_9_10, MessageHistoryDatabase.MIGRATION_10_11, MessageHistoryDatabase.MIGRATION_11_12)
        .build()

    private val messageDao = db.messageDao()
    private val hideSeenDao = db.hideSeenDao()
    internal val translationCacheDao = db.translationCacheDao()

    companion object {
        private const val MESSAGE_CACHE_SIZE = 100
        private const val SEEN_MESSAGE_CACHE_SIZE = 200
        private const val SEEN_MESSAGES_LIST_CACHE_SIZE = 50
        private const val BATCH_CHUNK_SIZE = 500

        private val EMPTY_SEEN_ITEM = MessageSeenItem("", "", "", false)
        private val EMPTY_MESSAGE_LIST = ArrayList<MessageItem>()

        @Volatile
        private var hideSeenChangeListener: HideSeenChangeListener? = null

        @Volatile
        private var mInstance: MessageHistoryStore? = null

        @JvmStatic
        fun getInstance(): MessageHistoryStore {
            return mInstance ?: synchronized(this) {
                mInstance ?: MessageHistoryStore(Utils.application).also { mInstance = it }
            }
        }

        @JvmStatic
        fun setHideSeenChangeListener(listener: HideSeenChangeListener?) {
            hideSeenChangeListener = listener
        }


    }

    fun insertMessage(id: Long, message: String, timestamp: Long) {
        try {
            messageDao.insert(
                MessageEntity(
                    rowId = id,
                    textData = message,
                    editTimestamp = timestamp
                )
            )
            messagesCache.remove(id)
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun getMessages(v: Long): ArrayList<MessageItem>? {
        try {
            val cachedMessages = messagesCache.get(v)
            if (cachedMessages != null) {
                return if (cachedMessages === EMPTY_MESSAGE_LIST) null else cachedMessages
            }

            val history = messageDao.getMessagesByRowId(v)
            if (history.isNotEmpty()) {
                val messages = ArrayList<MessageItem>()
                for (entity in history) {
                    messages.add(
                        MessageItem(
                            entity.rowId,
                            entity.textData,
                            entity.editTimestamp ?: 0L
                        )
                    )
                }
                messagesCache.put(v, messages)
                return messages
            } else {
                messagesCache.put(v, EMPTY_MESSAGE_LIST)
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
        return null
    }

    fun insertHideSeenMessage(
        jid: String?,
        userJid: String?,
        messageId: String?,
        type: ReceiptType?,
        viewed: Boolean
    ) {
        try {
            if (messageId == null || type == null) return
            val jids = buildJidList(jid, userJid)
            if (jids.isEmpty()) return
            val primaryJid = jid ?: userJid!!

            val isViewedInt = if (viewed) 1 else 0

            if (type == ReceiptType.PLAYED) {
                hideSeenDao.insertOrIgnore(
                    HideSeenEntity(
                        jid = primaryJid,
                        userjid = userJid,
                        messageId = messageId,
                        played = isViewedInt
                    )
                )
                if (userJid != null) {
                    hideSeenDao.updateUserJidIfNeeded(primaryJid, userJid, messageId)
                }
                hideSeenDao.updatePlayed(jids, messageId, isViewedInt)
            } else {
                hideSeenDao.insertOrIgnore(
                    HideSeenEntity(
                        jid = primaryJid,
                        userjid = userJid,
                        messageId = messageId,
                        read = isViewedInt
                    )
                )
                if (userJid != null) {
                    hideSeenDao.updateUserJidIfNeeded(primaryJid, userJid, messageId)
                }
                hideSeenDao.updateRead(jids, messageId, isViewedInt)
            }

            val cacheKey = createSeenMessageCacheKey(primaryJid, messageId, type)
            seenMessageCache.remove(cacheKey)
            invalidateSeenMessagesListCache(primaryJid, type)
            hideSeenChangeListener?.onHideSeenChanged(primaryJid, messageId, type, viewed)
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun updateViewedMessage(
        jid: String?,
        userJid: String?,
        messageId: String?,
        type: ReceiptType?,
        viewed: Boolean
    ): Boolean {
        try {
            if (messageId == null || type == null) return false
            val jids = buildJidList(jid, userJid)
            if (jids.isEmpty()) return false
            val primaryJid = jid ?: userJid!!

            val isViewedInt = if (viewed) 1 else 0
            val updatedRows = db.runInTransaction<Int> {
                if (userJid != null) {
                    hideSeenDao.updateUserJidIfNeeded(primaryJid, userJid, messageId)
                }
                if (type == ReceiptType.PLAYED) {
                    hideSeenDao.updatePlayed(jids, messageId, isViewedInt)
                } else {
                    hideSeenDao.updateRead(jids, messageId, isViewedInt)
                }
            }

            if (updatedRows <= 0) return false

            val cacheKey = createSeenMessageCacheKey(primaryJid, messageId, type)
            val cachedItem = seenMessageCache.get(cacheKey)

            if (cachedItem != null && (cachedItem === EMPTY_SEEN_ITEM || cachedItem.viewed != viewed)) {
                seenMessageCache.remove(cacheKey)
            }
            invalidateSeenMessagesListCache(primaryJid, type)

            hideSeenChangeListener?.onHideSeenChanged(primaryJid, messageId, type, viewed)
            return true
        } catch (t: Throwable) {
            XposedBridge.log(t)
            return false
        }
    }

    fun updateViewedMessagesBatch(
        jid: String?,
        userJid: String?,
        messageIds: List<String>,
        type: ReceiptType?,
        viewed: Boolean
    ): Boolean {
        try {
            val jids = buildJidList(jid, userJid)
            if (jids.isEmpty() || type == null || messageIds.isEmpty()) return false
            val primaryJid = jid ?: userJid!!

            val isViewedInt = if (viewed) 1 else 0
            var updatedRows = 0
            db.runInTransaction {
                for (chunk in messageIds.chunked(BATCH_CHUNK_SIZE)) {
                    updatedRows += if (type == ReceiptType.PLAYED) {
                        hideSeenDao.updatePlayedBatch(jids, chunk, isViewedInt)
                    } else {
                        hideSeenDao.updateReadBatch(jids, chunk, isViewedInt)
                    }
                }
            }
            if (updatedRows <= 0) return false

            for (messageId in messageIds) {
                seenMessageCache.remove(createSeenMessageCacheKey(primaryJid, messageId, type))
                hideSeenChangeListener?.onHideSeenChanged(primaryJid, messageId, type, viewed)
            }
            invalidateSeenMessagesListCache(primaryJid, type)
            return true
        } catch (t: Throwable) {
            XposedBridge.log(t)
            return false
        }
    }

    fun getHideSeenMessage(
        jid: String?,
        userJid: String?,
        messageId: String?,
        type: ReceiptType?
    ): MessageSeenItem? {
        try {
            if (messageId == null || type == null) return null
            val jids = buildJidList(jid, userJid)
            if (jids.isEmpty()) return null
            val primaryJid = jid ?: userJid!!

            val cacheKey = createSeenMessageCacheKey(primaryJid, messageId, type)
            val cachedItem = seenMessageCache.get(cacheKey)
            if (cachedItem != null) {
                return if (cachedItem === EMPTY_SEEN_ITEM) null else cachedItem
            }

            val state = if (type == ReceiptType.PLAYED) {
                hideSeenDao.getPlayedState(jids, messageId)
            } else {
                hideSeenDao.getReadState(jids, messageId)
            }

            if (state != null) {
                val viewed = state == 1
                val message = MessageSeenItem(jid, userJid, messageId, viewed)
                seenMessageCache.put(cacheKey, message)
                return message
            } else {
                seenMessageCache.put(cacheKey, EMPTY_SEEN_ITEM)
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
        return null
    }

    fun getHideSeenMessages(
        jid: String?,
        userJid: String?,
        type: ReceiptType?,
        viewed: Boolean
    ): List<MessageSeenItem>? {
        try {
            if (type == null) return null
            val jids = buildJidList(jid, userJid)
            if (jids.isEmpty()) return null
            val primaryJid = jid ?: userJid!!

            val cacheKey = createSeenMessagesListCacheKey(primaryJid, type, viewed)
            val cachedList = seenMessagesListCache.get(cacheKey)
            if (cachedList != null) {
                return cachedList
            }

            val isViewedInt = if (viewed) 1 else 0
            val entities = if (type == ReceiptType.PLAYED) {
                hideSeenDao.getMessagesByPlayedState(jids, isViewedInt)
            } else {
                hideSeenDao.getMessagesByReadState(jids, isViewedInt)
            }

            if (entities.isNotEmpty()) {
                val messages = ArrayList<MessageSeenItem>()
                for (entity in entities) {
                    val message = MessageSeenItem(jid, userJid, entity.messageId, viewed)
                    messages.add(message)

                    // Alimenta também o cache individual
                    val msgCacheKey = createSeenMessageCacheKey(primaryJid, entity.messageId, type)
                    seenMessageCache.put(msgCacheKey, message)
                }
                seenMessagesListCache.put(cacheKey, messages)
                return messages
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
        return null
    }

    fun clearCaches() {
        messagesCache.evictAll()
        seenMessageCache.evictAll()
        seenMessagesListCache.evictAll()
    }

    private fun buildJidList(jid: String?, userJid: String?): List<String> {
        return listOfNotNull(jid, userJid).distinct()
    }

    private fun createSeenMessageCacheKey(
        jid: String,
        messageId: String,
        type: ReceiptType
    ): String {
        return "${jid}_${messageId}_${type.ordinal}"
    }

    private fun createSeenMessagesListCacheKey(
        jid: String,
        type: ReceiptType,
        viewed: Boolean
    ): String {
        return "${jid}_${type.ordinal}_${if (viewed) "1" else "0"}"
    }

    private fun invalidateSeenMessagesListCache(jid: String, type: ReceiptType) {
        seenMessagesListCache.remove(createSeenMessagesListCacheKey(jid, type, true))
        seenMessagesListCache.remove(createSeenMessagesListCacheKey(jid, type, false))
    }
}