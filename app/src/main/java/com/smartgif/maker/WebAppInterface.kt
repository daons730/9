package com.smartgif.maker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class WebAppInterface(private val context: Context) {

    companion object {
        private const val TAG = "WebAppInterface"
        private const val CHANNEL_ID = "smart_gif_downloads"
        private const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    /**
     * Download Base64 data (e.g. data:image/gif;base64,...) directly to device Downloads
     */
    @JavascriptInterface
    fun downloadBase64(base64Data: String, filename: String, mimeType: String) {
        try {
            val cleanFilename = if (filename.isNotBlank()) {
                if (!filename.lowercase().endsWith(".gif") && mimeType.contains("gif")) {
                    "$filename.gif"
                } else {
                    filename
                }
            } else {
                "smart_gif_${System.currentTimeMillis()}.gif"
            }

            val actualMimeType = if (mimeType.isNotBlank()) mimeType else "image/gif"

            val rawData = if (base64Data.contains(",")) {
                base64Data.substringAfter(",")
            } else {
                base64Data
            }

            val fileBytes = Base64.decode(rawData, Base64.DEFAULT)
            var savedUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped Storage for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cleanFilename)
                    put(MediaStore.MediaColumns.MIME_TYPE, actualMimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SmartGIF")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream: OutputStream ->
                        stream.write(fileBytes)
                        stream.flush()
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    savedUri = uri
                }
            } else {
                // Legacy storage for Android 9 and below
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "SmartGIF"
                )
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val targetFile = File(downloadsDir, cleanFilename)
                FileOutputStream(targetFile).use { stream ->
                    stream.write(fileBytes)
                    stream.flush()
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(actualMimeType),
                    null
                )
                savedUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )
            }

            // Show Toast & Notification
            (context as? MainActivity)?.runOnUiThread {
                Toast.makeText(
                    context,
                    "GIF가 다운로드 폴더(SmartGIF)에 저장되었습니다: $cleanFilename",
                    Toast.LENGTH_LONG
                ).show()
            }

            savedUri?.let { showDownloadCompleteNotification(cleanFilename, it, actualMimeType) }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving GIF file", e)
            (context as? MainActivity)?.runOnUiThread {
                Toast.makeText(
                    context,
                    "GIF 다운로드 저장 실패: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        (context as? MainActivity)?.runOnUiThread {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "GIF 다운로드 알림"
            val descriptionText = "스마트 GIF 생성기 다운로드 완료 알림"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showDownloadCompleteNotification(filename: String, fileUri: Uri, mimeType: String) {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("GIF 다운로드 완료")
                .setContentText(filename)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post notification: ${e.message}")
        }
    }
}

