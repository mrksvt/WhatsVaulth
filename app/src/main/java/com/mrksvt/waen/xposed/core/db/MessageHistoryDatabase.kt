package com.mrksvt.waen.xposed.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mrksvt.waen.xposed.core.db.dao.HideSeenDao
import com.mrksvt.waen.xposed.core.db.dao.MessageDao
import com.mrksvt.waen.xposed.core.db.dao.TranslationCacheDao
import com.mrksvt.waen.xposed.core.db.entity.HideSeenEntity
import com.mrksvt.waen.xposed.core.db.entity.MessageEntity
import com.mrksvt.waen.xposed.core.db.entity.TranslationCacheEntity

@Database(
    entities = [MessageEntity::class, HideSeenEntity::class, TranslationCacheEntity::class],
    version = 6,
    exportSchema = false
)
abstract class MessageHistoryDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun hideSeenDao(): HideSeenDao
    abstract fun translationCacheDao(): TranslationCacheDao
}
