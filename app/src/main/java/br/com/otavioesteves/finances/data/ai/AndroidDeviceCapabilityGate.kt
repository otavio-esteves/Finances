package br.com.otavioesteves.finances.data.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import br.com.otavioesteves.finances.domain.ai.DeviceCapabilityPolicy
import br.com.otavioesteves.finances.domain.ai.UnsupportedReason

/** Gathers device signals and delegates the pass/fail call to [DeviceCapabilityPolicy]. */
class AndroidDeviceCapabilityGate(private val context: Context) {
    fun check(): UnsupportedReason? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val freeStorageBytes = StatFs(context.filesDir.path).availableBytes

        return DeviceCapabilityPolicy.evaluate(
            totalRamBytes = memoryInfo.totalMem,
            isLowRamDevice = activityManager.isLowRamDevice,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            freeStorageBytes = freeStorageBytes
        )
    }
}
