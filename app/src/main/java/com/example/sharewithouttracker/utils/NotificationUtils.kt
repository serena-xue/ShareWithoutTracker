package com.example.sharewithouttracker.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.sharewithouttracker.TransparentClipboardActivity

fun showPersistentNotification(context: Context) {
    val channelId = "clipboard_trigger_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(channelId, "剪贴板触发器", NotificationManager.IMPORTANCE_LOW)
    notificationManager.createNotificationChannel(channel)

    val shareIntent = Intent(context, TransparentClipboardActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(TransparentClipboardActivity.EXTRA_SHARE_MODE, TransparentClipboardActivity.MODE_DIRECT_SHARE)
    }

    val sharePendingIntent = PendingIntent.getActivity(
        context,
        10020,
        shareIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val commentShareIntent = Intent(context, TransparentClipboardActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(TransparentClipboardActivity.EXTRA_SHARE_MODE, TransparentClipboardActivity.MODE_COMMENT_BEFORE_SHARE)
    }

    val commentSharePendingIntent = PendingIntent.getActivity(
        context,
        10021,
        commentShareIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_share)
        .setContentTitle("点击此处分享")
        .setContentText("")
        .setContentIntent(sharePendingIntent)
        .addAction(
            android.R.drawable.ic_menu_edit,
            "评论并分享",
            commentSharePendingIntent
        )
        .setOngoing(true) // 设为常驻通知
        .build()

    notificationManager.notify(1002, notification)
}

// 在原有文件末尾追加以下函数

fun showResultNotification(context: Context, message: String) {
    // 结果提示改为 Toast（不再使用通知栏 Notification）。
    // 使用 applicationContext 避免持有 Activity 引用。
    val safeContext = context.applicationContext
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(safeContext, message, Toast.LENGTH_SHORT).show()
    }
}