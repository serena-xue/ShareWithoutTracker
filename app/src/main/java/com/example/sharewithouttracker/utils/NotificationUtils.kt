package com.example.sharewithouttracker.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.sharewithouttracker.TransparentClipboardActivity

fun showPersistentNotification(context: Context) {
    val channelId = "clipboard_trigger_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(channelId, "剪贴板触发器", NotificationManager.IMPORTANCE_LOW)
    notificationManager.createNotificationChannel(channel)

    val intent = Intent(context, TransparentClipboardActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_share)
        .setContentTitle("ShareWithoutTracker")
        .setContentText("点击此处获取剪贴板并分享")
        .setContentIntent(pendingIntent)
        .setOngoing(true) // 设为常驻通知
        .build()

    notificationManager.notify(1002, notification)
}

// 在原有文件末尾追加以下函数

fun showResultNotification(context: Context, message: String) {
    val channelId = "clipboard_result_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(channelId, "处理结果通知", NotificationManager.IMPORTANCE_HIGH)
    notificationManager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle("ShareWithoutTracker")
        .setContentText(message)
        .setAutoCancel(true) // 点击或稍后自动消失
        .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification) // 使用动态ID防止覆盖多条结果
}