package com.reveila.aegin.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelTierCatalogTest {

    @Test
    fun desktopWithAdequateDiscreteGpuAndDiskSelects14B() {
        val rec = ModelTierCatalog.recommendModel(
            EnvironmentProfile(
                target = "desktop",
                totalRamGb = 32.0,
                availableRamGb = 20.0,
                freeDiskGb = 50.0,
                gpuVramGb = 12.0,
                hasDiscreteGpu = true,
                cpuCores = 16
            )
        )
        assertEquals(ModelTierCatalog.MODEL_14B, rec.recommendedModel)
        assertTrue(rec.hasEnoughStorage)
        assertEquals("", rec.downgradedReason)
        assertTrue(rec.hardwareTier.contains("12.0 GB VRAM"))
    }

    @Test
    fun desktopCpuOnly32GbCapsBelow14B() {
        val rec = ModelTierCatalog.recommendModel(
            EnvironmentProfile(
                target = "desktop",
                totalRamGb = 32.0,
                availableRamGb = 20.0,
                freeDiskGb = 50.0,
                gpuVramGb = 0.0,
                hasDiscreteGpu = false,
                cpuCores = 12
            )
        )
        assertEquals(ModelTierCatalog.MODEL_7B, rec.recommendedModel)
        assertTrue(rec.hasEnoughStorage)
        assertTrue(rec.downgradedReason.contains("14B", ignoreCase = true))
        assertTrue(rec.hardwareTier.contains("CPU"))
    }

    @Test
    fun desktopAppleSiliconUnifiedMemory24GbSelects14B() {
        val rec = ModelTierCatalog.recommendModel(
            EnvironmentProfile(
                target = "desktop",
                totalRamGb = 24.0,
                availableRamGb = 16.0,
                freeDiskGb = 40.0,
                gpuVramGb = 18.0,
                hasUnifiedMemoryGpu = true,
                cpuCores = 10
            )
        )
        assertEquals(ModelTierCatalog.MODEL_14B, rec.recommendedModel)
        assertTrue(rec.hasEnoughStorage)
        assertEquals("", rec.downgradedReason)
        assertTrue(rec.hardwareTier.contains("Unified GPU"))
    }

    @Test
    fun desktopTightDiskSpaceStepsDownAutomatically() {
        val rec = ModelTierCatalog.recommendModel(
            EnvironmentProfile(
                target = "desktop",
                totalRamGb = 32.0,
                availableRamGb = 20.0,
                freeDiskGb = 4.5, // Too small for 14B (~10 GB) and 7B (~5.9 GB), fits 3B (~3.5 GB)
                gpuVramGb = 12.0,
                hasDiscreteGpu = true,
                cpuCores = 16
            )
        )
        assertEquals(ModelTierCatalog.MODEL_3B, rec.recommendedModel)
        assertTrue(rec.hasEnoughStorage)
        assertTrue(rec.downgradedReason.contains("disk", ignoreCase = true))
    }

    @Test
    fun desktopInsufficientDiskEvenForSmallestModelBlocksStorage() {
        val rec = ModelTierCatalog.recommendModel(
            EnvironmentProfile(
                target = "desktop",
                totalRamGb = 16.0,
                availableRamGb = 10.0,
                freeDiskGb = 1.0,
                gpuVramGb = 8.0,
                hasDiscreteGpu = true
            )
        )
        assertEquals(ModelTierCatalog.MODEL_0_5B, rec.recommendedModel)
        assertFalse(rec.hasEnoughStorage)
    }

    @Test
    fun androidFlagshipStepsDownWhenDiskOrAvailableRamIsTight() {
        val diskTight = ModelTierCatalog.recommendModel(
            EnvironmentProfile(
                target = "android",
                totalRamGb = 12.0,
                availableRamGb = 6.0,
                freeDiskGb = 2.8 // Fits 1.5B (~2.55 GB with 1.5 GB buffer), not 3B (~3.46 GB) or Gemma 2B (~3.09 GB)
            )
        )
        assertEquals(ModelTierCatalog.MODEL_1_5B, diskTight.recommendedModel)
        assertTrue(diskTight.hasEnoughStorage)
        assertTrue(diskTight.downgradedReason.contains("disk", ignoreCase = true))

        val ramTight = ModelTierCatalog.recommendModel(
            EnvironmentProfile(
                target = "android",
                totalRamGb = 12.0,
                availableRamGb = 1.8, // Too tight for 3B (3.0 GB) and 2B (2.2 GB), fits 1.5B (1.5 GB)
                freeDiskGb = 20.0
            )
        )
        assertEquals(ModelTierCatalog.MODEL_1_5B, ramTight.recommendedModel)
        assertTrue(ramTight.hasEnoughStorage)
        assertTrue(ramTight.downgradedReason.contains("RAM", ignoreCase = true))
    }
}
