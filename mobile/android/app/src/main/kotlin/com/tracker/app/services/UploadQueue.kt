package com.tracker.app.services

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe queue for pending screenshot uploads.
 */
object UploadQueue {
    private const val TAG = "UploadQueue"

    data class UploadItem(
        val file: File,
        val triggerType: String,
        val callSessionId: String? = null,
        val source: String? = null,
        val capturedAt: Long = System.currentTimeMillis()
    )

    private val queue = ConcurrentLinkedQueue<UploadItem>()

    fun addSpacebarScreenshot(file: File) {
        queue.add(UploadItem(file = file, triggerType = "spacebar"))
        Log.d(TAG, "Spacebar queued. Size: ${queue.size}")
    }

    fun addCallScreenshots(result: CallScreenshotResult, source: String) {
        for (i in result.files.indices) {
            queue.add(UploadItem(
                file = result.files[i],
                triggerType = "call",
                callSessionId = result.sessionId,
                source = source,
                capturedAt = result.capturedTimes.getOrElse(i) { System.currentTimeMillis() }
            ))
        }
        Log.d(TAG, "${result.files.size} call screenshots queued.")
    }

    fun addRealtimeScreenshot(file: File) {
        queue.add(UploadItem(file = file, triggerType = "admin_pull"))
    }

    fun drainAsMapList(): List<Map<String, Any?>> {
        val items = mutableListOf<UploadItem>()
        while (queue.isNotEmpty()) {
            queue.poll()?.let { items.add(it) }
        }
        return items.map { item ->
            mapOf(
                "filePath" to item.file.absolutePath,
                "triggerType" to item.triggerType,
                "callSessionId" to item.callSessionId,
                "source" to item.source,
                "capturedAt" to item.capturedAt
            )
        }
    }

    fun size(): Int = queue.size
}
