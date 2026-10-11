package com.kazumaproject.markdownhelperkeyboard.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DropRemovedFeatureTablesMigrationTest {
    @Test
    fun migration50To51DropsRemovedFeatureTablesAndKeepsLearning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("drop-tables-${System.nanoTime()}.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE learn_table (id INTEGER PRIMARY KEY, input TEXT NOT NULL, out TEXT NOT NULL)")
                        AppDatabase.DROPPED_TABLES_50_51.forEach {
                            db.execSQL("CREATE TABLE `$it` (id INTEGER PRIMARY KEY)")
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO learn_table (input, out) VALUES ('きょう', '今日')")
            AppDatabase.MIGRATION_50_51.migrate(db)
            AppDatabase.DROPPED_TABLES_50_51.forEach { table ->
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use {
                    assertFalse(table, it.moveToFirst())
                }
            }
            db.query("SELECT out FROM learn_table").use {
                assertTrue(it.moveToFirst())
                assertEquals("今日", it.getString(0))
            }
        } finally {
            helper.close()
        }
    }
}
