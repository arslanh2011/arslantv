package com.arslan.tv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sits between ExoPlayer and the network: refuses ad-network requests outright
 * and rewrites HLS playlists through [AdBlock.stripAdBreaks] before the parser
 * sees them.
 *
 * Playlists are a few kilobytes, so buffering one in memory to rewrite it is
 * cheap. Media segments stream straight through untouched.
 */
@UnstableApi
class AdFilterDataSource(private val upstream: DataSource) : DataSource {

    private var rewritten: ByteArray? = null
    private var rewrittenOffset = 0
    private var openedUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val url = dataSpec.uri.toString()
        if (AdBlock.isBlockedRequest(url)) throw AdBlock.BlockedRequestException(url)

        openedUri = dataSpec.uri
        rewritten = null
        rewrittenOffset = 0

        val length = upstream.open(dataSpec)

        // Only whole-playlist reads are worth rewriting; ranged reads pass through.
        if (dataSpec.position != 0L || !AdBlock.looksLikePlaylist(url)) return length

        val raw = DataSourceUtil.readToEnd(upstream)
        val text = raw.decodeToString()
        val bytes = if (text.startsWith("#EXTM3U")) {
            AdBlock.stripAdBreaks(text).encodeToByteArray()
        } else {
            raw // Not a playlist after all - replay exactly what we consumed.
        }
        rewritten = bytes
        return bytes.size.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val source = rewritten ?: return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        val remaining = source.size - rewrittenOffset
        if (remaining <= 0) return C.RESULT_END_OF_INPUT
        val count = minOf(length, remaining)
        source.copyInto(buffer, offset, rewrittenOffset, rewrittenOffset + count)
        rewrittenOffset += count
        return count
    }

    override fun getUri(): Uri? = upstream.uri ?: openedUri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        rewritten = null
        rewrittenOffset = 0
        upstream.close()
    }

    @UnstableApi
    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = AdFilterDataSource(upstream.createDataSource())
    }
}
