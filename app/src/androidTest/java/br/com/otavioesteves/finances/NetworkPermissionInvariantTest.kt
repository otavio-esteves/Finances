package br.com.otavioesteves.finances

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the product's core privacy claim: the merged manifest — including
 * anything a dependency might add transitively — must never declare
 * android.permission.INTERNET. See docs/PLANO_MOTOR_IA_LOCAL.md, seção 2.4 e 6.
 */
@RunWith(AndroidJUnit4::class)
class NetworkPermissionInvariantTest {
    @Test
    fun manifestNeverDeclaresInternetPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val declaredPermissions = packageInfo.requestedPermissions?.toList().orEmpty()

        assertFalse(
            "App não deve declarar android.permission.INTERNET — ver docs/PLANO_MOTOR_IA_LOCAL.md seção 2.4",
            declaredPermissions.contains(Manifest.permission.INTERNET)
        )
    }
}
