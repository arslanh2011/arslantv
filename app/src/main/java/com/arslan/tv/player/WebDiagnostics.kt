package com.arslan.tv.player

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures what the YouTube IFrame player is doing.
 *
 * Samsung's One UI drops app-level logcat output, so the same lines are also
 * appended to `<filesDir>/webview.log`, which can be read back with
 * `adb shell run-as com.arslan.tv cat files/webview.log`.
 */
class WebDiagnostics(context: Context) {

    private val file = File(context.filesDir, "webview.log")
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(line: String) {
        Log.d(TAG, line)
        runCatching {
            if (file.length() > MAX_BYTES) file.writeText("")
            file.appendText("${stamp.format(Date())}  $line\n")
        }
    }

    /**
     * Exposed to the page as `window.ArslanTv`. Every method is called on a
     * WebView worker thread, so [onVideo] must be safe off the main thread -
     * it only pushes into a StateFlow.
     */
    inner class Bridge(private val onVideo: (String) -> Unit = {}) {
        @JavascriptInterface
        fun onStateChange(state: String) = log("state=$state")

        @JavascriptInterface
        fun onVideoChange(videoId: String) {
            log("video=$videoId")
            if (videoId.isNotBlank()) onVideo(videoId)
        }

        @JavascriptInterface
        fun onError(code: String) = log("ERROR code=$code")

        @JavascriptInterface
        fun onReady(info: String) = log("ready $info")
    }

    private companion object {
        const val TAG = "YouTubePlayer"
        const val MAX_BYTES = 64 * 1024
    }
}
