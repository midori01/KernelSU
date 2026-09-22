package me.weishu.kernelsu.core.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.io.input.BoundedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel

class DataSourceChannel private constructor(
    private val client: OkHttpClient?,
    private val url: String?,
    private val fileChannel: FileChannel?,
    private val startOffset: Long,
    private val totalSize: Long,
) : SeekableByteChannel {

    constructor(client: OkHttpClient, url: String) : this(client, url, null, 0, fetchTotalSize(client, url))
    constructor(fileChannel: FileChannel) : this(null, null, fileChannel, 0, fileChannel.size())

    private var pos = 0L
    private var open = true
    private var cache: ByteArray? = null
    private var cacheStart = -1L

    override fun isOpen(): Boolean = open

    override fun size(): Long = totalSize

    override fun position(): Long = pos

    override fun position(newPosition: Long): DataSourceChannel {
        if (!open) throw ClosedChannelException()
        if (newPosition < 0) throw IllegalArgumentException("Position out of bounds: $newPosition")
        pos = newPosition
        return this
    }

    override fun close() {
        open = false
        cache = null
        fileChannel?.close()
    }

    override fun read(dst: ByteBuffer): Int {
        val bytesRead = read(dst, pos)
        if (bytesRead > 0) pos += bytesRead
        return bytesRead
    }

    fun read(dst: ByteBuffer, position: Long): Int {
        if (!open) throw ClosedChannelException()
        if (position < 0) throw IllegalArgumentException("Position out of bounds: $position")
        if (position >= totalSize) return -1

        if (fileChannel != null) {
            val startPosition = startOffset + position
            val endPosition = minOf(position + dst.remaining(), totalSize)
            val readLen = (endPosition - position).toInt()
            if (readLen <= 0) return 0
            val oldLimit = dst.limit()
            dst.limit(dst.position() + readLen)
            var totalRead = 0
            while (dst.hasRemaining()) {
                val r = fileChannel.read(dst, startPosition + totalRead)
                if (r <= 0) break
                totalRead += r
            }
            dst.limit(oldLimit)
            return if (totalRead == 0 && readLen > 0) -1 else totalRead
        }

        val requestSize = dst.remaining()
        if (requestSize == 0) return 0

        if (requestSize > DIRECT_READ_THRESHOLD) {
            return handleLargeRead(dst, position)
        }

        var totalBytesRead = 0
        var currentPos = position
        if (isCacheHit(currentPos, 1)) {
            val bytesFromCache = readFromCache(dst, currentPos)
            totalBytesRead += bytesFromCache
            currentPos += bytesFromCache
        }

        if (dst.hasRemaining() && currentPos < totalSize) {
            loadCache(currentPos, requestSize)
            totalBytesRead += if (isCacheHit(currentPos, dst.remaining())) {
                readFromCache(dst, currentPos)
            } else {
                readDirectly(dst, currentPos)
            }
        }

        return totalBytesRead
    }

    fun slice(offset: Long, sliceSize: Long): DataSourceChannel {
        if (offset == 0L && sliceSize == totalSize) return this
        if (offset < 0 || sliceSize <= 0 || offset + sliceSize > totalSize) {
            throw IllegalArgumentException("Invalid slice parameters")
        }
        return DataSourceChannel(client, url, fileChannel, startOffset + offset, sliceSize)
    }

    /** Reads up to [length] bytes at [position] into a fresh array. */
    fun readFully(position: Long, length: Long): ByteArray? {
        if (length > Int.MAX_VALUE) return null
        val buffer = ByteBuffer.allocate(length.toInt())
        var totalRead = 0
        while (buffer.hasRemaining() && position + totalRead < totalSize) {
            val r = read(buffer, position + totalRead)
            if (r <= 0) break
            totalRead += r
        }
        if (totalRead == 0) return null
        buffer.flip()
        val data = ByteArray(totalRead)
        buffer.get(data)
        return data
    }

    fun streamRead(position: Long, length: Long): InputStream {
        val endPosition = minOf(position + length, totalSize) + startOffset
        val startPosition = startOffset + position
        val readLength = endPosition - startPosition

        if (fileChannel != null) {
            fileChannel.position(startPosition)
            return BoundedInputStream.builder()
                .setInputStream(Channels.newInputStream(fileChannel))
                .setMaxCount(readLength)
                .setPropagateClose(false)
                .get()
        }

        val request = Request.Builder()
            .url(url!!)
            .header("Range", "bytes=$startPosition-${endPosition - 1}")
            .build()

        val response = client!!.newCall(request).execute()
        if (response.code != 206) {
            response.close()
            throw IOException("Unexpected response code ${response.code}")
        }
        return response.body.byteStream()
    }

    private fun handleLargeRead(dst: ByteBuffer, position: Long): Int {
        var bytesFromCache = 0
        var currentPos = position
        if (isCacheHit(currentPos, 1)) {
            bytesFromCache = readFromCache(dst, currentPos)
            currentPos += bytesFromCache
        }

        return if (dst.hasRemaining() && currentPos < totalSize) {
            bytesFromCache + readDirectly(dst, currentPos)
        } else {
            bytesFromCache
        }
    }

    private fun loadCache(requestPos: Long, requestSize: Int) {
        val lastCacheEnd = cache?.let { cacheStart + it.size } ?: -1L
        val newCacheSize: Int
        val newCacheStart: Long

        if (requestSize > SEQ_READ_THRESHOLD || lastCacheEnd == requestPos) {
            newCacheSize = SEQ_READ_CACHE_SIZE
            newCacheStart = requestPos
        } else {
            newCacheSize = RANDOM_READ_CACHE_SIZE
            newCacheStart = maxOf(0L, requestPos - newCacheSize / 2)
        }

        loadCacheAt(newCacheStart, newCacheSize)
    }

    private fun loadCacheAt(cacheStart: Long, cacheSize: Int) {
        val maxEnd = minOf(cacheStart + cacheSize, totalSize)
        val start = maxOf(0L, maxEnd - cacheSize)

        val buffer = ByteBuffer.allocate((maxEnd - start).toInt())
        var totalRead = 0
        while (buffer.hasRemaining() && start + totalRead < totalSize) {
            val bytesRead = readDirectly(buffer, start + totalRead)
            if (bytesRead <= 0) break
            totalRead += bytesRead
        }
        if (totalRead == 0 && buffer.capacity() > 0) {
            throw IOException("Failed to fill cache: reached EOF at $start")
        }

        cache = buffer.array()
        this.cacheStart = start
    }

    private fun isCacheHit(pos: Long, bytesToRead: Int): Boolean {
        val cache = cache ?: return false
        val cacheEnd = cacheStart + cache.size
        val readEnd = minOf(pos + bytesToRead, totalSize)
        return pos >= cacheStart && readEnd <= cacheEnd
    }

    private fun readFromCache(dst: ByteBuffer, position: Long): Int {
        val cache = cache ?: return 0
        val relativePos = position - cacheStart
        val available = minOf(dst.remaining().toLong(), cache.size - relativePos).toInt()
        dst.put(cache, relativePos.toInt(), available)
        return available
    }

    private fun readDirectly(dst: ByteBuffer, position: Long): Int {
        if (fileChannel != null) {
            val startPosition = startOffset + position
            val endPosition = minOf(position + dst.remaining(), totalSize)
            val readLen = (endPosition - position).toInt()
            if (readLen <= 0) return 0
            val oldLimit = dst.limit()
            dst.limit(dst.position() + readLen)
            val r = fileChannel.read(dst, startPosition)
            dst.limit(oldLimit)
            return maxOf(0, r)
        }

        Channels.newChannel(streamRead(position, dst.remaining().toLong())).use { channel ->
            var totalBytesRead = 0
            while (true) {
                val bytesRead = channel.read(dst)
                if (bytesRead <= 0) break
                totalBytesRead += bytesRead
            }
            return totalBytesRead
        }
    }

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()

    override fun truncate(size: Long): DataSourceChannel = throw NonWritableChannelException()

    companion object {
        private const val RANDOM_READ_CACHE_SIZE = 32 * 1024
        private const val SEQ_READ_CACHE_SIZE = 128 * 1024
        private const val SEQ_READ_THRESHOLD = 2048
        private const val DIRECT_READ_THRESHOLD = 64 * 1024

        private fun fetchTotalSize(client: OkHttpClient, url: String): Long {
            // First attempt: HEAD request
            try {
                val headRequest = Request.Builder().url(url).head().build()
                client.newCall(headRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val contentLength = response.header("Content-Length")?.toLongOrNull()
                        if (contentLength != null && contentLength > 0L) {
                            return contentLength
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fallback to GET with Range
            }

            // Second attempt: GET request with Range: bytes=0-0 (handles servers blocking HEAD or requiring Range)
            val rangeRequest = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .build()

            client.newCall(rangeRequest).execute().use { response ->
                if (response.code == 206) {
                    val contentRange = response.header("Content-Range")
                    if (contentRange != null) {
                        val slashIdx = contentRange.lastIndexOf('/')
                        if (slashIdx != -1) {
                            val totalStr = contentRange.substring(slashIdx + 1).trim()
                            val total = totalStr.toLongOrNull()
                            if (total != null && total > 0L) {
                                return total
                            }
                        }
                    }
                }
                if (response.isSuccessful) {
                    val contentLength = response.header("Content-Length")?.toLongOrNull()
                    if (contentLength != null && contentLength > 0L) {
                        return contentLength
                    }
                }
                throw IOException("Could not determine file size or server does not support byte ranges (HTTP ${response.code})")
            }
        }
    }
}
