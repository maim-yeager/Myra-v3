package com.myra.assistant.database

import androidx.room.*

@Dao
interface MacroDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(macro: Macro)

    @Delete
    suspend fun delete(macro: Macro)

    @Query("SELECT * FROM macros")
    suspend fun getAllMacros(): List<Macro>

    @Query("SELECT * FROM macros WHERE trigger = :trigger LIMIT 1")
    suspend fun getMacroByTrigger(trigger: String): Macro?
}