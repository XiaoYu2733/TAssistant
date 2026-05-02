package re.limus.timas.hook.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import re.limus.timas.hook.base.XBridge
import top.sacz.xphelper.dexkit.DexFinder
import top.sacz.xphelper.ext.getFieldValue
import top.sacz.xphelper.ext.toClass
import top.sacz.xphelper.reflect.MethodUtils
import java.util.WeakHashMap
import kotlin.math.min

class AvatarFinder : XBridge() {

    companion object {
        private const val TROOP_NOTIFICATION_HOLDER = "com.tencent.mobileqq.troop.troopnotification.a.b\$g"
        private const val TROOP_NOTIFICATION_AVATAR_FIELD = "m"
        private const val TROOP_NOTIFICATION_APPLICANT_AVATAR_DESC = "申请者头像"
        private const val TROOP_ROBOT_AVATAR_TAG = "com.tencent.mobileqq.troop.robot.d"
        private const val NEW_FRIEND_AVATAR_TAG = "com.tencent.mobileqq.newfriend.ui.builder.n\$b"
        private const val SEARCH_DETAIL_AVATAR_DRAWABLE = "com.tencent.mobileqq.search.searchdetail.util.a"
    }

    private val commonImageView = "com.tencent.mobileqq.aio.widget.CommonImageView".toClass()
    private val fixSizeImageView = "com.tencent.widget.FixSizeImageView".toClass()
    private val roundRectImageView = "com.tencent.qqnt.base.widget.RoundRectImageView".toClass()
    private val qqProAvatarLayerImageView = "com.tencent.mobileqq.proavatar.QQProAvatarLayerImageView".toClass()
    private val recentAvatarWrapper = "com.tencent.qqnt.chats.view.widget.RecentAvatarViewWrapper".toClass()
    private val recentAvatarParam = "com.tencent.qqnt.chats.core.ui.a.a".toClass()
    private val forwardHeadView = "com.tencent.mobileqq.widget.ForwardHeadView".toClass()
    private val qIphoneTitleBarFragment = "com.tencent.mobileqq.fragment.QIphoneTitleBarFragment".toClass()

    private val troopNotificationAdapter = "com.tencent.mobileqq.troop.troopnotification.a.b".toClass()

    private val systemMsgListAdapter = "com.tencent.mobileqq.newfriend.ui.adapter.SystemMsgListAdapter".toClass()
    private val suspiciousMsgAdapter =
        "com.tencent.mobileqq.activity.contact.newfriend.NewFriendMoreSysMsgSuspiciousFragment\$SysMsgSuspiciousAdapter".toClass()

    private val qZoneUserAvatarView = "com.qzone.reborn.feedx.widget.QZoneUserAvatarView".toClass()
    private val qZoneAvatarDecorator = "com.qzone.cover.ui.QzoneAvatarDecorator".toClass()
    private val qZoneFacadeDecorator = "com.qzone.cover.ui.QzoneFacadeDecorator".toClass()

    private val troopRobotAvatarViews = WeakHashMap<View, Unit>()

    fun hookAvatar() {
        hookChatUI()
        hookGroupManagerAvatar()
        hookRecentListAvatar()
        hookTroopNotificationAvatar()
        hookTroopNotificationApplicantAvatar()
        hookForwardRecentAvatar()
        hookNewFriendAvatar()
        hookTitleBarAvatar()
        hookQzoneAvatar()
        hookSearchDetailAvatar()
        hookTroopRobotAvatar()
    }

    // ChatUI
    private fun hookChatUI() {
        DexFinder.findMethod {
            declaredClass = roundRectImageView
            methodName = "setCornerRadiusAndMode"
            parameters = arrayOf(Int::class.java, Int::class.java)
            paramCount = 2
        }.hookBefore {
            val view = thisObject.cast<View>()
            if (!commonImageView.isInstance(view)) return@hookBefore

            args[0] = view.circleRadius()
            args[1] = 1
        }
    }

    // GroupManager
    private fun hookGroupManagerAvatar() {
        DexFinder.findMethod {
            declaredClass = qqProAvatarLayerImageView
            parameters = arrayOf(Context::class.java, AttributeSet::class.java)
            paramCount = 2
        }.hookConstructorAfter {
            thisObject.cast<View>().clipToCircle()
        }
    }

    // ChatList
    private fun hookRecentListAvatar() {
        DexFinder.findMethod {
            declaredClass = recentAvatarWrapper
            parameters = arrayOf(recentAvatarParam)
            paramCount = 1
        }.hookAfter {
            args[0].cast<View>().clipToCircle()
        }
    }

    // ApplyList
    private fun hookTroopNotificationAvatar() {
        DexFinder.findMethod {
            declaredClass = troopNotificationAdapter
            methodName = "getView"
            parameters = arrayOf(Int::class.java, View::class.java, ViewGroup::class.java)
            paramCount = 3
        }.hookAfter {
            val holder = result.cast<View?>()?.tag ?: return@hookAfter
            if (holder.javaClass.name != TROOP_NOTIFICATION_HOLDER) return@hookAfter

            holder.getFieldValue<View>(TROOP_NOTIFICATION_AVATAR_FIELD).clipToCircle()
        }
    }

    // TroopRequestActivity
    private fun hookTroopNotificationApplicantAvatar() {
        MethodUtils.create(View::class.java)
            .methodName("setContentDescription")
            .params(CharSequence::class.java)
            .paramCount(1)
            .first()
            .hookAfter {
                val view = thisObject.cast<View>()
                if (view !is ImageView) return@hookAfter
                if (!args[0].isApplicantAvatarDescription()) return@hookAfter

                view.clipToCircle()
            }
    }

    // ForwardRecent
    private fun hookForwardRecentAvatar() {
        DexFinder.findMethod {
            declaredClass = forwardHeadView
            methodName = "a"
            returnType = ImageView::class.java
            paramCount = 0
        }.hookAfter {
            result.cast<View>().clipToCircle()
        }
    }

    // NewFriend
    private fun hookNewFriendAvatar() {
        DexFinder.findMethod {
            declaredClass = systemMsgListAdapter
            methodName = "getView"
            parameters = arrayOf(Int::class.java, View::class.java, ViewGroup::class.java)
            paramCount = 3
        }.hookAfter {
            result.cast<View?>()
                ?.findViewByTagClass(NEW_FRIEND_AVATAR_TAG, fixSizeImageView)
                ?.clipToCircle()
        }

        DexFinder.findMethod {
            declaredClass = suspiciousMsgAdapter
            methodName = "getView"
            parameters = arrayOf(Int::class.java, View::class.java, ViewGroup::class.java)
            paramCount = 3
        }.hookAfter {
            result.cast<View?>()
                ?.findViewByClass(fixSizeImageView)
                ?.clipToCircle()
        }
    }

    // TitleBar
    private fun hookTitleBarAvatar() {
        DexFinder.findMethod {
            declaredClass = qIphoneTitleBarFragment
            methodName = "onCreateView"
            parameters = arrayOf(LayoutInflater::class.java, ViewGroup::class.java, Bundle::class.java)
            paramCount = 3
        }.hookAfter {
            result.cast<View?>()
                ?.clipImageViewsByProAvatarBackground()
        }
    }

    // QZoneFeed
    private fun hookQzoneAvatar() {
        DexFinder.findMethod {
            declaredClass = qZoneUserAvatarView
            parameters = arrayOf(Context::class.java, AttributeSet::class.java, Int::class.java)
            paramCount = 3
        }.hookConstructorAfter {
            thisObject.cast<View>().clipToCircle()
        }

        DexFinder.findMethod {
            declaredClass = qZoneAvatarDecorator
            methodName = "i"
            parameters = arrayOf(String::class.java)
            returnType = Drawable::class.java
            paramCount = 1
        }.hookBefore {
            result = null
        }

        DexFinder.findMethod {
            declaredClass = qZoneAvatarDecorator
            methodName = "k"
            parameters = arrayOf(String::class.java)
            returnType = Boolean::class.javaPrimitiveType!!
            paramCount = 1
        }.hookBefore {
            result = false
        }

        DexFinder.findMethod {
            declaredClass = qZoneFacadeDecorator
            methodName = "onDraw"
            parameters = arrayOf(Canvas::class.java)
            returnType = Void.TYPE
            paramCount = 1
        }.hookBefore {
            result = null
        }
    }

    // SearchDetail
    private fun hookSearchDetailAvatar() {
        MethodUtils.create(ImageView::class.java)
            .methodName("setImageDrawable")
            .params(Drawable::class.java)
            .paramCount(1)
            .first()
            .hookAfter {
                val drawable = args[0].cast<Drawable?>() ?: return@hookAfter
                if (!drawable.isSearchDetailAvatarDrawable()) return@hookAfter

                thisObject.cast<View>().clipToCircle()
            }
    }

    // TroopRobot
    private fun hookTroopRobotAvatar() {
        MethodUtils.create(View::class.java)
            .methodName("setTag")
            .paramCount(1)
            .first()
            .hookAfter {
                val view = thisObject.cast<View>()
                if (args[0]?.javaClass?.name == TROOP_ROBOT_AVATAR_TAG) {
                    troopRobotAvatarViews[view] = Unit
                    view.clipIfTroopRobotProAvatar(view.background)
                    (view.cast<ImageView?>())?.drawable?.let { view.clipIfTroopRobotProAvatar(it) }
                } else {
                    troopRobotAvatarViews.remove(view)
                }
            }

        MethodUtils.create(View::class.java)
            .methodName("setBackground")
            .params(Drawable::class.java)
            .paramCount(1)
            .first()
            .hookAfter {
                thisObject.cast<View>()
                    .clipIfTroopRobotProAvatar(args[0].cast<Drawable?>() ?: return@hookAfter)
            }
    }

    private fun View.circleRadius(): Int {
        return min(width, height).takeIf { it > 0 }?.div(2) ?: 100
    }

    private fun View.clipToCircle() {
        outlineProvider = circleOutlineProvider
        clipToOutline = true
        invalidateOutline()
    }

    private fun View.clipIfTroopRobotProAvatar(drawable: Drawable?) {
        if (this in troopRobotAvatarViews && drawable.isProAvatarDrawable()) {
            clipToCircle()
        }
    }

    private fun View.findViewByTagClass(className: String, viewClass: Class<*>): View? {
        if (viewClass.isInstance(this) && tag?.javaClass?.name == className) return this

        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val child = getChildAt(index).findViewByTagClass(className, viewClass)
                if (child != null) return child
            }
        }

        return null
    }

    private fun View.findViewByClass(viewClass: Class<*>): View? {
        if (viewClass.isInstance(this)) return this

        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val child = getChildAt(index).findViewByClass(viewClass)
                if (child != null) return child
            }
        }

        return null
    }

    private fun View.clipImageViewsByProAvatarBackground() {
        if (this is ImageView && background.isProAvatarDrawable()) {
            clipToCircle()
        }

        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).clipImageViewsByProAvatarBackground()
            }
        }
    }

    private fun Any?.isApplicantAvatarDescription(): Boolean {
        return toString().trim().endsWith(TROOP_NOTIFICATION_APPLICANT_AVATAR_DESC)
    }

    private fun Drawable?.isProAvatarDrawable(): Boolean {
        return this?.javaClass?.name?.startsWith("com.tencent.mobileqq.proavatar.QQProAvatar") == true
    }

    private fun Drawable.isSearchDetailAvatarDrawable(): Boolean {
        return javaClass.name == SEARCH_DETAIL_AVATAR_DRAWABLE
    }

    private val circleOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val size = min(view.width, view.height)
            if (size <= 0) {
                outline.setEmpty()
                return
            }

            val left = (view.width - size) / 2
            val top = (view.height - size) / 2
            outline.setRoundRect(left, top, left + size, top + size, size / 2f)
        }
    }
}
