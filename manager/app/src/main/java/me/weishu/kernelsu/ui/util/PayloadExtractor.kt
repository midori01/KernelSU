package me.weishu.kernelsu.ui.util

import android.os.ParcelFileDescriptor
import chromeos_update_engine.UpdateMetadata.DeltaArchiveManifest
import chromeos_update_engine.UpdateMetadata.Extent
import chromeos_update_engine.UpdateMetadata.InstallOperation
import chromeos_update_engine.UpdateMetadata.PartitionUpdate
import me.weishu.kernelsu.core.utils.DataSourceChannel
import okhttp3.OkHttpClient
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.archivers.zip.ZipMethod
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import me.weishu.kernelsu.core.tasks.BootKernelVersion
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.CancellationException

object PayloadExtractor {

    private class NonClosingSeekableByteChannel(
        private val delegate: SeekableByteChannel,
    ) : SeekableByteChannel {
        override fun isOpen(): Boolean = delegate.isOpen
        override fun close() {
            // Do not close the underlying channel
        }
        override fun read(dst: ByteBuffer?): Int = delegate.read(dst)
        override fun write(src: ByteBuffer?): Int = delegate.write(src)
        override fun position(): Long = delegate.position()
        override fun position(newPosition: Long): SeekableByteChannel {
            delegate.position(newPosition)
            return this
        }
        override fun size(): Long = delegate.size()
        override fun truncate(size: Long): SeekableByteChannel {
            delegate.truncate(size)
            return this
        }
    }

    data class PayloadMetadata(
        val version: Long,
        val manifestSize: Long,
        val metadataSignatureSize: Int,
        val blockSize: Long,
        val partitionCount: Int,
        val securityPatchLevel: String?,
        val maxTimestamp: Long?,
        val sourceName: String,
        val isIncremental: Boolean = false,
    )

    data class PartitionItem(
        val name: String,
        val size: Long,
        val hashHex: String?,
        val operationsCount: Int,
        val isBoot: Boolean,
        val isXblConfig: Boolean,
        val isIncremental: Boolean = false,
    )

    fun openLocal(pfd: ParcelFileDescriptor, displayName: String): PayloadSession {
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
        val fileChannel = stream.channel

        val magicBuf = ByteBuffer.allocate(4)
        fileChannel.read(magicBuf, 0)
        magicBuf.flip()

        val isZip = magicBuf.remaining() >= 4 &&
            magicBuf.get(0) == 0x50.toByte() &&
            magicBuf.get(1) == 0x4B.toByte() &&
            magicBuf.get(2) == 0x03.toByte() &&
            magicBuf.get(3) == 0x04.toByte()

        val activeChannel: DataSourceChannel
        if (isZip) {
            val nonClosing = NonClosingSeekableByteChannel(fileChannel)
            val zipFile = ZipFile.builder()
                .setSeekableByteChannel(nonClosing)
                .setIgnoreLocalFileHeader(true)
                .get()
            try {
                val payloadEntry = zipFile.getEntry("payload.bin")
                    ?: throw IOException("payload.bin not found in ZIP archive")
                if (payloadEntry.method != ZipMethod.STORED.code) {
                    throw IOException("payload.bin in ZIP is compressed, only STORED is supported")
                }
                zipFile.getRawInputStream(payloadEntry)
                fileChannel.position(0)
                activeChannel = DataSourceChannel(fileChannel).slice(payloadEntry.dataOffset, payloadEntry.size)
            } finally {
                zipFile.close()
            }
        } else {
            fileChannel.position(0)
            activeChannel = DataSourceChannel(fileChannel)
        }

        return PayloadSession(activeChannel, displayName, pfd, stream)
    }

    fun openUrl(url: String, client: OkHttpClient): PayloadSession {
        val channel = DataSourceChannel(client, url)
        val magicBuf = ByteBuffer.allocate(4)
        channel.read(magicBuf, 0)
        magicBuf.flip()

        val isZip = magicBuf.remaining() >= 4 &&
            magicBuf.get(0) == 0x50.toByte() &&
            magicBuf.get(1) == 0x4B.toByte() &&
            magicBuf.get(2) == 0x03.toByte() &&
            magicBuf.get(3) == 0x04.toByte()

        val activeChannel: DataSourceChannel
        if (isZip) {
            val nonClosing = NonClosingSeekableByteChannel(channel)
            val zipFile = ZipFile.builder()
                .setSeekableByteChannel(nonClosing)
                .setIgnoreLocalFileHeader(true)
                .get()
            try {
                val payloadEntry = zipFile.getEntry("payload.bin")
                    ?: throw IOException("payload.bin not found in remote ZIP archive")
                if (payloadEntry.method != ZipMethod.STORED.code) {
                    throw IOException("payload.bin in remote ZIP is compressed, only STORED is supported")
                }
                zipFile.getRawInputStream(payloadEntry)
                activeChannel = channel.slice(payloadEntry.dataOffset, payloadEntry.size)
            } finally {
                zipFile.close()
            }
        } else {
            activeChannel = channel
        }

        val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "payload.bin" }
        return PayloadSession(activeChannel, name, null, null)
    }

    class PayloadSession(
        private val channel: DataSourceChannel,
        val sourceName: String,
        private val pfdToClose: ParcelFileDescriptor?,
        private val streamToClose: Closeable? = null,
    ) : Closeable {

        val metadata: PayloadMetadata
        val partitions: List<PartitionItem>

        private val manifest: DeltaArchiveManifest
        private val dataBase: Long

        init {
            val magicBuffer = ByteBuffer.allocate(4)
            channel.read(magicBuffer, 0)
            magicBuffer.flip()
            val magic = String(magicBuffer.array())
            if (magic != "CrAU") {
                throw IOException("Invalid payload magic: $magic (expected CrAU)")
            }

            val versionBuffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            channel.read(versionBuffer, 4)
            versionBuffer.flip()
            val version = versionBuffer.long
            if (version != 2L) {
                throw IOException("Unsupported payload version: $version (expected 2)")
            }

            val manifestLenBuffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            channel.read(manifestLenBuffer, 12)
            manifestLenBuffer.flip()
            val manifestLen = manifestLenBuffer.long.toInt()

            val sigLenBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            channel.read(sigLenBuffer, 20)
            sigLenBuffer.flip()
            val manifestSigLen = sigLenBuffer.int

            val manifestBytes = ByteBuffer.allocate(manifestLen)
            var readManifest = 0
            while (readManifest < manifestLen) {
                val r = channel.read(manifestBytes, 24L + readManifest)
                if (r <= 0) break
                readManifest += r
            }
            if (readManifest < manifestLen) {
                throw IOException("Unexpected end of payload stream: expected manifest size $manifestLen, got $readManifest")
            }
            manifestBytes.flip()
            manifest = ByteArrayInputStream(manifestBytes.array(), 0, readManifest).use {
                DeltaArchiveManifest.parseFrom(it)
            }

            dataBase = 24L + manifestLen + manifestSigLen

            val securityPatch = if (manifest.hasSecurityPatchLevel()) manifest.securityPatchLevel else null
            val maxTimestamp = if (manifest.hasMaxTimestamp()) manifest.maxTimestamp else null

            partitions = manifest.partitionsList.map { p ->
                val pName = p.partitionName
                val pSize = if (p.hasNewPartitionInfo() && p.newPartitionInfo.hasSize()) {
                    p.newPartitionInfo.size
                } else {
                    p.operationsList.flatMap { it.dstExtentsList }.maxOfOrNull { (it.startBlock + it.numBlocks) * manifest.blockSize } ?: 0L
                }
                val hashHex = if (p.hasNewPartitionInfo() && p.newPartitionInfo.hash != null && p.newPartitionInfo.hash.size() > 0) {
                    p.newPartitionInfo.hash.toByteArray().joinToString("") { "%02x".format(it) }
                } else null

                val isBoot = pName.equals("boot", ignoreCase = true) ||
                    pName.equals("boot_a", ignoreCase = true) ||
                    pName.equals("boot_b", ignoreCase = true)

                val isXbl = pName.equals("xbl_config", ignoreCase = true) ||
                    pName.equals("xbl_config_a", ignoreCase = true) ||
                    pName.equals("xbl_config_b", ignoreCase = true) ||
                    pName.equals("xbl", ignoreCase = true) ||
                    pName.equals("xbl_a", ignoreCase = true) ||
                    pName.equals("xbl_b", ignoreCase = true)

                val isInc = p.hasOldPartitionInfo() || p.operationsList.any { op ->
                    op.type == InstallOperation.Type.SOURCE_COPY ||
                        op.type == InstallOperation.Type.SOURCE_BSDIFF ||
                        op.type == InstallOperation.Type.BROTLI_BSDIFF ||
                        op.type == InstallOperation.Type.PUFFDIFF ||
                        op.type == InstallOperation.Type.LZ4DIFF_BSDIFF ||
                        op.type == InstallOperation.Type.LZ4DIFF_PUFFDIFF ||
                        op.type == InstallOperation.Type.ZUCCHINI
                }

                PartitionItem(
                    name = pName,
                    size = pSize,
                    hashHex = hashHex,
                    operationsCount = p.operationsCount,
                    isBoot = isBoot,
                    isXblConfig = isXbl,
                    isIncremental = isInc,
                )
            }

            metadata = PayloadMetadata(
                version = version,
                manifestSize = manifestLen.toLong(),
                metadataSignatureSize = manifestSigLen,
                blockSize = manifest.blockSize.toLong(),
                partitionCount = manifest.partitionsCount,
                securityPatchLevel = securityPatch,
                maxTimestamp = maxTimestamp,
                sourceName = sourceName,
                isIncremental = partitions.any { it.isIncremental },
            )
        }

        fun extractPartition(
            partitionName: String,
            outputFile: File,
            onProgress: (Float, String) -> Unit,
            isCancelled: () -> Boolean = { false },
        ) {
            val partItem = partitions.find { it.name == partitionName }
            if (partItem?.isIncremental == true) {
                throw IOException("Incremental partition cannot be extracted without base image")
            }

            val partition = manifest.partitionsList.find { it.partitionName == partitionName }
                ?: throw IOException("Partition $partitionName not found in payload")

            val outDir = outputFile.parentFile
            if (outDir != null && !outDir.exists()) {
                outDir.mkdirs()
            }

            FileChannel.open(
                outputFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.READ,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { outChannel ->
                val size = if (partition.hasNewPartitionInfo() && partition.newPartitionInfo.hasSize()) {
                    partition.newPartitionInfo.size
                } else {
                    partition.operationsList.flatMap { it.dstExtentsList }.maxOfOrNull { (it.startBlock + it.numBlocks) * manifest.blockSize } ?: 0L
                }

                if (size > 0) {
                    outChannel.write(ByteBuffer.allocate(1), size - 1)
                }

                val totalOps = partition.operationsCount
                val blockSize = manifest.blockSize.toLong()

                partition.operationsList.forEachIndexed { index, op ->
                    if (isCancelled()) {
                        throw CancellationException("Extraction cancelled")
                    }

                    val progressFraction = if (totalOps > 0) (index.toFloat() / totalOps) else 0f
                    onProgress(progressFraction, "${index + 1}/$totalOps")

                    processOperation(outChannel, op, blockSize)
                }

                onProgress(1f, "$totalOps/$totalOps")

                // Verify SHA-256 if available
                if (partition.hasNewPartitionInfo() && partition.newPartitionInfo.hasHash()) {
                    val expectedHash = partition.newPartitionInfo.hash.toByteArray()
                    val digest = MessageDigest.getInstance("SHA-256")
                    val checkBuf = ByteBuffer.allocate(512 * 1024)
                    var readOffset = 0L
                    while (readOffset < size) {
                        checkBuf.clear()
                        val bytesToRead = minOf(checkBuf.capacity().toLong(), size - readOffset).toInt()
                        checkBuf.limit(bytesToRead)
                        val r = outChannel.read(checkBuf, readOffset)
                        if (r <= 0) break
                        checkBuf.flip()
                        digest.update(checkBuf)
                        readOffset += r
                    }
                    if (readOffset < size) {
                        throw IOException("Failed to read all bytes for SHA-256 verification: $readOffset of $size bytes read")
                    }
                    val actualHash = digest.digest()
                    if (!expectedHash.contentEquals(actualHash)) {
                        fun toHex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
                        throw IOException("SHA-256 mismatch: expected ${toHex(expectedHash)}, actual ${toHex(actualHash)}")
                    }
                }
            }
        }

        fun extractPartitionToByteArray(partitionName: String): ByteArray {
            val partItem = partitions.find { it.name == partitionName }
            if (partItem?.isIncremental == true) {
                throw IOException("Incremental partition cannot be extracted without base image")
            }

            val partition = manifest.partitionsList.find { it.partitionName == partitionName }
                ?: throw IOException("Partition $partitionName not found in payload")
            val size = if (partition.hasNewPartitionInfo() && partition.newPartitionInfo.hasSize()) {
                partition.newPartitionInfo.size
            } else {
                partition.operationsList.flatMap { it.dstExtentsList }.maxOfOrNull { (it.startBlock + it.numBlocks) * manifest.blockSize } ?: 0L
            }
            if (size > 32 * 1024 * 1024) {
                throw IOException("Partition $partitionName is too large to extract to memory ($size bytes)")
            }
            val out = ByteArray(size.toInt())
            val blockSize = manifest.blockSize.toLong()

            for (op in partition.operationsList) {
                val dataType = op.getType()
                if (dataType == InstallOperation.Type.ZERO || dataType == InstallOperation.Type.DISCARD) continue
                val dataLen = op.dataLength.toInt()
                if (dataLen <= 0) continue

                val dataBuffer = ByteBuffer.allocate(dataLen)
                var totalRead = 0
                while (totalRead < dataLen) {
                    val r = channel.read(dataBuffer, dataBase + op.dataOffset + totalRead)
                    if (r <= 0) break
                    totalRead += r
                }
                if (totalRead < dataLen) {
                    throw IOException("Unexpected EOF reading payload operation at offset ${op.dataOffset}")
                }
                dataBuffer.flip()
                val rawBytes = dataBuffer.array()
                val decompressed = when (dataType) {
                    InstallOperation.Type.REPLACE -> rawBytes.copyOf(totalRead)
                    InstallOperation.Type.REPLACE_BZ -> BZip2CompressorInputStream(ByteArrayInputStream(rawBytes, 0, totalRead)).use { it.readBytes() }
                    InstallOperation.Type.REPLACE_XZ -> XZCompressorInputStream(ByteArrayInputStream(rawBytes, 0, totalRead)).use { it.readBytes() }
                    InstallOperation.Type.REPLACE_ZSTD -> {
                        if (ZstdUtils.isZstdCompressionAvailable()) {
                            ZstdCompressorInputStream(ByteArrayInputStream(rawBytes, 0, totalRead)).use { it.readBytes() }
                        } else throw IOException("ZSTD not supported")
                    }
                    else -> throw IOException("Unsupported operation type: $dataType")
                }
                var offsetInDecomp = 0
                for (ext in op.dstExtentsList) {
                    if (offsetInDecomp >= decompressed.size) break
                    val extSize = (ext.numBlocks * blockSize).toInt()
                    val toCopy = minOf(extSize, decompressed.size - offsetInDecomp)
                    if (ext.startBlock != -1L && ext.startBlock >= 0) {
                        val dstPos = (ext.startBlock * blockSize).toInt()
                        if (dstPos + toCopy <= out.size) {
                            System.arraycopy(decompressed, offsetInDecomp, out, dstPos, toCopy)
                        }
                    }
                    offsetInDecomp += toCopy
                }
            }
            return out
        }

        private fun processOperation(
            outChannel: FileChannel,
            operation: InstallOperation,
            blockSize: Long,
        ) {
            val dataType = operation.getType()
            if (dataType == InstallOperation.Type.ZERO || dataType == InstallOperation.Type.DISCARD) {
                return
            }

            val dataLen = operation.dataLength.toInt()
            if (dataLen <= 0) return

            val dataBuffer = ByteBuffer.allocate(dataLen)
            var totalRead = 0
            while (totalRead < dataLen) {
                val r = channel.read(dataBuffer, dataBase + operation.dataOffset + totalRead)
                if (r <= 0) break
                totalRead += r
            }
            if (totalRead < dataLen) {
                throw IOException("Unexpected EOF reading payload operation at offset ${operation.dataOffset}, read $totalRead of $dataLen bytes")
            }
            dataBuffer.flip()

            val rawBytes = dataBuffer.array()
            val extents = operation.dstExtentsList

            when (dataType) {
                InstallOperation.Type.REPLACE -> {
                    writeToExtents(outChannel, extents, blockSize, rawBytes, totalRead)
                }

                InstallOperation.Type.REPLACE_BZ -> {
                    BZip2CompressorInputStream(ByteArrayInputStream(rawBytes, 0, totalRead)).use { bz ->
                        decompressToExtents(outChannel, extents, blockSize, bz)
                    }
                }

                InstallOperation.Type.REPLACE_XZ -> {
                    XZCompressorInputStream(ByteArrayInputStream(rawBytes, 0, totalRead)).use { xz ->
                        decompressToExtents(outChannel, extents, blockSize, xz)
                    }
                }

                InstallOperation.Type.REPLACE_ZSTD -> {
                    if (ZstdUtils.isZstdCompressionAvailable()) {
                        ZstdCompressorInputStream(ByteArrayInputStream(rawBytes, 0, totalRead)).use { zstd ->
                            decompressToExtents(outChannel, extents, blockSize, zstd)
                        }
                    } else {
                        throw IOException("ZSTD compression not supported on this device")
                    }
                }

                else -> throw IOException("Unsupported operation type: $dataType")
            }
        }

        private fun writeToExtents(
            outChannel: FileChannel,
            extents: List<Extent>,
            blockSize: Long,
            data: ByteArray,
            dataLen: Int,
        ) {
            var offsetInData = 0
            for (extent in extents) {
                if (offsetInData >= dataLen) break
                val extentSize = extent.numBlocks * blockSize
                val toWrite = minOf(extentSize, (dataLen - offsetInData).toLong()).toInt()
                if (extent.startBlock != -1L && extent.startBlock >= 0) {
                    val buffer = ByteBuffer.wrap(data, offsetInData, toWrite)
                    var writeOffset = extent.startBlock * blockSize
                    while (buffer.hasRemaining()) {
                        val w = outChannel.write(buffer, writeOffset)
                        if (w <= 0) break
                        writeOffset += w
                    }
                }
                offsetInData += toWrite
            }
        }

        private fun decompressToExtents(
            outChannel: FileChannel,
            extents: List<Extent>,
            blockSize: Long,
            inputStream: InputStream,
        ) {
            var currentExtentIdx = 0
            var currentExtentWritten = 0L
            val buffer = ByteArray(64 * 1024)

            while (currentExtentIdx < extents.size) {
                val extent = extents[currentExtentIdx]
                val extentSize = extent.numBlocks * blockSize
                val extentRemaining = extentSize - currentExtentWritten

                val toRead = minOf(buffer.size.toLong(), extentRemaining).toInt()
                val read = inputStream.read(buffer, 0, toRead)
                if (read <= 0) break

                if (extent.startBlock != -1L && extent.startBlock >= 0) {
                    val byteBuffer = ByteBuffer.wrap(buffer, 0, read)
                    var writeOffset = extent.startBlock * blockSize + currentExtentWritten
                    while (byteBuffer.hasRemaining()) {
                        val w = outChannel.write(byteBuffer, writeOffset)
                        if (w <= 0) break
                        writeOffset += w
                    }
                }
                currentExtentWritten += read

                if (currentExtentWritten >= extentSize) {
                    currentExtentIdx++
                    currentExtentWritten = 0L
                }
            }
        }

        fun extractKernelVersionFast(
            partitionName: String,
            onProgress: ((current: Int, total: Int, message: String) -> Unit)? = null
        ): String? {
            val partItem = partitions.find { it.name == partitionName }
            if (partItem?.isIncremental == true) return null

            val partition = manifest.partitionsList.find { it.partitionName == partitionName } ?: return null
            val nonZeroOps = partition.operationsList.filter {
                it.type != InstallOperation.Type.ZERO && it.type != InstallOperation.Type.DISCARD && it.dataLength > 0
            }
            if (nonZeroOps.isEmpty()) return null

            fun decompressOp(op: InstallOperation): ByteArray? {
                val dataLen = op.dataLength.toInt()
                if (dataLen <= 0) return null
                val dataBuffer = ByteBuffer.allocate(dataLen)
                var totalRead = 0
                try {
                    while (totalRead < dataLen) {
                        val r = channel.read(dataBuffer, dataBase + op.dataOffset + totalRead)
                        if (r <= 0) break
                        totalRead += r
                    }
                } catch (_: Throwable) {
                    return null
                }
                if (totalRead < dataLen) return null
                dataBuffer.flip()
                val rawBytes = dataBuffer.array()
                return try {
                    when (op.type) {
                        InstallOperation.Type.REPLACE -> rawBytes.copyOf(totalRead)
                        InstallOperation.Type.REPLACE_BZ -> BZip2CompressorInputStream(ByteArrayInputStream(rawBytes, 0, totalRead)).use { it.readBytes() }
                        InstallOperation.Type.REPLACE_XZ -> XZCompressorInputStream(ByteArrayInputStream(rawBytes, 0, totalRead)).use { it.readBytes() }
                        InstallOperation.Type.REPLACE_ZSTD -> {
                            if (ZstdUtils.isZstdCompressionAvailable()) {
                                ZstdCompressorInputStream(ByteArrayInputStream(rawBytes, 0, totalRead)).use { it.readBytes() }
                            } else null
                        }
                        else -> null
                    }
                } catch (_: Throwable) {
                    null
                }
            }

            // Step 1: Read the boot header operation (covering block 0)
            val op0 = nonZeroOps.find { op -> op.dstExtentsList.any { it.startBlock == 0L } } ?: nonZeroOps.first()
            onProgress?.invoke(1, nonZeroOps.size, "Reading boot header…")

            val decompressedOp0 = decompressOp(op0) ?: return null

            // Check if banner is immediately visible in Op 0
            val op0Banner = BootKernelAnalyzer.scanLinuxVersion(decompressedOp0)
            if (!op0Banner.isNullOrBlank() && (op0Banner.contains("#") || op0Banner.contains("PREEMPT") || op0Banner.length > 20)) {
                return op0Banner
            }

            val block = BootKernelVersion.bootKernelBlock(decompressedOp0)
            var kernelOffset = -1
            var kernelSize = 0
            var isCompressed = false

            if (block != null) {
                kernelOffset = block.first
                kernelSize = block.second
                isCompressed = BootKernelAnalyzer.isCompressedKernel(decompressedOp0, kernelOffset)
            } else if (decompressedOp0.size >= 4) {
                isCompressed = BootKernelAnalyzer.isCompressedKernel(decompressedOp0, 0)
                if (isCompressed) kernelOffset = 0
            }

            if (isCompressed) {
                // Compressed kernel (e.g. Pixel LZ4/GZIP): sequential stream accumulation
                val bootAccumulator = ByteArrayOutputStream()
                val maxAccumulateSize = 32 * 1024 * 1024
                bootAccumulator.write(decompressedOp0)

                if (kernelOffset >= 0 && decompressedOp0.size > kernelOffset + 4) {
                    val kernelSlice = decompressedOp0.copyOfRange(kernelOffset, decompressedOp0.size)
                    val decomp = BootKernelAnalyzer.decompressPrefix(kernelSlice, maxAccumulateSize)
                    if (decomp != null) {
                        val banner = BootKernelAnalyzer.scanLinuxVersion(decomp)
                        if (!banner.isNullOrBlank()) return banner
                    }
                }

                var scanned = 1
                for (op in nonZeroOps) {
                    if (op == op0) continue
                    if (bootAccumulator.size() >= maxAccumulateSize) break
                    scanned++
                    onProgress?.invoke(scanned, nonZeroOps.size, "Scanning kernel ($scanned/${nonZeroOps.size})…")

                    val decompressed = decompressOp(op) ?: continue
                    bootAccumulator.write(decompressed)
                    val accumulated = bootAccumulator.toByteArray()

                    if (kernelOffset >= 0 && accumulated.size > kernelOffset + 4) {
                        val kernelSlice = accumulated.copyOfRange(kernelOffset, accumulated.size)
                        val decomp = BootKernelAnalyzer.decompressPrefix(kernelSlice, maxAccumulateSize)
                        if (decomp != null) {
                            val banner = BootKernelAnalyzer.scanLinuxVersion(decomp)
                            if (!banner.isNullOrBlank() && (banner.contains("#") || banner.length > 20)) {
                                return banner
                            }
                        }
                    }
                }
                val finalAccum = bootAccumulator.toByteArray()
                return BootKernelAnalyzer.extractLinuxVersion(finalAccum)
            } else {
                // Uncompressed kernel (e.g. Android GKI / Xiaomi / modern devices):
                // Each operation is an independent compressor chunk.
                // In uncompressed ARM64/x86 kernels, the linux_banner lives in the .rodata section,
                // which is placed directly after the .text section (typically ~35% - 55% into the kernel image).
                val blockSize = manifest.blockSize.toLong()
                val kStartByte = if (kernelOffset >= 0) kernelOffset.toLong() else 4096L
                val kEndByte = if (kernelSize > 0) kStartByte + kernelSize else 40L * 1024 * 1024

                // Find candidate operations covering the kernel image
                val kernelOps = nonZeroOps.filter { op ->
                    op != op0 && op.dstExtentsList.any { ext ->
                        val s = ext.startBlock * blockSize
                        val e = s + ext.numBlocks * blockSize
                        maxOf(s, kStartByte) < minOf(e, kEndByte)
                    }
                }

                // Center-out target at ~42% of kernel where .rodata typically resides
                val targetByte = kStartByte + ((kEndByte - kStartByte) * 0.42).toLong()

                val sortedOps = kernelOps.sortedBy { op ->
                    val firstExtent = op.dstExtentsList.firstOrNull()
                    val start = (firstExtent?.startBlock ?: 0L) * blockSize
                    kotlin.math.abs(start - targetByte)
                }

                var probed = 1
                val totalToProbe = sortedOps.size + 1
                for (op in sortedOps) {
                    probed++
                    onProgress?.invoke(probed, totalToProbe, "Scanning kernel ($probed/$totalToProbe)…")
                    val decomp = decompressOp(op) ?: continue
                    val banner = BootKernelAnalyzer.scanLinuxVersion(decomp)
                    if (!banner.isNullOrBlank() && (banner.contains("#") || banner.contains("PREEMPT") || banner.length > 20)) {
                        return banner
                    }
                }

                // Fallback scan remaining operations
                for (op in nonZeroOps) {
                    if (op == op0 || sortedOps.contains(op)) continue
                    probed++
                    onProgress?.invoke(probed, nonZeroOps.size, "Scanning partition ($probed/${nonZeroOps.size})…")
                    val decomp = decompressOp(op) ?: continue
                    val banner = BootKernelAnalyzer.scanLinuxVersion(decomp)
                    if (!banner.isNullOrBlank()) return banner
                }

                return op0Banner
            }
        }

        fun extractArbFast(partitionName: String): ArbExtractor.ArbInfo? {
            val bytes = try {
                extractPartitionToByteArray(partitionName)
            } catch (_: Throwable) {
                null
            } ?: return null
            return ArbExtractor.extract(bytes)
        }

        override fun close() {
            try {
                channel.close()
            } catch (_: Throwable) {
            }
            try {
                streamToClose?.close()
            } catch (_: Throwable) {
            }
            try {
                pfdToClose?.close()
            } catch (_: Throwable) {
            }
        }
    }
}
