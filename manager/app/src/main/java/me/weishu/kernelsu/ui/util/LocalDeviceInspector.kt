package me.weishu.kernelsu.ui.util

import android.util.Log
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException

object LocalDeviceInspector {

    data class SlotInfo(
        val suffix: String,
        val current: Boolean,
        val kernel: String?,
        val avbRollbackIndex: Long?,
        val xblArb: ArbExtractor.ArbInfo?,
    )

    data class DeviceInfo(
        val model: String,
        val bootloader: String?,
        val secureArb: String?,
        val nonSecureArb: String?,
        val hasXblConfig: Boolean,
        val slots: List<SlotInfo>,
    )

    private const val TAG = "LocalDeviceInspector"
    private val byNameDirs = listOf("/dev/block/by-name", "/dev/block/bootdevice/by-name")
    private const val AVB_HEADER_SIZE = 128
    private const val MAX_XBL_CONFIG_SIZE = 16 shl 20

    fun inspect(
        cacheDir: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (String) -> Unit = {},
    ): DeviceInfo {
        val props = readProps(
            "ro.product.model", "ro.boot.bootloader", "ro.boot.slot_suffix",
            "ro.boot.hw.soc.sec-ar", "ro.boot.hw.soc.nonsec-ar",
        )
        val current = props["ro.boot.slot_suffix"].orEmpty()
        val suffixes = if (current.isEmpty()) listOf("") else listOf("_a", "_b")
        var hasXblConfig = false
        val slots = suffixes.map { suffix ->
            if (isCancelled()) throw CancellationException()
            onProgress("boot$suffix")
            val xbl = partition("xbl_config$suffix")
            if (xbl != null) hasXblConfig = true
            SlotInfo(
                suffix = suffix,
                current = suffix == current,
                kernel = partition("boot$suffix")?.let { readKernel(it, cacheDir, isCancelled) },
                avbRollbackIndex = partition("vbmeta$suffix")?.let { readAvbRollbackIndex(it) },
                xblArb = xbl?.let { readXblArb(it) },
            )
        }
        return DeviceInfo(
            model = props["ro.product.model"].orEmpty(),
            bootloader = props["ro.boot.bootloader"],
            secureArb = props["ro.boot.hw.soc.sec-ar"],
            nonSecureArb = props["ro.boot.hw.soc.nonsec-ar"],
            hasXblConfig = hasXblConfig,
            slots = slots,
        )
    }

    fun formatRollbackIndex(index: Long): String =
        if (index in 946684800L..4102444800L) {
            "${java.time.Instant.ofEpochSecond(index).atZone(java.time.ZoneOffset.UTC).toLocalDate()} ($index)"
        } else index.toString()

    private fun readProps(vararg names: String): Map<String, String> {
        val out = getRootShell().newJob().add(names.joinToString("; ") { "echo \"$it=$(getprop $it)\"" }).to(ArrayList(), null).exec().out
        return out.mapNotNull { line ->
            val key = line.substringBefore('=', "")
            val value = line.substringAfter('=', "").trim()
            if (key.isEmpty() || value.isEmpty()) null else key to value
        }.toMap()
    }

    private fun partition(name: String): SuFile? =
        byNameDirs.map { SuFile("$it/$name") }.firstOrNull { it.exists() }

    private fun readAvbRollbackIndex(file: SuFile): Long? = runCatching {
        val header = SuFileInputStream.open(file).use { it.readUpTo(AVB_HEADER_SIZE) }
        if (header.size < AVB_HEADER_SIZE || String(header, 0, 4, Charsets.US_ASCII) != "AVB0") null
        else ByteBuffer.wrap(header, 112, 8).long
    }.getOrNull()

    private fun readXblArb(file: SuFile): ArbExtractor.ArbInfo? = runCatching {
        val bytes = SuFileInputStream.open(file).use { it.readUpTo(MAX_XBL_CONFIG_SIZE) }
        ArbExtractor.extract(bytes)
    }.getOrNull()

    private fun readKernel(file: SuFile, cacheDir: File, isCancelled: () -> Boolean): String? {
        val temp = File.createTempFile("local_boot_", ".img", cacheDir)
        return try {
            // Copy in a dedicated root shell: a libsu FIFO is slow and fragile, and the shared shell must stay free.
            val copied = withNewRootShell {
                newJob().add("cat ${file.path.shellQuote()} > ${temp.absolutePath.shellQuote()}").exec().isSuccess
            }
            if (!copied || temp.length() == 0L) {
                Log.w(TAG, "copy ${file.path} failed")
                return null
            }
            if (isCancelled()) throw CancellationException()
            BootKernelAnalyzer.extractLinuxVersion(temp).also {
                if (it == null) Log.w(TAG, "no kernel banner in ${file.path} (${temp.length()} bytes)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "readKernel ${file.path} failed", e)
            null
        } finally {
            temp.delete()
        }
    }

    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val n = read(buffer, read, limit - read)
            if (n < 0) break
            read += n
        }
        return buffer.copyOf(read)
    }
}
