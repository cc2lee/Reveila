package com.reveila.aegin.model

import java.util.concurrent.TimeUnit
import kotlin.math.round

data class DesktopGpuInfo(
    val gpuName: String = "",
    val vramGb: Double = 0.0,
    val hasDiscreteGpu: Boolean = false,
    val hasUnifiedMemoryGpu: Boolean = false
)

/**
 * Lightweight, cached desktop GPU & VRAM probe supporting:
 * - macOS Apple Silicon unified memory GPUs
 * - NVIDIA GPUs via `nvidia-smi` (Windows & Linux)
 * - Windows AMD / Intel / NVIDIA GPUs via 64-bit registry `HardwareInformation.qwMemorySize`
 * - Linux AMD ROCm GPUs via `rocm-smi`
 */
object DesktopHardwareProbe {
    @Volatile
    private var cachedGpu: DesktopGpuInfo? = null

    fun detectGpu(totalRamGb: Double, forceRefresh: Boolean = false): DesktopGpuInfo {
        if (!forceRefresh) {
            cachedGpu?.let { return it }
        }
        val detected = probeGpuUncached(totalRamGb)
        cachedGpu = detected
        return detected
    }

    private fun probeGpuUncached(totalRamGb: Double): DesktopGpuInfo {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()

        // 1. macOS Apple Silicon (unified memory Metal GPU)
        if (osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm64"))) {
            val unifiedVram = round(totalRamGb * 0.75 * 10.0) / 10.0
            return DesktopGpuInfo(
                gpuName = "Apple Silicon Unified GPU",
                vramGb = unifiedVram,
                hasDiscreteGpu = false,
                hasUnifiedMemoryGpu = true
            )
        }

        // 2. NVIDIA GPU via nvidia-smi (fast, exact VRAM in MiB on Windows & Linux)
        runCommandWithTimeout(
            listOf("nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader,nounits"),
            timeoutSeconds = 3
        )?.let { output ->
            parseNvidiaSmiCsv(output)?.let { return it }
        }

        // 3. Windows fallback (AMD Radeon / Intel Arc / NVIDIA without PATH nvidia-smi)
        // Reads 64-bit HardwareInformation.qwMemorySize to avoid the 4 GB 32-bit AdapterRAM cap.
        if (osName.contains("win")) {
            val psScript = """
                Get-ItemProperty -Path 'HKLM:\SYSTEM\ControlSet001\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}\0*' -ErrorAction SilentlyContinue |
                ForEach-Object {
                    ${'$'}n = ${'$'}_.DriverDesc
                    ${'$'}m = ${'$'}_.'HardwareInformation.qwMemorySize'
                    if (${'$'}n -and ${'$'}m) { "${'$'}n|${'$'}m" }
                }
            """.trimIndent()
            runCommandWithTimeout(
                listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psScript),
                timeoutSeconds = 4
            )?.let { output ->
                parseWindowsRegistryGpuLines(output)?.let { return it }
            }
        }

        // 4. Linux AMD ROCm fallback
        if (osName.contains("linux")) {
            runCommandWithTimeout(
                listOf("rocm-smi", "--showmeminfo", "vram", "--csv"),
                timeoutSeconds = 3
            )?.let { output ->
                parseRocmSmiCsv(output)?.let { return it }
            }
        }

        return DesktopGpuInfo()
    }

    internal fun parseNvidiaSmiCsv(output: String): DesktopGpuInfo? {
        var bestName = ""
        var bestVramGb = 0.0
        for (rawLine in output.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || !line.contains(',')) continue
            val idx = line.lastIndexOf(',')
            val name = line.substring(0, idx).trim()
            val mib = line.substring(idx + 1).trim().toDoubleOrNull() ?: continue
            val gb = mib / 1024.0
            if (gb > bestVramGb) {
                bestVramGb = gb
                bestName = name
            }
        }
        if (bestName.isBlank() || bestVramGb <= 0.0) return null
        val rounded = round(bestVramGb * 10.0) / 10.0
        return DesktopGpuInfo(
            gpuName = bestName,
            vramGb = rounded,
            hasDiscreteGpu = rounded >= 2.0,
            hasUnifiedMemoryGpu = false
        )
    }

    internal fun parseWindowsRegistryGpuLines(output: String): DesktopGpuInfo? {
        var bestName = ""
        var bestVramGb = 0.0
        for (rawLine in output.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || !line.contains('|')) continue
            val parts = line.split('|', limit = 2)
            val name = parts[0].trim()
            val bytes = parts[1].trim().toLongOrNull() ?: continue
            if (name.contains("Microsoft Basic", ignoreCase = true) || bytes <= 0L) continue
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            if (gb > bestVramGb) {
                bestVramGb = gb
                bestName = name
            }
        }
        if (bestName.isBlank() || bestVramGb <= 0.0) return null
        val rounded = round(bestVramGb * 10.0) / 10.0
        val lower = bestName.lowercase()
        val isIntegrated = lower.contains("intel(r) uhd") ||
            lower.contains("intel(r) hd graphics") ||
            lower.contains("intel(r) iris") ||
            lower.contains("radeon(tm) graphics")
        return DesktopGpuInfo(
            gpuName = bestName,
            vramGb = rounded,
            hasDiscreteGpu = !isIntegrated && rounded >= 2.0,
            hasUnifiedMemoryGpu = false
        )
    }

    internal fun parseRocmSmiCsv(output: String): DesktopGpuInfo? {
        var bestBytes = 0L
        for (rawLine in output.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("device", ignoreCase = true)) continue
            val cols = line.split(',')
            val totalBytes = cols.getOrNull(1)?.trim()?.toLongOrNull() ?: continue
            if (totalBytes > bestBytes) bestBytes = totalBytes
        }
        if (bestBytes <= 0L) return null
        val rounded = round((bestBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)) * 10.0) / 10.0
        return DesktopGpuInfo(
            gpuName = "AMD ROCm GPU",
            vramGb = rounded,
            hasDiscreteGpu = rounded >= 2.0,
            hasUnifiedMemoryGpu = false
        )
    }

    private fun runCommandWithTimeout(command: List<String>, timeoutSeconds: Long): String? {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }
}
