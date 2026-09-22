package me.weishu.kernelsu.ui.util

import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.core.tasks.BootKernelVersion
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * Extracts kernel build info (e.g. Linux version 5.10.xxx ...) from boot images.
 * Tries magiskboot unpack first when root is available, with pure Kotlin fallback.
 */
object BootKernelAnalyzer {

    private val MAGISKBOOT_PATHS = listOf(
        "/data/adb/ksu/bin/magiskboot",
        "/data/adb/magisk/magiskboot",
        "/data/adb/ap/bin/magiskboot"
    )

    val gzipMagic = byteArrayOf(0x1F, 0x8B.toByte())
    val xzMagic = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)
    val lz4FrameMagic = byteArrayOf(0x04, 0x22, 0x4D, 0x18)
    val lz4LegacyMagic = byteArrayOf(0x02, 0x21, 0x4C, 0x18)

    fun extractLinuxVersion(bootFile: File): String? {
        if (!bootFile.exists() || bootFile.length() < 64) return null

        // Attempt 1: Using magiskboot unpack if root is available
        val magiskbootVersion = extractViaMagiskboot(bootFile)
        if (!magiskbootVersion.isNullOrBlank()) {
            return magiskbootVersion
        }

        // Attempt 2: Pure Kotlin kernel parser and string scanner
        return extractViaKotlinParser(bootFile)
    }

    fun extractLinuxVersion(bootBytes: ByteArray): String? {
        if (bootBytes.size < 64) return null
        if (isCompressedKernel(bootBytes, 0)) {
            decompressPrefix(bootBytes)?.let { decompressed ->
                scanLinuxVersion(decompressed)?.let { return it }
            }
        }
        val block = BootKernelVersion.bootKernelBlock(bootBytes)
        if (block != null) {
            val (kernelOffset, kernelSize) = block
            if (kernelOffset < bootBytes.size && kernelSize > 0) {
                val sliceLen = minOf(kernelSize, bootBytes.size - kernelOffset)
                val kernelSlice = bootBytes.copyOfRange(kernelOffset, kernelOffset + sliceLen)
                if (isCompressedKernel(kernelSlice, 0)) {
                    decompressPrefix(kernelSlice)?.let { decompressed ->
                        scanLinuxVersion(decompressed)?.let { return it }
                    }
                } else {
                    scanLinuxVersion(kernelSlice)?.let { return it }
                }
            }
        }
        return scanLinuxVersion(bootBytes)
    }

    private fun extractViaMagiskboot(bootFile: File): String? {
        val hasRoot = try { Natives.version >= Natives.MINIMAL_SUPPORTED_KERNEL } catch (_: Throwable) { false }
        if (!hasRoot) return null

        val shell = getRootShell()
        val magiskbootBin = MAGISKBOOT_PATHS.firstOrNull { path ->
            shell.newJob().add("[ -x '$path' ]").exec().isSuccess
        } ?: return null

        val tempDirResult = shell.newJob().add("mktemp -d /data/local/tmp/boot_unpack_XXXXXX").exec()
        if (!tempDirResult.isSuccess || tempDirResult.out.isEmpty()) return null
        val workDir = tempDirResult.out.first().trim()

        try {
            val unpackCmd = "cd '$workDir' && '$magiskbootBin' unpack '${bootFile.absolutePath}'"
            val unpackResult = shell.newJob().add(unpackCmd).exec()
            if (!unpackResult.isSuccess) return null

            val stringsCmd = """
                if [ -f '$workDir/kernel' ]; then
                    strings '$workDir/kernel' | grep -m 1 "Linux version "
                fi
            """.trimIndent()
            val stringsResult = shell.newJob().add(stringsCmd).exec()
            if (stringsResult.isSuccess && stringsResult.out.isNotEmpty()) {
                val line = stringsResult.out.firstOrNull { it.contains("Linux version ") }
                if (!line.isNullOrBlank()) {
                    return line.trim()
                }
            }
        } finally {
            shell.newJob().add("rm -rf '$workDir'").exec()
        }
        return null
    }

    private fun extractViaKotlinParser(bootFile: File): String? {
        return try {
            RandomAccessFile(bootFile, "r").use { raf ->
                val length = raf.length()
                if (length < 64) return null
                val header = ByteArray(minOf(length, 4096).toInt())
                raf.readFully(header)

                val block = BootKernelVersion.bootKernelBlock(header)
                if (block != null) {
                    val (kernelOffset, kernelSize) = block
                    if (kernelOffset < length && kernelSize > 0) {
                        val readSize = minOf(kernelSize.toLong(), 64L * 1024 * 1024, length - kernelOffset).toInt()
                        raf.seek(kernelOffset.toLong())
                        val kernelBuf = ByteArray(readSize)
                        raf.readFully(kernelBuf)

                        if (isCompressedKernel(kernelBuf, 0)) {
                            decompressPrefix(kernelBuf)?.let { decompressed ->
                                scanLinuxVersion(decompressed)?.let { return it }
                            }
                        } else {
                            scanLinuxVersion(kernelBuf)?.let { return it }
                        }
                    }
                }

                // Fallback scan: read first 64MB of boot image and scan
                val scanLen = minOf(length, 64L * 1024 * 1024).toInt()
                raf.seek(0)
                val fullPrefix = ByteArray(scanLen)
                raf.readFully(fullPrefix)
                if (isCompressedKernel(fullPrefix, 0)) {
                    decompressPrefix(fullPrefix)?.let { decompressed ->
                        scanLinuxVersion(decompressed)?.let { return it }
                    }
                } else {
                    scanLinuxVersion(fullPrefix)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun scanLinuxVersion(bytes: ByteArray): String? {
        val target = "Linux version ".toByteArray(StandardCharsets.US_ASCII)
        val targetLen = target.size
        var bestMatch: String? = null

        for (i in 0..bytes.size - targetLen) {
            var matched = true
            for (j in 0 until targetLen) {
                if (bytes[i + j] != target[j]) {
                    matched = false
                    break
                }
            }
            if (!matched) continue

            // Must be followed by a digit (e.g. '5', '6') - eliminates format strings like "Linux version %s"
            val verStart = i + targetLen
            if (verStart >= bytes.size || bytes[verStart] !in '0'.code.toByte()..'9'.code.toByte()) {
                continue
            }

            var end = verStart
            var validAscii = true
            while (end < bytes.size && end < i + 1024) {
                val b = bytes[end]
                if (b == 0.toByte() || b == '\n'.code.toByte() || b == '\r'.code.toByte()) break
                if (b < 32 || b > 126) {
                    validAscii = false
                    break
                }
                end++
            }
            if (!validAscii) continue

            val raw = String(bytes, i, end - i, StandardCharsets.US_ASCII).trim()
            if (raw.length > 20) {
                // Complete banner with build info / timestamp
                if (raw.contains("#") || raw.contains("SMP") || raw.contains("PREEMPT")) {
                    return raw
                }
                if (bestMatch == null) {
                    bestMatch = raw
                }
            }
        }
        return bestMatch
    }

    fun isCompressedKernel(data: ByteArray, offset: Int): Boolean {
        return startsWithAt(data, offset, gzipMagic) ||
            startsWithAt(data, offset, xzMagic) ||
            startsWithAt(data, offset, lz4FrameMagic) ||
            startsWithAt(data, offset, lz4LegacyMagic)
    }

    fun decompressPrefix(data: ByteArray, maxDecompressed: Int = 64 * 1024 * 1024): ByteArray? {
        return try {
            if (startsWithAt(data, 0, lz4LegacyMagic)) {
                return decompressLz4Legacy(data, maxDecompressed)
            }
            val stream = when {
                startsWithAt(data, 0, gzipMagic) -> GZIPInputStream(ByteArrayInputStream(data))
                startsWithAt(data, 0, xzMagic) -> XZCompressorInputStream(ByteArrayInputStream(data))
                startsWithAt(data, 0, lz4FrameMagic) -> FramedLZ4CompressorInputStream(ByteArrayInputStream(data))
                else -> return null
            }
            stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                while (output.size() < maxDecompressed) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                if (output.size() > 0) output.toByteArray() else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun decompressLz4Legacy(data: ByteArray, maxDecompressed: Int = 64 * 1024 * 1024): ByteArray? {
        if (!startsWithAt(data, 0, lz4LegacyMagic)) return null
        var kpos = 4
        val out = ByteArrayOutputStream()
        val blockBuf = ByteArray(8 * 1024 * 1024)

        while (kpos + 4 <= data.size && out.size() < maxDecompressed) {
            val bsize = (data[kpos].toInt() and 0xFF) or
                ((data[kpos + 1].toInt() and 0xFF) shl 8) or
                ((data[kpos + 2].toInt() and 0xFF) shl 16) or
                ((data[kpos + 3].toInt() and 0xFF) shl 24)
            kpos += 4
            if (bsize <= 0 || bsize > 8 * 1024 * 1024 || kpos + bsize > data.size) break
            val decompressedLen = decompressLz4Block(data, kpos, bsize, blockBuf, 0, blockBuf.size)
            kpos += bsize
            if (decompressedLen <= 0) break
            out.write(blockBuf, 0, decompressedLen)

            val currentBytes = out.toByteArray()
            val banner = scanLinuxVersion(currentBytes)
            if (banner != null && (banner.contains("#") || banner.contains("SMP") || banner.contains("PREEMPT"))) {
                return currentBytes
            }
        }
        return if (out.size() > 0) out.toByteArray() else null
    }

    fun decompressLz4Block(
        src: ByteArray,
        srcOffset: Int,
        srcLength: Int,
        dest: ByteArray,
        destOffset: Int,
        maxDestLen: Int,
    ): Int {
        var pos = srcOffset
        val srcEnd = srcOffset + srcLength
        var outPos = destOffset
        val destEnd = destOffset + maxDestLen

        while (pos < srcEnd) {
            val token = src[pos++].toInt() and 0xFF
            var litLen = token ushr 4
            if (litLen == 15) {
                while (pos < srcEnd) {
                    val s = src[pos++].toInt() and 0xFF
                    litLen += s
                    if (s != 255) break
                }
            }
            if (outPos + litLen > destEnd || pos + litLen > srcEnd) break
            System.arraycopy(src, pos, dest, outPos, litLen)
            pos += litLen
            outPos += litLen
            if (pos >= srcEnd) break

            if (pos + 2 > srcEnd) break
            val matchOffset = (src[pos++].toInt() and 0xFF) or ((src[pos++].toInt() and 0xFF) shl 8)
            if (matchOffset == 0) break

            var matchLen = (token and 0x0F) + 4
            if ((token and 0x0F) == 15) {
                while (pos < srcEnd) {
                    val s = src[pos++].toInt() and 0xFF
                    matchLen += s
                    if (s != 255) break
                }
            }
            if (outPos + matchLen > destEnd) break

            var matchSrc = outPos - matchOffset
            if (matchSrc < 0) break
            for (k in 0 until matchLen) {
                dest[outPos++] = dest[matchSrc++]
            }
        }
        return outPos - destOffset
    }

    private fun startsWithAt(data: ByteArray, offset: Int, magic: ByteArray): Boolean {
        if (data.size < offset + magic.size) return false
        for (i in magic.indices) {
            if (data[offset + i] != magic[i]) return false
        }
        return true
    }

    data class ParsedKernelInfo(
        val rawBanner: String,
        val kernelVersion: String,
        val compiler: String? = null,
        val buildUserHost: String? = null,
        val buildDate: String? = null,
    )

    private fun extractTopLevelParentheses(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var startIndex = -1
        for (i in text.indices) {
            val c = text[i]
            if (c == '(') {
                if (depth == 0) {
                    startIndex = i + 1
                }
                depth++
            } else if (c == ')') {
                depth--
                if (depth == 0 && startIndex != -1) {
                    result.add(text.substring(startIndex, i).trim())
                    startIndex = -1
                } else if (depth < 0) {
                    depth = 0
                }
            }
        }
        return result
    }

    fun parseKernelBanner(raw: String): ParsedKernelInfo {
        val prefix = "Linux version "
        val content = if (raw.startsWith(prefix)) raw.substring(prefix.length) else raw

        val firstSpace = content.indexOf(' ')
        val firstParen = content.indexOf('(')
        val endIdx = when {
            firstSpace != -1 && firstParen != -1 -> minOf(firstSpace, firstParen)
            firstSpace != -1 -> firstSpace
            firstParen != -1 -> firstParen
            else -> content.length
        }
        val version = content.substring(0, endIdx).trim()

        val parens = extractTopLevelParentheses(content)
        var userHost: String? = null
        var compiler: String? = null
        for (p in parens) {
            if (p.contains("@") && userHost == null) {
                userHost = p
            } else if ((p.contains("clang", ignoreCase = true) || p.contains("gcc", ignoreCase = true)) && compiler == null) {
                compiler = p
            }
        }

        val hashIdx = content.lastIndexOf('#')
        val date = if (hashIdx != -1) {
            val rawDate = content.substring(hashIdx).trim()
            val cleaned = rawDate.replace(Regex("""^#\d+\s*(?:(?:SMP|PREEMPT|DYNAMIC|RT|_)+\s*)*""", RegexOption.IGNORE_CASE), "").trim()
            cleaned.ifBlank { rawDate }
        } else null

        return ParsedKernelInfo(
            rawBanner = raw,
            kernelVersion = if (version.isNotEmpty()) version else raw,
            compiler = compiler,
            buildUserHost = userHost,
            buildDate = date
        )
    }
}
