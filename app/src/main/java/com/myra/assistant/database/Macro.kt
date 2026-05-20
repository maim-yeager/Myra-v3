package com.myra.assistant.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macros")
data class Macro(
    @PrimaryKey val id: String,
    val trigger: String,
    val actions: String // JSON serialized list of actions
)