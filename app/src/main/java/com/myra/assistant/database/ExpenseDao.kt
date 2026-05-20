package com.myra.assistant.database

import androidx.room.*

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAllExpenses(): List<Expense>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :startTime")
    suspend fun getTotalSince(startTime: Long): Double?

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getExpensesSince(startTime: Long): List<Expense>
}