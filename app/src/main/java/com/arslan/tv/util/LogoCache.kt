package com.arslan.tv.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny memory + disk cache for channel logos.
 *
 * A full image-loading library would be the obvious choice, but logos are a
 * handful of small PNGs that never change, and keeping them out of the
 * dependency list is what keeps the app's startup as light as it is.
 */
object LogoCache {

    private const val TAG = "LogoCache"
    private const val MAX_EDGE_PX = 480
    private const val MEMORY_BUDGET_BYTES = 6 * 1024 * 1024

    private val memory = object : LruCache<String, Bitmap>(MEMORY_BUDGET_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * One lock per URL, so two cards wanting the same logo fetch it once while
     * different logos still download in parallel.
     */
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun load(context: Context, url: String): Bitmap? {
        memory.get(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            val file = File(cacheDir(context), hash(url))
            decode(file)?.let { cached ->
                memory.put(url, cached)
                return@withContext cached
            }

            locks.getOrPut(url) { Mutex() }.withLock {
                // Another caller may have finished while we waited for the lock.
                memory.get(url)?.let { return@withLock it }
                if (!download(url, file)) return@withLock null
                decode(file)?.also { memory.put(url, it) }
            }
        }
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "logos").apply { mkdirs() }

    private fun download(url: String, target: File): Boolean {
        val temp = File(target.parentFile, target.name + ".part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ArslanTV/1.0 (Android TV)")
        }
        return try {
            if (connection.responseCode !in 200..299) return false
            connection.inputStream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            temp.renameTo(target)
        } catch (e: Exception) {
            Log.w(TAG, "Logo download failed: $url", e)
            temp.delete()
            false
        } finally {
            connection.disconnect()
        }
    }

    /** Decodes downscaled - a 900 px channel avatar is far bigger than a card needs. */
    private fun decode(file: File): Bitmap? {
        if (!file.isFile || file.length() == 0L) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            var sampleSize = 1
            val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
            while (longestEdge / sampleSize > MAX_EDGE_PX * 2) sampleSize *= 2

            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }.getOrNull()
    }

    private fun hash(url: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
