package com.mrksvt.waen.xposed.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mrksvt.waen.xposed.core.db.dao.DelMessageDao
import com.mrksvt.waen.xposed.core.db.entity.DelMessage
import com.mrksvt.waen.xposed.core.db.entity.DeletedMessageBackupEntity

@Database(entities = [DelMessage::class, DeletedMessageBackupEntity::class], version = 16, exportSchema = false)
abstract class DelMessageDatabase : RoomDatabase() {

    abstract fun delMessageDao(): DelMessageDao

    companion object {
        @Volatile
        private var INSTANCE: DelMessageDatabase? = null

        private val DELMESSAGES_COLUMNS = listOf(
            "_id", "jid", "msgid", "timestamp", "text", "mediaPath", "mediaType",
            "senderName", "wa", "contact", "intime", "deltime", "voiceFileName", "fileId"
        )

        private val BACKUP_COLUMNS = listOf(
            "_id", "deleted_at", "restore_status", "restore_error", "restored_at",
            "message_unique_hash", "key_id", "chat_raw_string", "chat_phone_raw_string",
            "sender_raw_string", "sender_phone_raw_string", "from_me", "message_type",
            "message_timestamp", "message_json", "message_media_json", "message_text_json"
        )

        private const val DELMESSAGES_DDL =
            "CREATE TABLE IF NOT EXISTS `delmessages` (" +
                "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`jid` TEXT, `msgid` TEXT, `timestamp` INTEGER DEFAULT 0, `text` TEXT, " +
                "`mediaPath` TEXT, `mediaType` INTEGER DEFAULT -1, `senderName` TEXT, " +
                "`wa` TEXT, `contact` TEXT, `intime` INTEGER DEFAULT 0, " +
                "`deltime` INTEGER DEFAULT 0, `voiceFileName` TEXT, `fileId` TEXT)"

        private const val BACKUP_DDL =
            "CREATE TABLE IF NOT EXISTS `deleted_message_backup` (" +
                "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`deleted_at` INTEGER NOT NULL, `restore_status` TEXT NOT NULL, " +
                "`restore_error` TEXT, `restored_at` INTEGER NOT NULL, " +
                "`message_unique_hash` TEXT NOT NULL, `key_id` TEXT NOT NULL, " +
                "`chat_raw_string` TEXT, `chat_phone_raw_string` TEXT, " +
                "`sender_raw_string` TEXT, `sender_phone_raw_string` TEXT, " +
                "`from_me` INTEGER, `message_type` INTEGER, `message_timestamp` INTEGER, " +
                "`message_json` TEXT NOT NULL, `message_media_json` TEXT, " +
                "`message_text_json` TEXT)"

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN timestamp INTEGER DEFAULT 0;")
                } catch (_: Exception) {
                }
            }
        }

        private val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- Fix delmessages table ---
                db.execSQL(
                    "CREATE TABLE delmessages_new (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "jid TEXT, " +
                            "msgid TEXT, " +
                            "timestamp INTEGER DEFAULT 0)"
                )
                db.execSQL(
                    "INSERT INTO delmessages_new (_id, jid, msgid, timestamp) " +
                            "SELECT _id, jid, msgid, timestamp FROM delmessages"
                )
                db.execSQL("DROP TABLE delmessages")
                db.execSQL("ALTER TABLE delmessages_new RENAME TO delmessages")
                db.execSQL("CREATE UNIQUE INDEX index_delmessages_jid_msgid ON delmessages (jid, msgid)")

            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS deleted_for_me")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN text TEXT;")
                } catch (_: Exception) {
                }
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN mediaPath TEXT;")
                } catch (_: Exception) {
                }
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN mediaType INTEGER DEFAULT -1;")
                } catch (_: Exception) {
                }
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN senderName TEXT;")
                } catch (_: Exception) {
                }
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN wa TEXT;")
                } catch (_: Exception) {
                }
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN contact TEXT;")
                } catch (_: Exception) {
                }
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN intime INTEGER DEFAULT 0;")
                } catch (_: Exception) {
                }
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN deltime INTEGER DEFAULT 0;")
                } catch (_: Exception) {
                }
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN voiceFileName TEXT;")
                } catch (_: Exception) {
                }
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN fileId TEXT;")
                } catch (_: Exception) {
                }
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(BACKUP_DDL)
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_deleted_message_backup_message_unique_hash` " +
                        "ON `deleted_message_backup` (`message_unique_hash`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_deleted_backup_status_deleted_at` " +
                        "ON `deleted_message_backup` (`restore_status`, `deleted_at`)"
                )
            }
        }

        /**
         * Converges a version 15 database to the exact schema the entities declare.
         *
         * Version 15 was shipped with more than one physical schema (fork builds and the
         * upstream WaEnhancer module share the same `delmessages.db` file), so the stored
         * room_master_table identity hash can differ from the one this code expects while
         * `user_version` stays 15. Room cannot repair that case on its own and throws
         * "Room cannot verify the data integrity". Rebuilding both tables here makes the
         * physical schema match the entities, and Room then rewrites the identity hash.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                rebuildTable(
                    db,
                    "delmessages",
                    DELMESSAGES_DDL,
                    DELMESSAGES_COLUMNS,
                    "DELETE FROM `delmessages` WHERE `_id` NOT IN " +
                        "(SELECT MIN(`_id`) FROM `delmessages` GROUP BY `jid`, `msgid`)",
                    listOf(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_delmessages_jid_msgid` " +
                            "ON `delmessages` (`jid`, `msgid`)"
                    )
                )
                rebuildTable(
                    db,
                    "deleted_message_backup",
                    BACKUP_DDL,
                    BACKUP_COLUMNS,
                    "DELETE FROM `deleted_message_backup` WHERE `_id` NOT IN " +
                        "(SELECT MIN(`_id`) FROM `deleted_message_backup` " +
                        "GROUP BY `message_unique_hash`)",
                    listOf(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_deleted_message_backup_message_unique_hash` " +
                            "ON `deleted_message_backup` (`message_unique_hash`)",
                        "CREATE INDEX IF NOT EXISTS `idx_deleted_backup_status_deleted_at` " +
                            "ON `deleted_message_backup` (`restore_status`, `deleted_at`)"
                    )
                )
            }
        }

        private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf<Any>(table)
            ).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun existingColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
            val columns = HashSet<String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return columns
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(nameIndex))
                }
            }
            return columns
        }

        private fun rebuildTable(
            db: SupportSQLiteDatabase,
            table: String,
            createSql: String,
            columns: List<String>,
            dedupeSql: String,
            indexSql: List<String>
        ) {
            if (!tableExists(db, table)) {
                db.execSQL(createSql)
                indexSql.forEach { db.execSQL(it) }
                return
            }
            val temp = "${table}__migrate"
            val available = existingColumns(db, table)
            val shared = columns.filter { available.contains(it) }
            db.execSQL("DROP TABLE IF EXISTS `$temp`")
            db.execSQL(createSql.replaceFirst("`$table`", "`$temp`"))
            if (shared.isNotEmpty()) {
                val list = shared.joinToString(", ") { "`$it`" }
                db.execSQL("INSERT INTO `$temp` ($list) SELECT $list FROM `$table`")
            }
            db.execSQL("DROP TABLE IF EXISTS `$table`")
            db.execSQL("ALTER TABLE `$temp` RENAME TO `$table`")
            db.execSQL(dedupeSql)
            indexSql.forEach { db.execSQL(it) }
        }

        fun getInstance(context: Context): DelMessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DelMessageDatabase::class.java,
                    "delmessages.db"
                )
                    .addMigrations(
                        MIGRATION_1_4,
                        MIGRATION_4_6,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16
                    )
                    .fallbackToDestructiveMigration(true)
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
