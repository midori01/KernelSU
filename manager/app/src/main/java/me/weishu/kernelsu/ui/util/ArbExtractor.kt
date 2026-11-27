package me.weishu.kernelsu.ui.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Extracts Qualcomm anti-rollback (ARB) index and OEM metadata from xbl_config images.
 * Pure Kotlin port of https://github.com/koaaN/arbextract.
 */
object ArbExtractor {

    data class ArbInfo(
        val majorVersion: Long,
        val minorVersion: Long,
        val arbIndex: Long,
    )

    fun extract(file: File): ArbInfo? {
        if (!file.exists() || file.length() < 64) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val size = raf.length()
                if (size < 64) return null
                val header = ByteArray(64)
                raf.readFully(header)

                // Check ELF magic: \x7f E L F
                if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
                    header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
                ) {
                    return null
                }
                // ELFCLASS64 = 2
                if (header[4].toInt() and 0xFF != 2) return null

                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val ePhoff = bb.getLong(0x20)
                val ePhentsz = bb.getShort(0x36).toInt() and 0xFFFF
                val ePhnum = bb.getShort(0x38).toInt() and 0xFFFF

                if (ePhoff <= 0 || ePhentsz < 56 || ePhnum <= 0) return null

                // Locate candidate HASH segment (last non-empty PT_NULL: p_type == 0, p_filesz > 0)
                var hashOff = 0L
                var hashSize = 0L
                val phBuf = ByteArray(56)

                for (i in ePhnum - 1 downTo 0) {
                    val phPos = ePhoff + i.toLong() * ePhentsz
                    if (phPos < 0L || phPos > size - 56L) continue
                    raf.seek(phPos)
                    raf.readFully(phBuf)
                    val phBb = ByteBuffer.wrap(phBuf).order(ByteOrder.LITTLE_ENDIAN)
                    val pType = phBb.getInt(0)
                    val pOffset = phBb.getLong(8)
                    val pFilesz = phBb.getLong(32)

                    if (pType == 0 && pFilesz > 0) {
                        hashOff = pOffset
                        hashSize = pFilesz
                        break
                    }
                }

                if (hashOff < 0L || hashSize <= 0L || hashSize > 16L * 1024 * 1024 || hashOff > size - hashSize) {
                    return null
                }

                raf.seek(hashOff)
                val seg = ByteArray(hashSize.toInt())
                raf.readFully(seg)
                extractFromHashSegment(seg)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun extract(bytes: ByteArray): ArbInfo? {
        if (bytes.size < 64) return null
        if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            return null
        }
        if (bytes[4].toInt() and 0xFF != 2) return null

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val ePhoff = bb.getLong(0x20)
        val ePhentsz = bb.getShort(0x36).toInt() and 0xFFFF
        val ePhnum = bb.getShort(0x38).toInt() and 0xFFFF

        if (ePhoff <= 0 || ePhentsz < 56 || ePhnum <= 0) return null

        var hashOff = 0L
        var hashSize = 0L
        val bytesLen = bytes.size.toLong()

        for (i in ePhnum - 1 downTo 0) {
            val phPosLong = ePhoff + i.toLong() * ePhentsz
            if (phPosLong < 0L || phPosLong > bytesLen - 56L) continue
            val phPos = phPosLong.toInt()
            val pType = bb.getInt(phPos)
            val pOffset = bb.getLong(phPos + 8)
            val pFilesz = bb.getLong(phPos + 32)

            if (pType == 0 && pFilesz > 0) {
                hashOff = pOffset
                hashSize = pFilesz
                break
            }
        }

        if (hashOff < 0L || hashSize <= 0L || hashSize > 16L * 1024 * 1024 || hashOff > bytesLen - hashSize) {
            return null
        }
        val seg = bytes.copyOfRange(hashOff.toInt(), (hashOff + hashSize).toInt())
        return extractFromHashSegment(seg)
    }

    private fun extractFromHashSegment(seg: ByteArray): ArbInfo? {
        val segSize = seg.size
        val segBb = ByteBuffer.wrap(seg).order(ByteOrder.LITTLE_ENDIAN)
        val maxScan = minOf(segSize - 36, 0x1000)

        var headerOff = -1
        var commonSz = 0L
        var qtiSz = 0L

        for (off in 0..maxScan step 4) {
            val version = segBb.getInt(off).toLong() and 0xFFFFFFFFL
            val cSz = segBb.getInt(off + 4).toLong() and 0xFFFFFFFFL
            val qSz = segBb.getInt(off + 8).toLong() and 0xFFFFFFFFL
            val oSz = segBb.getInt(off + 12).toLong() and 0xFFFFFFFFL
            val hSz = segBb.getInt(off + 16).toLong() and 0xFFFFFFFFL

            if (version !in 1L..10L) continue
            if (cSz > 0x1000L || oSz > 0x4000L || hSz > 0x4000L) continue
            if (off.toLong() + 36L + cSz + qSz + oSz > segSize.toLong()) continue

            headerOff = off
            commonSz = cSz
            qtiSz = qSz
            break
        }

        if (headerOff == -1) return null

        val oemMdOff = (headerOff.toLong() + 36L + commonSz + qtiSz).toInt()
        if (oemMdOff + 12 > segSize) return null

        val major = segBb.getInt(oemMdOff).toLong() and 0xFFFFFFFFL
        val minor = segBb.getInt(oemMdOff + 4).toLong() and 0xFFFFFFFFL
        val arb = segBb.getInt(oemMdOff + 8).toLong() and 0xFFFFFFFFL

        return ArbInfo(
            majorVersion = major,
            minorVersion = minor,
            arbIndex = arb,
        )
    }
}
