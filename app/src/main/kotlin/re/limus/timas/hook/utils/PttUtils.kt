package re.limus.timas.hook.utils

import android.os.Build
import androidx.annotation.RequiresApi
import re.limus.timas.api.ContactUtils
import re.limus.timas.api.CreateElement
import re.limus.timas.api.TIMSendMsgTool
import java.lang.reflect.Field

object PttUtils {

    /**
     * 发送语音到指定联系人
     * @param rawUinType 转发界面/qqfav 返回的原始 uinType，内部 +1 映射到 Contact chatType
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun sendPtt(filePath: String, uin: String, rawUinType: Int) {
        val element = CreateElement.createPttElement(filePath)
        sendPtt(element, uin, rawUinType)
    }

    /**
     * 使用已创建的 PttElement 发送语音（适用于批量发送场景，避免重复创建 Element）
     */
    fun sendPtt(pttElement: Any, uin: String, rawUinType: Int) {
        val contact = ContactUtils.getContact(rawUinType + 1, uin)
        TIMSendMsgTool.sendMsg(contact, arrayListOf(pttElement))
    }

    /**
     * 在类层次中按类型查找字段
     */
    fun findFieldByType(clazz: Class<*>, type: Class<*>): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredFields.firstOrNull { it.type == type }?.let { return it }
            current = current.superclass
        }
        return null
    }
}
