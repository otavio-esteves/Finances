package br.com.otavioesteves.finances.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCapabilityPolicyTest {

    private val sufficientRam = DeviceCapabilityPolicy.MIN_TOTAL_RAM_BYTES
    private val sufficientStorage = DeviceCapabilityPolicy.MIN_FREE_STORAGE_BYTES
    private val supportedAbis = listOf(DeviceCapabilityPolicy.REQUIRED_ABI)

    @Test
    fun evaluate_returnsNullWhenEverythingIsSufficient() {
        val result = DeviceCapabilityPolicy.evaluate(
            totalRamBytes = sufficientRam,
            isLowRamDevice = false,
            supportedAbis = supportedAbis,
            freeStorageBytes = sufficientStorage
        )

        assertNull(result)
    }

    @Test
    fun evaluate_flagsInsufficientRamWhenBelowThreshold() {
        val result = DeviceCapabilityPolicy.evaluate(
            totalRamBytes = sufficientRam - 1,
            isLowRamDevice = false,
            supportedAbis = supportedAbis,
            freeStorageBytes = sufficientStorage
        )

        assertEquals(UnsupportedReason.INSUFFICIENT_RAM, result)
    }

    @Test
    fun evaluate_flagsInsufficientRamWhenSystemMarksDeviceAsLowRam() {
        val result = DeviceCapabilityPolicy.evaluate(
            totalRamBytes = sufficientRam * 2,
            isLowRamDevice = true,
            supportedAbis = supportedAbis,
            freeStorageBytes = sufficientStorage
        )

        assertEquals(UnsupportedReason.INSUFFICIENT_RAM, result)
    }

    @Test
    fun evaluate_flagsUnsupportedAbiWhenRequiredAbiIsMissing() {
        val result = DeviceCapabilityPolicy.evaluate(
            totalRamBytes = sufficientRam,
            isLowRamDevice = false,
            supportedAbis = listOf("armeabi-v7a"),
            freeStorageBytes = sufficientStorage
        )

        assertEquals(UnsupportedReason.UNSUPPORTED_ABI, result)
    }

    @Test
    fun evaluate_flagsNoStorageWhenBelowThreshold() {
        val result = DeviceCapabilityPolicy.evaluate(
            totalRamBytes = sufficientRam,
            isLowRamDevice = false,
            supportedAbis = supportedAbis,
            freeStorageBytes = sufficientStorage - 1
        )

        assertEquals(UnsupportedReason.NO_STORAGE, result)
    }
}
