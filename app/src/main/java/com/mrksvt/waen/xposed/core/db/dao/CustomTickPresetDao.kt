package com.mrksvt.waen.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mrksvt.waen.xposed.core.db.entity.CustomTickPresetEntity

@Dao
interface CustomTickPresetDao {

    @Query("SELECT * FROM custom_tick_presets")
    fun getAll(): List<CustomTickPresetEntity>

    @Query("SELECT * FROM custom_tick_presets WHERE _id = :id")
    fun getById(id: Long): CustomTickPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(preset: CustomTickPresetEntity): Long

    @Update
    fun update(preset: CustomTickPresetEntity)

    @Delete
    fun delete(preset: CustomTickPresetEntity)

    @Query("DELETE FROM custom_tick_presets WHERE _id = :id")
    fun deleteById(id: Long)
}
