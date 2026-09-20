package br.com.otavioesteves.finances.domain.ai

/**
 * Pure capacity heuristics gating whether the local AI engine is offered at
 * all (docs/PLANO_MOTOR_IA_LOCAL.md, seção 4, "Detecção de capacidade").
 * Thresholds are first-pass placeholders — Fase 4 replaces them with numbers
 * measured on real target devices.
 */
object DeviceCapabilityPolicy {
    const val MIN_TOTAL_RAM_BYTES = 6L * 1024 * 1024 * 1024
    const val MIN_FREE_STORAGE_BYTES = 2L * 1024 * 1024 * 1024
    const val REQUIRED_ABI = "arm64-v8a"

    fun evaluate(
        totalRamBytes: Long,
        isLowRamDevice: Boolean,
        supportedAbis: List<String>,
        freeStorageBytes: Long
    ): UnsupportedReason? = when {
        isLowRamDevice || totalRamBytes < MIN_TOTAL_RAM_BYTES -> UnsupportedReason.INSUFFICIENT_RAM
        REQUIRED_ABI !in supportedAbis -> UnsupportedReason.UNSUPPORTED_ABI
        freeStorageBytes < MIN_FREE_STORAGE_BYTES -> UnsupportedReason.NO_STORAGE
        else -> null
    }
}
