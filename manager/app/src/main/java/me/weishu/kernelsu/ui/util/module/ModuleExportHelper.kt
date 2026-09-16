package me.weishu.kernelsu.ui.util.module

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.ui.util.getRootShell
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.zip.Deflater

object ModuleExportHelper {
    private const val TAG = "ModuleExportHelper"

    private const val MAGISK_UPDATE_BINARY = """#!/sbin/sh

#################
# Initialization
#################

umask 022

# echo before loading util_functions
ui_print() { echo "${'$'}1"; }

require_new_magisk() {
  ui_print "*******************************"
  ui_print " Please install Magisk v20.4+! "
  ui_print "*******************************"
  exit 1
}

#########################
# Load util_functions.sh
#########################

OUTFD=${'$'}2
ZIPFILE=${'$'}3

mount /data 2>/dev/null

if [ -f /data/adb/magisk/util_functions.sh ]; then
  . /data/adb/magisk/util_functions.sh
  [ ${'$'}MAGISK_VER_CODE -lt 20400 ] && require_new_magisk
  install_module
  exit 0
elif [ -x /data/adb/ksu/bin/ksud ]; then
  /data/adb/ksu/bin/ksud module install "${'$'}ZIPFILE"
  exit 0
elif [ -x /data/adb/ap/bin/apd ]; then
  /data/adb/ap/bin/apd module install "${'$'}ZIPFILE"
  exit 0
else
  require_new_magisk
fi
"""

    suspend fun exportModule(
        context: Context,
        module: Module,
        targetUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val moduleId = module.id
        val timestamp = System.currentTimeMillis()
        val stagingDir = File(context.cacheDir, "export_${moduleId}_$timestamp")
        val scriptFile = File(context.cacheDir, "export_script_${moduleId}_$timestamp.sh")

        val escapedStaging = stagingDir.absolutePath.replace("'", "'\\''")
        val escapedScript = scriptFile.absolutePath.replace("'", "'\\''")
        val escapedModuleId = moduleId.replace("'", "'\\''")

        try {
            val appUid = context.applicationInfo.uid
            val scriptContent = buildString {
                appendLine("set -e")
                appendLine("if [ -x /data/adb/ksu/bin/busybox ]; then")
                appendLine("    BB=/data/adb/ksu/bin/busybox")
                appendLine("elif [ -x /data/adb/magisk/busybox ]; then")
                appendLine("    BB=/data/adb/magisk/busybox")
                appendLine("elif [ -x /data/adb/ap/bin/busybox ]; then")
                appendLine("    BB=/data/adb/ap/bin/busybox")
                appendLine("elif command -v busybox >/dev/null 2>&1; then")
                appendLine("    BB=\$(command -v busybox)")
                appendLine("else")
                appendLine("    BB=\"\"")
                appendLine("fi")
                appendLine()
                appendLine("if [ -n \"\$BB\" ]; then")
                appendLine("    CP=\"\$BB cp\"")
                appendLine("    CHMOD=\"\$BB chmod\"")
                appendLine("    CHOWN=\"\$BB chown\"")
                appendLine("    READLINK=\"\$BB readlink\"")
                appendLine("else")
                appendLine("    CP=\"cp\"")
                appendLine("    CHMOD=\"chmod\"")
                appendLine("    CHOWN=\"chown\"")
                appendLine("    READLINK=\"readlink\"")
                appendLine("fi")
                appendLine()
                appendLine("MODULE_DIR=\"/data/adb/modules/$escapedModuleId\"")
                appendLine("STAGING=\"$escapedStaging\"")
                appendLine()
                appendLine("if [ ! -d \"\$MODULE_DIR\" ]; then")
                appendLine("    echo \"MODULE_NOT_FOUND\" >&2")
                appendLine("    exit 2")
                appendLine("fi")
                appendLine()
                appendLine("rm -rf \"\$STAGING\"")
                appendLine("mkdir -p \"\$STAGING\"")
                appendLine()
                appendLine("\$CP -a \"\$MODULE_DIR/.\" \"\$STAGING/\"")
                appendLine()
                appendLine("rm -f \"\$STAGING/disable\" \"\$STAGING/update\" \"\$STAGING/remove\"")
                appendLine("rm -rf \"\$STAGING/lost+found\"")
                appendLine()
                appendLine("if [ -L \"\$STAGING/webroot\" ]; then")
                appendLine("    WEBROOT_TARGET=\"\$(\$READLINK -f \"\$STAGING/webroot\" 2>/dev/null || true)\"")
                appendLine("    if [ -n \"\$WEBROOT_TARGET\" ] && [ -d \"\$WEBROOT_TARGET\" ]; then")
                appendLine("        rm -f \"\$STAGING/webroot\"")
                appendLine("        \$CP -r \"\$WEBROOT_TARGET\" \"\$STAGING/webroot\"")
                appendLine("    fi")
                appendLine("fi")
                appendLine()
                appendLine("mkdir -p \"\$STAGING/META-INF/com/google/android\"")
                appendLine("if [ ! -f \"\$STAGING/META-INF/com/google/android/updater-script\" ] || ! grep -q '#MAGISK' \"\$STAGING/META-INF/com/google/android/updater-script\" 2>/dev/null; then")
                appendLine("    printf '#MAGISK\\n' > \"\$STAGING/META-INF/com/google/android/updater-script\"")
                appendLine("fi")
                appendLine("if [ ! -f \"\$STAGING/META-INF/com/google/android/update-binary\" ]; then")
                appendLine("    cat << 'EOF_UB' > \"\$STAGING/META-INF/com/google/android/update-binary\"")
                append(MAGISK_UPDATE_BINARY)
                appendLine("EOF_UB")
                appendLine("    \$CHMOD 755 \"\$STAGING/META-INF/com/google/android/update-binary\"")
                appendLine("fi")
                appendLine()
                appendLine("\$CHOWN -R $appUid:$appUid \"\$STAGING\"")
                appendLine("\$CHMOD -R u+rX \"\$STAGING\"")
            }

            scriptFile.writeText(scriptContent)

            val shell = getRootShell()
            val result = shell.newJob().add("sh '$escapedScript'").exec()
            if (!result.isSuccess) {
                val err = result.err.joinToString("\n").ifBlank { "Exit code ${result.code}" }
                if (err.contains("MODULE_NOT_FOUND")) {
                    return@withContext Result.failure(FileNotFoundException("Module directory not found: /data/adb/modules/$moduleId"))
                }
                return@withContext Result.failure(IOException("Failed to prepare module files: $err"))
            }

            context.contentResolver.openOutputStream(targetUri)?.use { rawOut ->
                BufferedOutputStream(rawOut).use { buffOut ->
                    ZipArchiveOutputStream(buffOut).use { zipOut ->
                        zipOut.setEncoding("UTF-8")
                        zipOut.setLevel(Deflater.DEFAULT_COMPRESSION)
                        zipDirectory(stagingDir, zipOut)
                        zipOut.finish()
                        zipOut.flush()
                    }
                }
            } ?: return@withContext Result.failure(IOException("Failed to open output stream for destination: $targetUri"))

            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "exportModule failed", e)
            Result.failure(e)
        } finally {
            scriptFile.delete()
            stagingDir.deleteRecursively()
            if (stagingDir.exists() || scriptFile.exists()) {
                runCatching {
                    getRootShell().newJob().add("rm -rf '$escapedStaging' '$escapedScript'").exec()
                }
            }
        }
    }

    private fun zipDirectory(rootDir: File, zipOut: ZipArchiveOutputStream) {
        val rootPath = rootDir.toPath()
        Files.walk(rootPath).use { stream ->
            stream.forEach { currentPath ->
                if (currentPath == rootPath) return@forEach

                val relativePath = rootPath.relativize(currentPath).toString().replace('\\', '/')
                val file = currentPath.toFile()

                val isSymlink = Files.isSymbolicLink(currentPath)
                if (isSymlink) {
                    val linkTarget = runCatching { Files.readSymbolicLink(currentPath).toString() }.getOrNull()
                    if (linkTarget != null) {
                        val entry = ZipArchiveEntry(relativePath)
                        entry.unixMode = UnixStat.LINK_FLAG or UnixStat.DEFAULT_LINK_PERM
                        val time = runCatching { file.lastModified() }.getOrDefault(0L)
                        entry.time = if (time > 0) time else System.currentTimeMillis()
                        zipOut.putArchiveEntry(entry)
                        zipOut.write(linkTarget.toByteArray(Charsets.UTF_8))
                        zipOut.closeArchiveEntry()
                    }
                    return@forEach
                }

                if (file.isDirectory) {
                    val entryName = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
                    val entry = ZipArchiveEntry(entryName)
                    entry.unixMode = UnixStat.DIR_FLAG or UnixStat.DEFAULT_DIR_PERM
                    val time = runCatching { file.lastModified() }.getOrDefault(0L)
                    entry.time = if (time > 0) time else System.currentTimeMillis()
                    zipOut.putArchiveEntry(entry)
                    zipOut.closeArchiveEntry()
                } else if (file.isFile) {
                    val entry = ZipArchiveEntry(relativePath)
                    val perm = if (file.canExecute() || relativePath.endsWith(".sh") || relativePath.contains("/bin/") || relativePath.contains("/xbin/")) {
                        UnixStat.DEFAULT_DIR_PERM
                    } else {
                        UnixStat.DEFAULT_FILE_PERM
                    }
                    entry.unixMode = UnixStat.FILE_FLAG or perm
                    val time = runCatching { file.lastModified() }.getOrDefault(0L)
                    entry.time = if (time > 0) time else System.currentTimeMillis()
                    zipOut.putArchiveEntry(entry)
                    try {
                        file.inputStream().use { input ->
                            input.copyTo(zipOut, bufferSize = 8192)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read file $relativePath: ${e.message}")
                    } finally {
                        zipOut.closeArchiveEntry()
                    }
                }
            }
        }
    }
}
