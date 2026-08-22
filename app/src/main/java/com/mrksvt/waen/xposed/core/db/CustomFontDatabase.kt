package com.mrksvt.waen.xposed.core.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.ColumnInfo
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "custom_font_presets")
data class CustomFontPresetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long? = null,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "source", defaultValue = "bundled")
    val source: String,
    @ColumnInfo(name = "bundled_name", defaultValue = "")
    val bundledName: String? = null,
    @ColumnInfo(name = "custom_path", defaultValue = "")
    val customPath: String? = null
)

@Dao
interface CustomFontPresetDao {
    @Query("SELECT * FROM custom_font_presets")
    fun getAll(): List<CustomFontPresetEntity>

    @Query("SELECT * FROM custom_font_presets WHERE _id = :id")
    fun getById(id: Long): CustomFontPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(preset: CustomFontPresetEntity): Long

    @Update
    fun update(preset: CustomFontPresetEntity)

    @Delete
    fun delete(preset: CustomFontPresetEntity)

    @Query("DELETE FROM custom_font_presets WHERE _id = :id")
    fun deleteById(id: Long)
}

@Database(
    entities = [CustomFontPresetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CustomFontDatabase : RoomDatabase() {
    abstract fun customFontPresetDao(): CustomFontPresetDao
}
