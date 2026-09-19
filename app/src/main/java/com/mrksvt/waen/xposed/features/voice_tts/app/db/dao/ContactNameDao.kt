package com.mrksvt.waen.xposed.features.voice_tts.app.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.ContactNameEntity

@Dao
interface ContactNameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entity: ContactNameEntity)

    @Query("SELECT display_name FROM contact_name WHERE contact_id = :contactId")
    fun get(contactId: String): String?
}
