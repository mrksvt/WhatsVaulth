package com.mrksvt.waen.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mrksvt.waen.xposed.core.db.entity.ThemePresetEntity

@Dao
interface ThemePresetDao {

    @Query("SELECT * FROM theme_presets")
    fun getAll(): List<ThemePresetEntity>

    @Query("SELECT * FROM theme_presets WHERE _id = :id")
    fun getById(id: Long): ThemePresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(preset: ThemePresetEntity): Long

    @Update
    fun update(preset: ThemePresetEntity)

    @Delete
    fun delete(preset: ThemePresetEntity)

    @Query("DELETE FROM theme_presets WHERE _id = :id")
    fun deleteById(id: Long)
}
