package com.example.autoaction.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.autoaction.data.AppDatabase
import com.example.autoaction.data.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.Toast

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_SAVE_EXPENSE) {
            val amount = intent.getDoubleExtra("amount", 0.0)
            val currency = intent.getStringExtra("currency") ?: "$"
            val merchant = intent.getStringExtra("merchant")

            val db = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                db.expenseDao().insertExpense(
                    Expense(
                        amount = amount,
                        currency = currency,
                        merchant = merchant,
                        date = System.currentTimeMillis(),
                        imageUri = "" // We don't have URI easily here unless passed, MVP doesn't strictly require image link for expense yet
                    )
                )
                CoroutineScope(Dispatchers.Main).launch {
                     Toast.makeText(context, "Expense Saved: $currency$amount", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Cancel notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(NotificationHelper.NOTIFICATION_ID_ACTION)
        }
    }
}
