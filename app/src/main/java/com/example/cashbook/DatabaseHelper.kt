package com.example.cashbook

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar
import java.util.Date

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "cashbook.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "transactions"
        private const val COLUMN_ID = "id"
        private const val COLUMN_DESCRIPTION = "description"
        private const val COLUMN_AMOUNT = "amount"
        private const val COLUMN_IS_CASH_IN = "is_cash_in"
        private const val COLUMN_DATE = "date"

        private val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DESCRIPTION TEXT NOT NULL,
                $COLUMN_AMOUNT REAL NOT NULL,
                $COLUMN_IS_CASH_IN INTEGER NOT NULL,
                $COLUMN_DATE INTEGER NOT NULL
            )
        """.trimIndent()
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun addTransaction(transaction: Transaction): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DESCRIPTION, transaction.description)
            put(COLUMN_AMOUNT, transaction.amount)
            put(COLUMN_IS_CASH_IN, if (transaction.isCashIn) 1 else 0)
            put(COLUMN_DATE, transaction.date.time)
        }
        val id = db.insert(TABLE_NAME, null, values)
        db.close()
        return if (id == -1L) 0 else 1 // Return 1 for success, 0 for failure
    }

    fun updateTransaction(transaction: Transaction): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DESCRIPTION, transaction.description)
            put(COLUMN_AMOUNT, transaction.amount)
            put(COLUMN_IS_CASH_IN, if (transaction.isCashIn) 1 else 0)
            put(COLUMN_DATE, transaction.date.time)
        }
        val rowsAffected = db.update(
            TABLE_NAME,
            values,
            "$COLUMN_ID = ?",
            arrayOf(transaction.id.toString())
        )
        db.close()
        return rowsAffected
    }

    fun getTransactionsByMonth(year: Int, month: Int): List<Transaction> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1) // Month is 0-based in Calendar
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        val endTime = calendar.timeInMillis

        val transactions = mutableListOf<Transaction>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_NAME WHERE $COLUMN_DATE BETWEEN ? AND ? ORDER BY $COLUMN_DATE DESC",
            arrayOf(startTime.toString(), endTime.toString())
        )
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION))
            val amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT))
            val isCashIn = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_CASH_IN)) == 1
            val date = Date(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)))
            transactions.add(Transaction(id, description, amount, isCashIn, date))
        }
        cursor.close()
        return transactions
    }

    fun deleteTransaction(id: Long): Int {
        val db = writableDatabase
        val result = db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return result
    }
}