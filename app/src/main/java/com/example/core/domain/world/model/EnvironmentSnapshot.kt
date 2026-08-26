package com.example.core.domain.world.model

data class BatteryState(
    val levelPercentage: Int, // 0 to 100
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean
)

data class NetworkState(
    val isConnected: Boolean,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val isMetered: Boolean
)

/**
 * Point-in-time snapshot of the host device and OS environment context.
 */
data class EnvironmentSnapshot(
    val battery: BatteryState,
    val network: NetworkState,
    val availableCapabilities: Set<String>,
    val activeLocale: String,
    val capturedAt: Long
) {
    companion object {
        fun defaultSnapshot(capturedAt: Long): EnvironmentSnapshot = EnvironmentSnapshot(
            battery = BatteryState(levelPercentage = 100, isCharging = true, isPowerSaveMode = false),
            network = NetworkState(isConnected = true, isWifi = true, isCellular = false, isMetered = false),
            availableCapabilities = emptySet(),
            activeLocale = "en-US",
            capturedAt = capturedAt
        )
    }
}
