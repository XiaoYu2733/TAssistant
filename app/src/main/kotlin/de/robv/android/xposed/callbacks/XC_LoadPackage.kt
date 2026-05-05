package de.robv.android.xposed.callbacks

import android.content.pm.ApplicationInfo

object XC_LoadPackage {
    class LoadPackageParam(
        @JvmField val packageName: String,
        @JvmField val appInfo: ApplicationInfo,
        @JvmField val classLoader: ClassLoader
    )
}
