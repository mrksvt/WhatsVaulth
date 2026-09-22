package com.mrksvt.waen.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrksvt.waen.xposed.core.db.entity.DelMessage

@Dao
interface DelMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMessage(message: DelMessage)

    @Query("SELECT timestamp FROM delmessages WHERE jid = :jid AND msgid = :msgid LIMIT 1")
    fun getTimestampByJidAndMsgId(jid: String, msgid: String): Long?

    @Query("SELECT * FROM delmessages")
    fun getAllMessages(): List<DelMessage>

    @Query("SELECT * FROM delmessages WHERE _id > :lastId ORDER BY _id LIMIT :limit")
    fun getMessagesAfter(lastId: Long, limit: Int): List<DelMessage>

    @Query("SELECT * FROM delmessages WHERE jid = :jid")
    fun getFullMessagesByJid(jid: String): List<DelMessage>

    @Query("DELETE FROM delmessages WHERE jid = :jid")
    fun deleteByJid(jid: String)

    @Query("DELETE FROM delmessages")
    fun deleteAll()

}
