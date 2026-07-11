package com.avow.app.data.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VowDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_SESSIONS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // NEVER drop-and-recreate here — that wipes every user's focus history on a version bump.
        // Migrations must be additive (ALTER TABLE / CREATE TABLE IF NOT EXISTS) and step one version
        // at a time so an upgrade spanning several versions composes correctly.
        var version = oldVersion
        while (version < newVersion) {
            when (version) {
                // 1 -> 2: legacy dev-only schema; ensure the table exists without touching data.
                1 -> db.execSQL(CREATE_TABLE_SESSIONS)
                // 2 -> 3 (future): db.execSQL("ALTER TABLE vow_sessions ADD COLUMN newCol ...")
            }
            version++
        }
    }

    companion object {
        const val DATABASE_NAME = "vow_database"
        const val DATABASE_VERSION = 2

        private const val CREATE_TABLE_SESSIONS = """
            CREATE TABLE IF NOT EXISTS vow_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                startTimeMillis INTEGER NOT NULL,
                endTimeMillis INTEGER NOT NULL,
                durationSeconds INTEGER NOT NULL,
                pickups INTEGER NOT NULL,
                allowedScreenTimeMs INTEGER NOT NULL,
                zenScore INTEGER NOT NULL
            )
        """
    }
}
