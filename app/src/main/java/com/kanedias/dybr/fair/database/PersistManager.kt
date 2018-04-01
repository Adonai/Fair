package com.kanedias.dybr.fair.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import com.kanedias.dybr.fair.entities.Account
import com.kanedias.dybr.fair.entities.OwnProfile

import java.sql.SQLException

/**
 * Helper class for managing OrmLite database and DAOs
 *
 * @author Kanedias
 */
class PersistManager(context: Context) : OrmLiteSqliteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // Dao fast access links
    val accDao: Dao<Account, Long> = getDao(Account::class.java)

    override fun onCreate(db: SQLiteDatabase, connectionSource: ConnectionSource) {
        try {
            TableUtils.createTable<Account>(connectionSource, Account::class.java)
        } catch (e: SQLException) {
            Log.e(TAG, "error creating DB " + DATABASE_NAME)
            throw RuntimeException(e)
        }

    }

    override fun onUpgrade(db: SQLiteDatabase, connectionSource: ConnectionSource, oldVer: Int, newVer: Int) {

    }

    fun clearAllTables() {
        try {
            TableUtils.clearTable<Account>(DbProvider.helper.getConnectionSource(), Account::class.java)
            TableUtils.clearTable<OwnProfile>(DbProvider.helper.getConnectionSource(), OwnProfile::class.java)
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }

    }

    companion object {

        private val TAG = "DATABASE"

        private val DATABASE_NAME = "fair.db"

        private val DATABASE_VERSION = 1
    }
}
