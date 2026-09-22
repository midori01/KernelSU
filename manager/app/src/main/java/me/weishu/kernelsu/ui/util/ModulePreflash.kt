package me.weishu.kernelsu.ui.util

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize
import me.weishu.kernelsu.R
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@Parcelize
enum class ModuleRiskLevel : Parcelable {
    SAFE,
    INFO,
    WARNING,
    DANGER
}

@Parcelize
data class ModuleRiskItem(
    val level: ModuleRiskLevel,
    @StringRes val titleRes: Int,
    val detail: String,
) : Parcelable

@Parcelize
data class ModulePreflashResult(
    val uriString: String,
    val fileName: String,
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int,
    val author: String,
    val description: String,
    val isMetaModule: Boolean,
    val riskLevel: ModuleRiskLevel,
    val risks: List<ModuleRiskItem>,
    val systemFiles: List<String>,
    val systemFilesCount: Int = systemFiles.size,
    val scriptHooks: List<String>,
    val hasWebUi: Boolean,
) : Parcelable {
    val uri: Uri get() = Uri.parse(uriString)
}

object ModulePreflash {
    private val BLOCK_DEV_REGEX = Regex(
        """(?:\b(?:dd\s+[^;\n|]*of=|cp\s+[^;\n|]*\s+|tee\s+[^;\n|]*|shred\s+[^;\n|]*)|>{1,2}\s*)["']?/dev/block[^\s;|&]*|\b(?:flash_erase|blockdev|wipefs|mke2fs|make_ext4fs|mkfs\.[a-z0-9]+|sgdisk\s+[^;\n|]*(?:--zap|-z|-d|--delete))"""
    )
    private val RECURSIVE_RM_REGEX = Regex(
        """\brm\s+(?:-[a-zA-Z]+\s+)*["']?(?:/[*]?|/data(?:/[*]?)?|/system(?:/[*]?)?|/vendor(?:/[*]?)?|/product(?:/[*]?)?|/metadata(?:/[*]?)?|/data/adb(?:/\*?|/modules(?:/\*?)?)?)["']?(?=[\s;"'\)]|$)"""
    )
    private val SELINUX_DISABLE_REGEX = Regex(
        """\b(setenforce\s+(?:0|permissive)|echo\s+(?:-n\s+)?["'\s]*0["'\s]*>\s*/sys/fs/selinux/enforce)""",
        RegexOption.IGNORE_CASE
    )

    private val CRITICAL_SYSTEM_FILES = setOf(
        "libc.so", "libm.so", "libdl.so", "libart.so", "libart-compiler.so",
        "app_process", "app_process32", "app_process64",
        "surfaceflinger", "servicemanager", "keystore2",
        "linker", "linker64", "vold", "init"
    )

    fun inspectModuleZip(context: Context, uri: Uri): ModulePreflashResult {
        var fileName = uri.getFileName(context) ?: ""
        if (fileName.isEmpty()) {
            fileName = uri.lastPathSegment ?: "module.zip"
        }
        fileName = runCatching { Uri.decode(fileName) }.getOrDefault(fileName)

        val props = mutableMapOf<String, String>()
        val previewSystemFiles = mutableListOf<String>()
        var systemFilesCount = 0
        val scriptHooks = mutableListOf<String>()
        val risks = mutableListOf<ModuleRiskItem>()
        var hasWebUi = false
        var hasNativeBinary = false
        var firstNativeBinary: String? = null
        var hasKernelModule = false
        var firstKernelModule: String? = null
        var totalEntries = 0
        var hasReadError = false

        try {
            context.contentResolver.openInputStream(uri)?.use { rawIn ->
                ZipInputStream(rawIn.buffered()).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        totalEntries++
                        if (totalEntries > 50000) break

                        val rawName = entry.name
                        val name = rawName.replace('\\', '/')
                            .split('/')
                            .filter { it.isNotEmpty() && it != "." }
                            .joinToString("/")

                        if (name.isEmpty() || entry.isDirectory || rawName.endsWith("/")) {
                            zis.closeEntry()
                            continue
                        }

                        if (name == "module.prop") {
                            val propText = readZipEntryText(zis, 64 * 1024)
                            propText.lineSequence().forEach { line ->
                                val trimmed = line.trim().removePrefix("\uFEFF")
                                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                                    val parts = trimmed.split("=", limit = 2)
                                    props[parts[0].trim()] = parts[1].trim()
                                }
                            }
                        } else if (name.startsWith("webroot/")) {
                            hasWebUi = true
                        } else if (name.startsWith("system/") || name.startsWith("system_ext/") ||
                            name.startsWith("product/") || name.startsWith("vendor/")) {
                            systemFilesCount++
                            if (previewSystemFiles.size < 10) {
                                previewSystemFiles.add(name)
                            }
                            val baseName = name.substringAfterLast('/')
                            if (baseName in CRITICAL_SYSTEM_FILES) {
                                risks.add(
                                    ModuleRiskItem(
                                        level = ModuleRiskLevel.WARNING,
                                        titleRes = R.string.module_preflash_risk_critical_sys,
                                        detail = name
                                    )
                                )
                            }
                        } else {
                            when (name) {
                                "customize.sh", "post-fs-data.sh", "service.sh", "boot-completed.sh",
                                "action.sh", "uninstall.sh", "pre-mount.sh" -> {
                                    scriptHooks.add(name)
                                    val scriptContent = readZipEntryText(zis, 512 * 1024)
                                    scanScript(name, scriptContent, risks)
                                }
                                "sepolicy.rule" -> {
                                    scriptHooks.add(name)
                                    risks.add(
                                        ModuleRiskItem(
                                            level = ModuleRiskLevel.INFO,
                                            titleRes = R.string.module_preflash_risk_sepolicy,
                                            detail = name
                                        )
                                    )
                                }
                                "system.prop" -> {
                                    scriptHooks.add(name)
                                }
                            }
                        }

                        if (name.endsWith(".ko")) {
                            hasKernelModule = true
                            if (firstKernelModule == null) {
                                firstKernelModule = name.substringAfterLast('/')
                            }
                        }

                        if ((name.startsWith("bin/") || name.contains("/bin/") ||
                                name.startsWith("xbin/") || name.contains("/xbin/") ||
                                name.startsWith("arm64-v8a/") || name.startsWith("armeabi-v7a/") ||
                                name.startsWith("x86_64/") || name.startsWith("x86/")) && !name.endsWith(".sh")) {
                            val header = ByteArray(4)
                            val read = zis.read(header)
                            if (read == 4 && isElfHeader(header)) {
                                hasNativeBinary = true
                                if (firstNativeBinary == null) {
                                    firstNativeBinary = name
                                }
                            }
                        }

                        zis.closeEntry()
                    }
                }
            }
        } catch (e: Exception) {
            hasReadError = true
            Log.w("ModulePreflash", "Failed to inspect module zip: $uri", e)
        }

        if (hasReadError || totalEntries == 0) {
            risks.add(
                ModuleRiskItem(
                    level = ModuleRiskLevel.WARNING,
                    titleRes = R.string.module_preflash_risk_unreadable,
                    detail = fileName
                )
            )
        }

        if (hasKernelModule) {
            risks.add(
                ModuleRiskItem(
                    level = ModuleRiskLevel.INFO,
                    titleRes = R.string.module_preflash_risk_lkm,
                    detail = firstKernelModule ?: "kernel module (*.ko)"
                )
            )
        }

        if (hasNativeBinary) {
            risks.add(
                ModuleRiskItem(
                    level = ModuleRiskLevel.INFO,
                    titleRes = R.string.module_preflash_risk_binary,
                    detail = firstNativeBinary ?: "ELF binary"
                )
            )
        }

        val isMeta = props["metamodule"] == "1" || props["metamodule"]?.equals("true", ignoreCase = true) == true
        if (isMeta) {
            risks.add(
                ModuleRiskItem(
                    level = ModuleRiskLevel.WARNING,
                    titleRes = R.string.module_preflash_risk_metamodule,
                    detail = "metamodule=1"
                )
            )
        }

        if (hasWebUi) {
            risks.add(
                ModuleRiskItem(
                    level = ModuleRiskLevel.INFO,
                    titleRes = R.string.module_preflash_risk_webui,
                    detail = "webroot/"
                )
            )
        }

        val overallLevel = when {
            risks.any { it.level == ModuleRiskLevel.DANGER } -> ModuleRiskLevel.DANGER
            risks.any { it.level == ModuleRiskLevel.WARNING } -> ModuleRiskLevel.WARNING
            risks.any { it.level == ModuleRiskLevel.INFO } -> ModuleRiskLevel.INFO
            else -> ModuleRiskLevel.SAFE
        }

        return ModulePreflashResult(
            uriString = uri.toString(),
            fileName = fileName,
            id = props["id"] ?: fileName.removeSuffix(".zip"),
            name = props["name"] ?: (props["id"] ?: fileName),
            version = props["version"] ?: "1.0",
            versionCode = props["versionCode"]?.toIntOrNull() ?: 1,
            author = props["author"] ?: "Unknown",
            description = props["description"] ?: "",
            isMetaModule = isMeta,
            riskLevel = overallLevel,
            risks = risks.distinctBy { "${it.titleRes}_${it.detail}" }.take(30),
            systemFiles = previewSystemFiles,
            systemFilesCount = systemFilesCount,
            scriptHooks = scriptHooks,
            hasWebUi = hasWebUi
        )
    }

    private fun isElfHeader(header: ByteArray): Boolean {
        return header.size >= 4 &&
            header[0] == 0x7F.toByte() &&
            header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() &&
            header[3] == 'F'.code.toByte()
    }

    private fun readZipEntryText(zis: ZipInputStream, maxBytes: Int): String {
        val buffer = ByteArray(4096)
        val baos = ByteArrayOutputStream()
        var totalRead = 0
        while (totalRead < maxBytes) {
            val toRead = minOf(buffer.size, maxBytes - totalRead)
            val count = zis.read(buffer, 0, toRead)
            if (count == -1) break
            baos.write(buffer, 0, count)
            totalRead += count
        }
        return String(baos.toByteArray(), Charsets.UTF_8)
    }

    private fun scanScript(scriptName: String, content: String, risks: MutableList<ModuleRiskItem>) {
        val executableContent = content.lineSequence()
            .filter { line -> !line.trimStart().startsWith("#") }
            .joinToString("\n")

        BLOCK_DEV_REGEX.findAll(executableContent).take(3).forEach { match ->
            risks.add(
                ModuleRiskItem(
                    level = ModuleRiskLevel.DANGER,
                    titleRes = R.string.module_preflash_risk_block_dev,
                    detail = "$scriptName: ${match.value.trim().take(80)}"
                )
            )
        }
        RECURSIVE_RM_REGEX.findAll(executableContent).take(3).forEach { match ->
            risks.add(
                ModuleRiskItem(
                    level = ModuleRiskLevel.DANGER,
                    titleRes = R.string.module_preflash_risk_recursive_rm,
                    detail = "$scriptName: ${match.value.trim().take(80)}"
                )
            )
        }
        SELINUX_DISABLE_REGEX.findAll(executableContent).take(3).forEach { match ->
            risks.add(
                ModuleRiskItem(
                    level = ModuleRiskLevel.WARNING,
                    titleRes = R.string.module_preflash_risk_selinux,
                    detail = "$scriptName: ${match.value.trim().take(80)}"
                )
            )
        }
    }
}
