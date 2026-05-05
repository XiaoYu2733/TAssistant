package re.limus.timas.hook

import android.content.Context
import android.content.ContextWrapper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import top.sacz.xphelper.XpHelper

class HookEntry : XposedModule() {

    companion object {
        var loadPackageParam: LoadPackageParam? = null
    }

    private val hookSteps: HookSteps = HookSteps()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        XposedBridge.init(this)
        XpHelper.initModulePath(getModuleApplicationInfo().sourceDir)
    }

    override fun onPackageReady(loadParam: PackageReadyParam) {
        if (!loadParam.isFirstPackage) return
        if (loadParam.packageName != "com.tencent.tim") return
        HookEnv.setHostAppPackageName(loadParam.packageName)
        val legacyLoadParam = LoadPackageParam(
            loadParam.packageName,
            loadParam.applicationInfo,
            loadParam.classLoader
        )
        loadPackageParam = legacyLoadParam
        val applicationCreateMethod = hookSteps.getApplicationCreateMethod(legacyLoadParam) ?: return

        XposedBridge.hookMethod(applicationCreateMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val context = param.thisObject as ContextWrapper
                entryHook(context.baseContext)
            }
        })
    }

    private fun entryHook(context: Context) {
        XpHelper.initContext(context)
        XpHelper.injectResourcesToContext(context)
        hookSteps.initHook()
    }
}
