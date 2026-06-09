package com.avow.app.data.history

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.OptIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class VowSessionDao(private val dbHelper: VowDbHelper) {

    // A shared flow to emit an event whenever data is mutated, triggering re-querying of all flows.
    private val mutationTrigger = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit)
    }

    suspend fun insert(session: VowSession) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("startTimeMillis", session.startTimeMillis)
            put("endTimeMillis", session.endTimeMillis)
            put("durationSeconds", session.durationSeconds)
            put("intrusionsBlocked", session.intrusionsBlocked)
            put("allowedScreenTimeMs", session.allowedScreenTimeMs)
            put("zenScore", session.zenScore)
        }
        db.insert("vow_sessions", null, values)
        mutationTrigger.emit(Unit)
    }

    fun getAllSessions(): Flow<List<VowSession>> {
        return mutationTrigger.flatMapLatest {
            flow {
                val sessions = mutableListOf<VowSession>()
                val db = dbHelper.readableDatabase
                val cursor = db.query(
                    "vow_sessions",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "startTimeMillis DESC"
                )
                cursor.use { c ->
                    val idCol = c.getColumnIndex("id")
                    val startCol = c.getColumnIndex("startTimeMillis")
                    val endCol = c.getColumnIndex("endTimeMillis")
                    val durCol = c.getColumnIndex("durationSeconds")
                    val intCol = c.getColumnIndex("intrusionsBlocked")
                    val screenCol = c.getColumnIndex("allowedScreenTimeMs")
                    val zenCol = c.getColumnIndex("zenScore")

                    while (c.moveToNext()) {
                        sessions.add(
                            VowSession(
                                id = if (idCol >= 0) c.getInt(idCol) else 0,
                                startTimeMillis = if (startCol >= 0) c.getLong(startCol) else 0L,
                                endTimeMillis = if (endCol >= 0) c.getLong(endCol) else 0L,
                                durationSeconds = if (durCol >= 0) c.getLong(durCol) else 0L,
                                intrusionsBlocked = if (intCol >= 0) c.getInt(intCol) else 0,
                                allowedScreenTimeMs = if (screenCol >= 0) c.getLong(screenCol) else 0L,
                                zenScore = if (zenCol >= 0) c.getInt(zenCol) else 0
                            )
                        )
                    }
                }
                emit(sessions)
            }.flowOn(Dispatchers.IO)
        }
    }

    fun getSessionCount(): Flow<Int> {
        return mutationTrigger.flatMapLatest {
            flow {
                val db = dbHelper.readableDatabase
                val cursor = db.rawQuery("SELECT COUNT(*) FROM vow_sessions", null)
                var count = 0
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        count = c.getInt(0)
                    }
                }
                emit(count)
            }.flowOn(Dispatchers.IO)
        }
    }

    fun getTotalDurationSeconds(): Flow<Long?> {
        return mutationTrigger.flatMapLatest {
            flow {
                val db = dbHelper.readableDatabase
                val cursor = db.rawQuery("SELECT SUM(durationSeconds) FROM vow_sessions", null)
                var sum: Long? = null
                cursor.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) {
                        sum = c.getLong(0)
                    }
                }
                emit(sum)
            }.flowOn(Dispatchers.IO)
        }
    }

    fun getTotalIntrusionsBlocked(): Flow<Int?> {
        return mutationTrigger.flatMapLatest {
            flow {
                val db = dbHelper.readableDatabase
                val cursor = db.rawQuery("SELECT SUM(intrusionsBlocked) FROM vow_sessions", null)
                var sum: Int? = null
                cursor.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) {
                        sum = c.getInt(0)
                    }
                }
                emit(sum)
            }.flowOn(Dispatchers.IO)
        }
    }

    fun getTotalAllowedScreenTimeMs(): Flow<Long?> {
        return mutationTrigger.flatMapLatest {
            flow {
                val db = dbHelper.readableDatabase
                val cursor = db.rawQuery("SELECT SUM(allowedScreenTimeMs) FROM vow_sessions", null)
                var sum: Long? = null
                cursor.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) {
                        sum = c.getLong(0)
                    }
                }
                emit(sum)
            }.flowOn(Dispatchers.IO)
        }
    }

    fun getAverageZenScore(): Flow<Double?> {
        return mutationTrigger.flatMapLatest {
            flow {
                val db = dbHelper.readableDatabase
                val cursor = db.rawQuery("SELECT AVG(zenScore) FROM vow_sessions", null)
                var avg: Double? = null
                cursor.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) {
                        avg = c.getDouble(0)
                    }
                }
                emit(avg)
            }.flowOn(Dispatchers.IO)
        }
    }
}
