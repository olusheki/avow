package com.avow.app.data.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VowDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_SESSIONS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS vow_sessions")
        onCreate(db)
    }

    companion object {
        const val DATABASE_NAME = "vow_database"
        const val DATABASE_VERSION = 1

        private const val CREATE_TABLE_SESSIONS = """
            CREATE TABLE vow_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                startTimeMillis INTEGER NOT NULL,
                endTimeMillis INTEGER NOT NULL,
                durationSeconds INTEGER NOT NULL,
                intrusionsBlocked INTEGER NOT NULL,
                allowedScreenTimeMs INTEGER NOT NULL,
                zenScore INTEGER NOT NULL
            )
        """
    }
}
