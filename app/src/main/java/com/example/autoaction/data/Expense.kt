package com.example.autoaction.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val date: Long,
    val imageUri: String
)
