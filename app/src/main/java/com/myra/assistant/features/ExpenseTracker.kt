package com.myra.assistant.features

import android.content.Context
import com.myra.assistant.database.AppDatabase
import com.myra.assistant.database.Expense
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ExpenseTracker(context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val expenseDao = database.expenseDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun addExpense(amount: Double, category: String, note: String, callback: (String) -> Unit) {
        scope.launch {
            val expense = Expense(
                amount = amount,
                category = category,
                note = note,
                timestamp = System.currentTimeMillis()
            )
            expenseDao.insert(expense)
            withContext(Dispatchers.Main) {
                callback("Expense of $amount added successfully!")
            }
        }
    }

    fun getMonthlyTotal(callback: (String) -> Unit) {
        scope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            val total = expenseDao.getTotalSince(calendar.timeInMillis) ?: 0.0
            withContext(Dispatchers.Main) {
                callback("This month's total expenses: $total")
            }
        }
    }

    fun getWeeklyTotal(callback: (String) -> Unit) {
        scope.launch {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            val total = expenseDao.getTotalSince(calendar.timeInMillis) ?: 0.0
            withContext(Dispatchers.Main) {
                callback("Last week's total expenses: $total")
            }
        }
    }

    fun getTodayTotal(callback: (String) -> Unit) {
        scope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            val total = expenseDao.getTotalSince(calendar.timeInMillis) ?: 0.0
            withContext(Dispatchers.Main) {
                callback("Today's expenses: $total")
            }
        }
    }

    fun getRecentExpenses(limit: Int = 10, callback: (List<Expense>) -> Unit) {
        scope.launch {
            val expenses = expenseDao.getAllExpenses().take(limit)
            withContext(Dispatchers.Main) {
                callback(expenses)
            }
        }
    }

    fun deleteExpense(expense: Expense, callback: (String) -> Unit) {
        scope.launch {
            expenseDao.delete(expense)
            withContext(Dispatchers.Main) {
                callback("Expense deleted")
            }
        }
    }
}