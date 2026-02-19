package re.limus.timas.hook.items.function

import android.content.Context
import re.limus.timas.annotations.RegisterToUI
import re.limus.timas.annotations.UiCategory
import re.limus.timas.hook.base.SwitchHook
import top.sacz.xphelper.ext.getStaticFieldValue
import top.sacz.xphelper.ext.toClass

@RegisterToUI
object ForcePadMode : SwitchHook() {

    override val name = "强制平板模式"

    override val description = "重启后可能会退出登录状态, 重新登录即可 (开启后似乎较新的表情会变成文字显示)"

    override val category = UiCategory.FUNCTION

    override fun onHook(ctx: Context, loader: ClassLoader) {
        val appSettingClass = "com.tencent.common.config.AppSetting".toClass()
        val method = appSettingClass.getDeclaredMethod("f")
        method.hookBefore {
            val pad = appSettingClass.getStaticFieldValue<Int>("g")
            result = pad
        }
    }
}