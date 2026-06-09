package com.avow.app.data.history

import android.content.Context

class VowDatabase private constructor(context: Context) {

    private val dbHelper = VowDbHelper(context.applicationContext)
    private val dao = VowSessionDao(dbHelper)

    fun vowSessionDao(): VowSessionDao {
        return dao
    }

    companion object {
        @Volatile
        private var INSTANCE: VowDatabase? = null

        fun getDatabase(context: Context): VowDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VowDatabase(context).also { INSTANCE = it }
            }
        }
    }
}
