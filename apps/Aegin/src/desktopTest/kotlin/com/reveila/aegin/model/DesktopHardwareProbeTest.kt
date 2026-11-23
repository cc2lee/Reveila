package com.reveila.aegin.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopHardwareProbeTest {

    @Test
    fun parseNvidiaSmiCsv_picksHighestVramDiscreteGpu() {
        val output = """
            NVIDIA GeForce RTX 4070 Laptop GPU, 8188
            NVIDIA RTX 4090, 24564
        """.trimIndent()
        val gpu = DesktopHardwareProbe.parseNvidiaSmiCsv(output)
        assertNotNull(gpu)
        assertEquals("NVIDIA RTX 4090", gpu.gpuName)
        assertEquals(24.0, gpu.vramGb)
        assertTrue(gpu.hasDiscreteGpu)
    }

    @Test
    fun parseWindowsRegistryGpuLines_handles64BitVramAndFiltersSoftwareAdapters() {
        val output = """
            Microsoft Basic Display Adapter|1073741824
            Intel(R) UHD Graphics 770|134217728
            AMD Radeon RX 7900 XTX|25769803776
        """.trimIndent()
        val gpu = DesktopHardwareProbe.parseWindowsRegistryGpuLines(output)
        assertNotNull(gpu)
        assertEquals("AMD Radeon RX 7900 XTX", gpu.gpuName)
        assertEquals(24.0, gpu.vramGb)
        assertTrue(gpu.hasDiscreteGpu)
    }

    @Test
    fun parseWindowsRegistryGpuLines_marksIntegratedGpuNonDiscrete() {
        val output = "Intel(R) Iris(R) Xe Graphics|2147483648"
        val gpu = DesktopHardwareProbe.parseWindowsRegistryGpuLines(output)
        assertNotNull(gpu)
        assertEquals(2.0, gpu.vramGb)
        assertFalse(gpu.hasDiscreteGpu)
    }
}
