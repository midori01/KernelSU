package me.weishu.kernelsu.ui.screen.flash

import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.FlashResult
import me.weishu.kernelsu.ui.util.LkmSelection
import me.weishu.kernelsu.ui.util.downloadBoot
import me.weishu.kernelsu.ui.util.flashAnyKernelZip
import me.weishu.kernelsu.ui.util.flashModule
import me.weishu.kernelsu.ui.util.installBoot
import me.weishu.kernelsu.ui.util.restoreBoot
import me.weishu.kernelsu.ui.util.uninstallPermanently
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

enum class FlashingStatus {
    FLASHING,
    SUCCESS,
    FAILED
}

enum class UninstallType(val icon: ImageVector, val title: Int, val message: Int) {
    TEMPORARY(
        Icons.Rounded.RemoveModerator,
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message
    ),
    PERMANENT(
        Icons.Rounded.DeleteForever,
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message
    ),
    RESTORE_STOCK_IMAGE(
        Icons.Rounded.RestartAlt,
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message
    ),
    NONE(Icons.Rounded.Adb, 0, 0)
}

@Parcelize
sealed class FlashIt : Parcelable {
    @Parcelize
    data class FlashBoot(
        val boot: Uri? = null,
        val lkm: LkmSelection,
        val ota: Boolean,
        val partition: String? = null,
        val allowShell: Boolean = false,
        val enableAdb: Boolean = false,
        val backup: Boolean = false,
    ) : FlashIt()

    @Parcelize
    data class DownloadBoot(
        val url: String,
        val partition: String,
        val lkm: LkmSelection,
        val allowShell: Boolean = false,
        val enableAdb: Boolean = false,
        val backup: Boolean = false,
    ) : FlashIt()

    @Parcelize
    data class FlashModules(val uris: List<Uri>) : FlashIt()

    @Parcelize
    data class FlashAnyKernel(val uri: Uri, val slot: String = "") : FlashIt()

    @Parcelize
    data object FlashRestore : FlashIt()

    @Parcelize
    data object FlashUninstall : FlashIt()

    @Parcelize
    data object BackupBoot : FlashIt()

    @Parcelize
    data class FlashBootImg(val uri: Uri, val slot: String = "") : FlashIt()
}

fun flashModulesSequentially(
    uris: List<Uri>,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    for (uri in uris) {
        flashModule(uri, onStdout, onStderr).apply {
            if (code != 0) {
                return FlashResult(code, err, showReboot)
            }
        }
    }
    return FlashResult(0, "", true)
}

fun flashIt(
    flashIt: FlashIt,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    return when (flashIt) {
        is FlashIt.FlashBoot -> installBoot(
            flashIt.boot,
            flashIt.lkm,
            flashIt.ota,
            flashIt.partition,
            flashIt.allowShell,
            flashIt.enableAdb,
            flashIt.backup,
            onStdout,
            onStderr
        )

        is FlashIt.DownloadBoot -> downloadBoot(
            flashIt.url,
            flashIt.partition,
            flashIt.lkm,
            flashIt.allowShell,
            flashIt.enableAdb,
            flashIt.backup,
            onStdout,
            onStderr
        )

        is FlashIt.FlashModules -> {
            flashModulesSequentially(flashIt.uris, onStdout, onStderr)
        }

        is FlashIt.FlashAnyKernel -> {
            flashAnyKernelZip(flashIt.uri, flashIt.slot, onStdout, onStderr)
        }

        FlashIt.FlashRestore -> restoreBoot(onStdout, onStderr)
        FlashIt.FlashUninstall -> uninstallPermanently(onStdout, onStderr)

        is FlashIt.BackupBoot -> {
            val slot = com.topjohnwu.superuser.ShellUtils.fastCmd(
                me.weishu.kernelsu.ui.util.getRootShell(true),
                "getprop ro.boot.slot_suffix"
            ).trim()
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val outputPath = "/sdcard/Download/boot_backup_${timestamp}.img"
            val cmd = """
                slot="$slot"
                if [ -z "§slot" ]; then
                    slot=§(getprop ro.boot.slot_suffix)
                    if [ -z "§slot" ]; then
                        s=§(getprop ro.boot.slot)
                        [ -n "§s" ] && slot="_§s"
                    fi
                fi

                target="boot§slot"
                boot_dev=""
                for candidate in \
                    "/dev/block/by-name/§target" \
                    "/dev/block/bootdevice/by-name/§target" \
                    /dev/block/platform/*/by-name/§target \
                    /dev/block/platform/*/*/by-name/§target; do
                    if [ -e "§candidate" ]; then
                        boot_dev="§candidate"
                        break
                    fi
                done
                if [ -z "§boot_dev" ]; then
                    boot_dev=§(find /dev/block \( -type b -o -type c -o -type l \) -iname "§target" 2>/dev/null | head -n 1)
                fi
                if [ -z "§boot_dev" ]; then
                    for uevent in /sys/dev/block/*/uevent; do
                        [ -f "§uevent" ] || continue
                        devname=§(grep -E '^DEVNAME=' "§uevent" | cut -d= -f2)
                        partname=§(grep -E '^PARTNAME=' "§uevent" | cut -d= -f2)
                        if [ -n "§partname" ] && [ "§(echo "§target" | tr '[:upper:]' '[:lower:]')" = "§(echo "§partname" | tr '[:upper:]' '[:lower:]')" ]; then
                            boot_dev="/dev/block/§devname"
                            break
                        fi
                    done
                fi

                if [ -n "§boot_dev" ]; then
                    real_dev=§(readlink -f "§boot_dev" 2>/dev/null || echo "§boot_dev")
                    [ -b "§real_dev" ] && boot_dev="§real_dev"
                fi

                if [ -z "§boot_dev" ] || [ ! -b "§boot_dev" ]; then
                    echo "Error: Cannot find boot§slot partition block device!" >&2
                    exit 1
                fi

                mkdir -p /sdcard/Download 2>/dev/null || true
                out_dir=§(dirname "$outputPath")
                mkdir -p "§out_dir" 2>/dev/null || true

                echo "Backing up §boot_dev to $outputPath..."
                if dd if="§boot_dev" of="$outputPath" bs=1M; then
                    sync
                    if [ -s "$outputPath" ]; then
                        echo "boot.img backed up successfully to: $outputPath"
                    else
                        echo "Error: Output backup file is empty!" >&2
                        rm -f "$outputPath" 2>/dev/null || true
                        exit 1
                    fi
                else
                    sync
                    echo "Error: dd command failed while reading from §boot_dev!" >&2
                    rm -f "$outputPath" 2>/dev/null || true
                    exit 1
                fi
            """.trimIndent().replace('§', '$')
            me.weishu.kernelsu.ui.util.flashBootBackup(cmd, onStdout, onStderr)
        }

        is FlashIt.FlashBootImg -> {
            val slot = if (flashIt.slot.isNotEmpty()) {
                flashIt.slot
            } else {
                val s = com.topjohnwu.superuser.ShellUtils.fastCmd(
                    me.weishu.kernelsu.ui.util.getRootShell(true),
                    "getprop ro.boot.slot_suffix"
                ).trim()
                if (s.isNotEmpty()) s else {
                    val s2 = com.topjohnwu.superuser.ShellUtils.fastCmd(
                        me.weishu.kernelsu.ui.util.getRootShell(true),
                        "getprop ro.boot.slot"
                    ).trim()
                    if (s2.isNotEmpty()) "_$s2" else ""
                }
            }
            val cacheFile = java.io.File(me.weishu.kernelsu.ksuApp.cacheDir, "boot_flash.img")
            try {
                me.weishu.kernelsu.ksuApp.contentResolver.openInputStream(flashIt.uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                onStderr("Failed to read selected file: ${e.message}")
                return FlashResult(-1, e.message ?: "Failed to read file", false)
            }
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                onStderr("Error: The selected file is empty or unreadable!")
                return FlashResult(-1, "File is empty or unreadable", false)
            }
            cacheFile.setReadable(true, false)

            // Verify Android boot image magic: first 8 bytes must be "ANDROID!"
            val magic = ByteArray(8)
            val bytesRead = try {
                cacheFile.inputStream().use { it.read(magic) }
            } catch (_: Exception) { -1 }
            if (bytesRead < 8 || !magic.contentEquals("ANDROID!".toByteArray(Charsets.US_ASCII))) {
                cacheFile.delete()
                onStderr("Error: Invalid boot image! The file does not have the required 'ANDROID!' header.")
                return FlashResult(-1, "Invalid boot image header", false)
            }

            val cmd = """
                target="boot$slot"
                boot_dev=""
                for candidate in \
                    "/dev/block/by-name/§target" \
                    "/dev/block/bootdevice/by-name/§target" \
                    /dev/block/platform/*/by-name/§target \
                    /dev/block/platform/*/*/by-name/§target; do
                    if [ -e "§candidate" ]; then
                        boot_dev="§candidate"
                        break
                    fi
                done
                if [ -z "§boot_dev" ]; then
                    boot_dev=§(find /dev/block \( -type b -o -type c -o -type l \) -iname "§target" 2>/dev/null | head -n 1)
                fi
                if [ -z "§boot_dev" ]; then
                    for uevent in /sys/dev/block/*/uevent; do
                        [ -f "§uevent" ] || continue
                        devname=§(grep -E '^DEVNAME=' "§uevent" | cut -d= -f2)
                        partname=§(grep -E '^PARTNAME=' "§uevent" | cut -d= -f2)
                        if [ -n "§partname" ] && [ "§(echo "§target" | tr '[:upper:]' '[:lower:]')" = "§(echo "§partname" | tr '[:upper:]' '[:lower:]')" ]; then
                            boot_dev="/dev/block/§devname"
                            break
                        fi
                    done
                fi

                if [ -n "§boot_dev" ]; then
                    real_dev=§(readlink -f "§boot_dev" 2>/dev/null || echo "§boot_dev")
                    [ -b "§real_dev" ] && boot_dev="§real_dev"
                fi

                if [ -z "§boot_dev" ] || [ ! -b "§boot_dev" ]; then
                    echo "Error: Cannot find boot$slot partition block device!" >&2
                    exit 1
                fi

                img_path="${cacheFile.absolutePath}"
                img_size=§(wc -c < "§img_path")
                dev_size=§(blockdev --getsize64 "§boot_dev" 2>/dev/null || true)
                if [ -n "§dev_size" ] && [ "§dev_size" -gt 0 ] 2>/dev/null; then
                    if [ "§img_size" -gt "§dev_size" ]; then
                        echo "Error: Image size (§img_size bytes) exceeds partition size (§dev_size bytes)!" >&2
                        exit 1
                    fi
                    echo "Image size: §img_size bytes, Partition size: §dev_size bytes"
                fi

                echo "Flashing boot image to §boot_dev..."
                if dd if="§img_path" of="§boot_dev" bs=1M conv=fsync; then
                    sync
                    echo "Flashing completed successfully!"
                else
                    sync
                    echo "Error: dd command failed during flashing!" >&2
                    exit 1
                fi
            """.trimIndent().replace('§', '$')

            val result = try {
                me.weishu.kernelsu.ui.util.flashBootImgCmd(cmd, onStdout, onStderr)
            } finally {
                cacheFile.delete()
            }
            result
        }
    }
}

@Composable
fun FlashEffect(
    flashIt: FlashIt,
    text: String,
    logContent: StringBuilder,
    onTextUpdate: (String) -> Unit,
    onShowRebootChange: (Boolean) -> Unit,
    onFlashingStatusChange: (FlashingStatus) -> Unit,
    enabled: Boolean = true
) {
    LaunchedEffect(enabled) {
        if (!enabled || text.isNotEmpty()) {
            return@LaunchedEffect
        }
        var currentText = text
        val mainHandler = Handler(Looper.getMainLooper())
        withContext(Dispatchers.IO) {
            flashIt(flashIt, onStdout = {
                val tempText = "$it\n"
                if (tempText.startsWith("[H[J")) { // clear command
                    currentText = tempText.substring(6)
                } else {
                    currentText += tempText
                }
                mainHandler.post {
                    onTextUpdate(currentText)
                }
                logContent.append(it).append("\n")
            }, onStderr = {
                currentText += "$it\n"
                mainHandler.post {
                    onTextUpdate(currentText)
                }
                logContent.append(it).append("\n")
            }).apply {
                if (code != 0) {
                    currentText += "Error code: $code.\n $err Please save and check the log.\n"
                    mainHandler.post {
                        onTextUpdate(currentText)
                    }
                }
                if (showReboot) {
                    currentText += "\n\n\n"
                    mainHandler.post {
                        onTextUpdate(currentText)
                        onShowRebootChange(true)
                    }
                }
                mainHandler.post {
                    onFlashingStatusChange(if (code == 0) FlashingStatus.SUCCESS else FlashingStatus.FAILED)
                }
            }
        }
    }
}

fun saveLog(
    logContent: StringBuilder,
    scope: CoroutineScope,
    showMessage: (String) -> Unit
): () -> Unit {
    return {
        scope.launch {
            val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
            val date = format.format(Date())
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "KernelSU_install_log_${date}.log"
            )
            file.writeText(logContent.toString())
            showMessage("Log saved to ${file.absolutePath}")
        }
    }
}

private const val JAILBREAK_WARNING_COUNTDOWN = 10

@Composable
fun JailbreakFlashWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(JAILBREAK_WARNING_COUNTDOWN) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000.milliseconds)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(android.R.string.dialog_alert_title)) },
        text = {
            Text(
                stringResource(R.string.jailbreak_flash_warning),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = countdown == 0
            ) {
                Text(
                    if (countdown > 0)
                        stringResource(R.string.jailbreak_flash_warning_countdown, countdown)
                    else
                        stringResource(R.string.install_next)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
