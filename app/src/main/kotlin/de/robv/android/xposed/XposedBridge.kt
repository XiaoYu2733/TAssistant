package de.robv.android.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Member

object XposedBridge {
    private const val TAG = "TAssistant"

    @Volatile
    private var xposed: XposedInterface? = null

    @JvmStatic
    fun init(xposed: XposedInterface) {
        this.xposed = xposed
    }

    @JvmStatic
    fun hookMethod(hookMethod: Member, callback: XC_MethodHook): XC_MethodHook.Unhook {
        val executable = hookMethod as? Executable
            ?: throw IllegalArgumentException("Only methods and constructors can be hooked: $hookMethod")
        executable.isAccessible = true

        val handle = requireXposed()
            .hook(executable)
            .setPriority(callback.priority)
            .intercept { chain ->
                val param = XC_MethodHook.MethodHookParam().apply {
                    method = hookMethod
                    thisObject = chain.thisObject
                    args = chain.args.toTypedArray()
                }

                callback.callBeforeHookedMethod(param)
                if (!param.isReturnEarly) {
                    try {
                        param.setResultFromOriginal(proceed(chain, param))
                    } catch (throwable: Throwable) {
                        param.setThrowableFromOriginal(throwable)
                    }
                }
                callback.callAfterHookedMethod(param)

                param.throwable?.let { throw it }
                param.result
            }
        return XC_MethodHook.Unhook(hookMethod, handle)
    }

    @JvmStatic
    fun hookAllMethods(
        hookClass: Class<*>,
        methodName: String,
        callback: XC_MethodHook
    ): Set<XC_MethodHook.Unhook> {
        return hookClass.declaredMethods
            .filter { it.name == methodName }
            .mapTo(LinkedHashSet()) { hookMethod(it, callback) }
    }

    @JvmStatic
    fun log(text: String) {
        val current = xposed
        if (current != null) {
            current.log(Log.INFO, TAG, text)
        } else {
            Log.i(TAG, text)
        }
    }

    @JvmStatic
    fun log(throwable: Throwable) {
        val current = xposed
        if (current != null) {
            current.log(Log.ERROR, TAG, throwable.message ?: throwable.javaClass.name, throwable)
        } else {
            Log.e(TAG, throwable.message ?: throwable.javaClass.name, throwable)
        }
    }

    private fun requireXposed(): XposedInterface {
        return xposed ?: error("XposedBridge has not been initialized")
    }

    private fun proceed(
        chain: XposedInterface.Chain,
        param: XC_MethodHook.MethodHookParam
    ): Any? {
        val args = param.args.toNonNullArray()
        val thisObject = param.thisObject
        return if (thisObject != null && thisObject !== chain.thisObject) {
            chain.proceedWith(thisObject, args)
        } else {
            chain.proceed(args)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Array<Any?>.toNonNullArray(): Array<Any> {
        return this as Array<Any>
    }
}
