package re.limus.timas.hook.items.message

import android.content.Context
import android.view.View
import android.view.ViewGroup
import re.limus.timas.annotations.RegisterToUI
import re.limus.timas.annotations.UiCategory
import re.limus.timas.api.TIMMessageViewListener
import re.limus.timas.api.TIMMsgViewAdapter
import re.limus.timas.hook.base.SwitchHook
import re.limus.timas.hook.utils.cast
import top.artmoe.inao.item.FriendChatMessageRecall
import top.artmoe.inao.item.GroupChatMessageRecall
import top.artmoe.inao.item.NewPreventRetractingMessageCore
import top.artmoe.inao.item.RetractingCallback
import top.sacz.xphelper.reflect.ClassUtils
import top.sacz.xphelper.reflect.FieldUtils
import top.sacz.xphelper.reflect.MethodUtils
import top.sacz.xphelper.util.ConfigUtils

@RegisterToUI
object PreventRevokeMsg : SwitchHook() {

    override val name = "防撤回"

    override val description = "防止他人消息被撤回, 并带有灰字提示"

    override val category = UiCategory.MESSAGE

    private const val viewId = 0x298382
    private val config = ConfigUtils("RevokeMsgDataBase")

    //使用map来拼接key查，性能通常比list更快
    private val friendChatMap = mutableMapOf<String, FriendChatMessageRecall>()
    private val groupChatMap = mutableMapOf<String, GroupChatMessageRecall>()

    private val friendChatList = mutableListOf<FriendChatMessageRecall>()
    private val groupChatList = mutableListOf<GroupChatMessageRecall>()

    /**
     * 缓存到本地
     */
    private fun readLocalCache() {
        val friendCache = config.getList("friendCache", FriendChatMessageRecall::class.java)
        friendChatList.addAll(friendCache)
        val groupCache = config.getList("groupCache", GroupChatMessageRecall::class.java)
        groupChatList.addAll(groupCache)
        //然后生成缓存到map
        for (friend in friendChatList) {
            friendChatMap[friend.peerUid + friend.msgSeq.toString()] = friend
        }
        for (group in groupChatList) {
            groupChatMap[group.groupUin + group.msgSeq.toString()] = group
        }
    }

    private fun addToFriendChatList(data: FriendChatMessageRecall) {
        friendChatList.add(data)
        friendChatMap[data.peerUid + data.msgSeq.toString()] = data
        config.put("friendCache", friendChatList)
    }

    private fun addToGroupChatList(data: GroupChatMessageRecall) {
        groupChatList.add(data)
        groupChatMap[data.groupUin + data.msgSeq.toString()] = data
        config.put("groupCache", groupChatList)
    }

    override fun onHook(ctx: Context, loader: ClassLoader) {
        readLocalCache()

        val onMSFPushMethod = MethodUtils.create("com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession\$CppProxy")
            .params(
                String::class.java,
                ByteArray::class.java,
                ClassUtils.findClass("com.tencent.qqnt.kernel.nativeinterface.PushExtraInfo")
            )
            .methodName("onMsfPush")
            .first()
        NewPreventRetractingMessageCore.setOnRecallMessageDetected(object : RetractingCallback {
            override fun onFriendChatMessageRecall(data: FriendChatMessageRecall) {
                addToFriendChatList(data)
            }

            override fun onGroupChatMessageRecall(data: GroupChatMessageRecall) {
                addToGroupChatList(data)
            }
        })
        onMSFPushMethod.hookBefore {
            val cmd = args[0].cast<String>()
            val protoBuf = args[1].cast<ByteArray>()
            if (cmd == "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush") {
                NewPreventRetractingMessageCore.handleInfoSyncPush(protoBuf, this)
            } else if (cmd == "trpc.msg.olpush.OlPushService.MsgPush") {
                NewPreventRetractingMessageCore.handleMsgPush(protoBuf, this)
            }
        }
        hookAIOMsgUpdate()
    }


    private fun hookAIOMsgUpdate() {
        TIMMessageViewListener.addMessageViewUpdateListener(
            this,
            object : TIMMessageViewListener.OnChatViewUpdateListener {
                override fun onViewUpdateAfter(msgItemView: View, msgRecord: Any) {
                    //约束布局
                    val rootView = msgItemView.cast<ViewGroup>()

                    //防止有撤回 进群等消息类型
                    if (!TIMMsgViewAdapter.hasContentMessage(rootView)) return

                    val peerUid: String = FieldUtils.create(msgRecord)
                        .fieldName("peerUid")
                        .fieldType(String::class.java)
                        .firstValue(msgRecord)

                    val msgSeq: Long = FieldUtils.create(msgRecord)
                        .fieldName("msgSeq")
                        .fieldType(Long::class.javaPrimitiveType).firstValue(msgRecord)

                    //防止错误添加提示没有删除
                    val recallPromptTextView = rootView.findViewById<View>(viewId)
                    if (recallPromptTextView != null) rootView.removeView(recallPromptTextView)
                    //这个msg是秒级的 不是毫秒
                    var msgTime: Long = FieldUtils.create(msgRecord).fieldName("msgTime")
                        .fieldType(Long::class.javaPrimitiveType).firstValue(msgRecord)
                    //变成毫秒级
                    msgTime *= 1000
                    //计算时间差 发送时间低于1秒不判断
                    if ((System.currentTimeMillis() - msgTime) < 1000) {
                        return
                    }
                    //如果有那就是已经撤回的消息
                    if (isRetractMessage(peerUid, msgSeq.toInt())) {
                    }
                }

            })
    }

    /**
     * 是否撤回的消息
     */
    private fun isRetractMessage(peerUid: String, msgSeq: Int): Boolean {
        val key = peerUid + msgSeq.toString()
        return friendChatMap.containsKey(key) || groupChatMap.containsKey(key)
    }

}