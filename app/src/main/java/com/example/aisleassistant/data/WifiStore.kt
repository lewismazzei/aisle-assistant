package com.example.aisleassistant.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.aisleassistant.models.WifiInfoItem

class WifiStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE wifi_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_id INTEGER NOT NULL,
                ssid TEXT,
                bssid TEXT,
                rssi INTEGER,
                frequency INTEGER,
                capabilities TEXT,
                distance_mm INTEGER,
                distance_stddev_mm INTEGER,
                created_at_ms INTEGER NOT NULL,
                FOREIGN KEY(item_id) REFERENCES items(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Perform additive migrations to preserve existing data
        var v = oldVersion
        if (v < 2) {
            // Add distance columns to wifi_entries
            db.execSQL("ALTER TABLE wifi_entries ADD COLUMN distance_mm INTEGER")
            db.execSQL("ALTER TABLE wifi_entries ADD COLUMN distance_stddev_mm INTEGER")
            v = 2
        }
    }

    fun recordScanForItem(itemName: String, wifiItems: List<WifiInfoItem>) {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val itemId = getOrInsertItemId(db, itemName)
            // Insert entries
            for (w in wifiItems) {
                val cv = ContentValues().apply {
                    put("item_id", itemId)
                    put("ssid", w.ssid)
                    put("bssid", w.bssid)
                    put("rssi", w.rssi)
                    put("frequency", w.frequency)
                    put("capabilities", w.capabilities)
                    if (w.distanceMm != null) put("distance_mm", w.distanceMm)
                    if (w.distanceStdDevMm != null) put("distance_stddev_mm", w.distanceStdDevMm)
                    put("created_at_ms", now)
                }
                db.insert("wifi_entries", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun getOrInsertItemId(db: SQLiteDatabase, name: String): Long {
        // Try fetch existing
        var c: Cursor? = null
        try {
            c = db.rawQuery("SELECT id FROM items WHERE name = ?", arrayOf(name))
            if (c.moveToFirst()) {
                return c.getLong(0)
            }
        } finally {
            c?.close()
        }

        // Insert new
        val cv = ContentValues().apply { put("name", name) }
        val id = db.insertWithOnConflict("items", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        return if (id != -1L) id else {
            // Race: someone else inserted; fetch again
            val c2 = db.rawQuery("SELECT id FROM items WHERE name = ?", arrayOf(name))
            c2.use { if (it.moveToFirst()) it.getLong(0) else throw IllegalStateException("Failed to get item id") }
        }
    }

    companion object {
        private const val DB_NAME = "aisle_store.db"
        private const val DB_VERSION = 2
    }

    fun getItemsWithStats(): List<ItemWithStats> {
        val list = mutableListOf<ItemWithStats>()
        val db = readableDatabase
        val sql = """
            SELECT i.id, i.name, COUNT(e.id) as cnt, MAX(e.created_at_ms) as last_ms
            FROM items i
            LEFT JOIN wifi_entries e ON e.item_id = i.id
            GROUP BY i.id, i.name
            ORDER BY i.name COLLATE NOCASE
        """.trimIndent()
        val c = db.rawQuery(sql, emptyArray())
        c.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val name = it.getString(1)
                val cnt = it.getInt(2)
                val lastMs = if (!it.isNull(3)) it.getLong(3) else null
                list += ItemWithStats(id, name, cnt, lastMs)
            }
        }
        return list
    }

    fun getEntriesForItem(itemId: Long): List<WifiEntryRecord> {
        val list = mutableListOf<WifiEntryRecord>()
        val db = readableDatabase
        val sql = """
            SELECT ssid, bssid, rssi, frequency, capabilities, distance_mm, distance_stddev_mm, created_at_ms
            FROM wifi_entries
            WHERE item_id = ?
            ORDER BY created_at_ms DESC, id DESC
        """.trimIndent()
        val c = db.rawQuery(sql, arrayOf(itemId.toString()))
        c.use {
            while (it.moveToNext()) {
                list += WifiEntryRecord(
                    ssid = if (!it.isNull(0)) it.getString(0) else null,
                    bssid = if (!it.isNull(1)) it.getString(1) else null,
                    rssi = if (!it.isNull(2)) it.getInt(2) else null,
                    frequency = if (!it.isNull(3)) it.getInt(3) else null,
                    capabilities = if (!it.isNull(4)) it.getString(4) else null,
                    distanceMm = if (!it.isNull(5)) it.getInt(5) else null,
                    distanceStdDevMm = if (!it.isNull(6)) it.getInt(6) else null,
                    createdAtMs = it.getLong(7)
                )
            }
        }
        return list
    }

    fun getAllEntries(): List<AllEntryRecord> {
        val db = readableDatabase
        val list = mutableListOf<AllEntryRecord>()
        val sql = """
            SELECT i.name, e.ssid, e.bssid, e.rssi, e.frequency, e.capabilities, e.distance_mm, e.distance_stddev_mm, e.created_at_ms
            FROM wifi_entries e
            JOIN items i ON i.id = e.item_id
            ORDER BY i.name COLLATE NOCASE, e.created_at_ms DESC, e.id DESC
        """.trimIndent()
        val c = db.rawQuery(sql, emptyArray())
        c.use {
            while (it.moveToNext()) {
                list += AllEntryRecord(
                    itemName = it.getString(0),
                    ssid = if (!it.isNull(1)) it.getString(1) else null,
                    bssid = if (!it.isNull(2)) it.getString(2) else null,
                    rssi = if (!it.isNull(3)) it.getInt(3) else null,
                    frequency = if (!it.isNull(4)) it.getInt(4) else null,
                    capabilities = if (!it.isNull(5)) it.getString(5) else null,
                    distanceMm = if (!it.isNull(6)) it.getInt(6) else null,
                    distanceStdDevMm = if (!it.isNull(7)) it.getInt(7) else null,
                    createdAtMs = it.getLong(8)
                )
            }
        }
        return list
    }
}
