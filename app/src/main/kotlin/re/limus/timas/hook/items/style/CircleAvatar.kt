package re.limus.timas.hook.items.style

import android.content.Context
import re.limus.timas.annotations.RegisterToUI
import re.limus.timas.annotations.UiCategory
import re.limus.timas.hook.base.SwitchHook
import re.limus.timas.hook.utils.AvatarFinder

@RegisterToUI
object CircleAvatar : SwitchHook() {

    override val name = "圆形头像"

    override val description = "令 TIM 的 不圆不方 的 头像 变为圆形 (需重启)"

    override val category = UiCategory.STYLE

    override val needRestart = true

    override fun onHook(ctx: Context, loader: ClassLoader) {
        AvatarFinder().hookAvatar()
    }
}
