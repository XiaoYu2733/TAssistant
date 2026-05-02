## QQ Proto Buf 防撤回

所有人：言子飘花(https://github.com/Suzhelan),QStory,Tim小助手    
数据提供与初开发者: 叶叶(https://github.com/leafmoes)  
这是我很重要的人开发的 任何地方使用请保留原本注释和包名(top/artmoe/inao)  
如果你有更好的写法请告诉我

## 📖 用途说明

本文档是一套用于QQ消息防撤回功能的Protocol Buffer协议定义文件集合，主要用于：

- 拦截和处理QQ的消息撤回操作
- 解析QQ网络协议中的消息推送数据
- 实现消息防撤回的核心逻辑支撑

## 🔧 工作原理

### 核心机制

通过Hook QQ客户端的消息处理流程，拦截以下关键网络包：

1. **MsgPush** - 单条消息推送包
2. **InfoSyncPush** - 消息同步推送包

### 防撤回实现逻辑

1. 监听网络层消息接收
2. 识别撤回操作消息（msgType=528/subType=138 或 msgType=732/subType=17）
3. 修改撤回消息的关键字段（将msgSeq修改为无效值）
4. 缓存被撤回的消息信息用于UI展示
5. 让撤回操作在客户端层面失效

### 实现教程

原本的google protobuf已经弃用，现在使用kotlinx-serialization-protobuf来实现

1. 添加依赖

```groovy
//plugins添加
alias(libs.plugins.kotlinx.serialization)
//具体依赖你想找到应该不难
implementation(libs.kotlinx.serialization.protobuf)
implementation("com.github.suzhelan:XpHelper:3.0")
```

2. 复制此项目的 [app/src/main/javatop/artmoe/inao/entries](app/src/main/javatop/artmoe/inao/entries)
   目录中的所有文件到你的项目 保留包名
3. 编写hook代码，拦截protobuf包

```kotlin
//监听拦截回调，用于在ui中显示
NewPreventRetractingMessageCore.setOnRecallMessageDetected(object : RetractingCallback {
    override fun onFriendChatMessageRecall(data: FriendChatMessageRecall) {
        val peerUid = data.peerUid
        val msgSeq = data.msgSeq
        writeAndRefresh(peerUid, msgSeq)
    }

    override fun onGroupChatMessageRecall(data: GroupChatMessageRecall) {
        val peerUid = data.groupUin
        val msgSeq = data.msgSeq
        writeAndRefresh(peerUid, msgSeq)
    }
})
//hook MSF PUSH消息 可以拦截到撤回消息
val onMSFPushMethod =
    MethodUtils.create($$"com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession$CppProxy")
        .params(
            String::class.java,
            ByteArray::class.java,
            ClassUtils.getClass("com.tencent.qqnt.kernel.nativeinterface.PushExtraInfo")
        )
        .methodName("onMsfPush")
        .first()
hookBefore(onMSFPushMethod) { param: MethodHookParam ->
    val cmd = param.args[0] as String
    val protoBuf = param.args[1] as ByteArray
    if (cmd == "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush") {
        PreventRetractingMessageCore.handleInfoSyncPush(protoBuf, param)
    } else if (cmd == "trpc.msg.olpush.OlPushService.MsgPush") {
        PreventRetractingMessageCore.handleMsgPush(protoBuf, param)
    }
}
```

4. 核心处理与拦截代码 方法结尾控制回调和显示即可

```kotlin

import de.robv.android.xposed.XC_MethodHook
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import top.artmoe.inao.entries.MsgPush
import top.artmoe.inao.entries.NewSyncPush
import top.artmoe.inao.entries.QQMessage
import java.io.ByteArrayOutputStream

interface RetractingCallback {
    fun onFriendChatMessageRecall(data: FriendChatMessageRecall)
    fun onGroupChatMessageRecall(data: GroupChatMessageRecall)
}

@Serializable
data class FriendChatMessageRecall(
    @SerialName("peerUid")
    val peerUid: String,
    @SerialName("msgSeq")
    val msgSeq: Int,
)

@Serializable
data class GroupChatMessageRecall(
    @SerialName("groupUin")
    val groupUin: String,
    @SerialName("operatorUid")
    val operatorUid: String,
    @SerialName("msgSeq")
    val msgSeq: Int,
)

/**
 * 防撤回核心解析
 * by 叶叶,suzhelan
 */
@OptIn(ExperimentalSerializationApi::class)
object NewPreventRetractingMessageCore {
    private var onRecallMessageDetected: RetractingCallback? = null

    private data class ProtoVarInt(
        val value: Int,
        val nextIndex: Int,
    )

    fun setOnRecallMessageDetected(callback: RetractingCallback) {
        onRecallMessageDetected = callback
    }

    fun handleInfoSyncPush(buffer: ByteArray, param: XC_MethodHook.MethodHookParam) {
        val infoSyncPush = ProtoBuf.decodeFromByteArray<NewSyncPush>(buffer)
        val syncRecallContent = infoSyncPush.syncRecallContent ?: return
        val syncInfoBody = syncRecallContent.syncInfoBody ?: return
        if (syncInfoBody.isEmpty()) {
            return
        }
        val friendList = mutableListOf<FriendChatMessageRecall>()
        val troopList = mutableListOf<GroupChatMessageRecall>()
        val newSyncInfoBody = syncInfoBody.map { syncInfoBodyBytes ->
            rewriteSyncInfoBody(syncInfoBodyBytes, friendList, troopList)
        }
        if (troopList.isEmpty() && friendList.isEmpty()) {
            return
        }
        val newInfoSyncPush = infoSyncPush.copy(
            syncRecallContent = syncRecallContent.copy(
                syncInfoBody = newSyncInfoBody
            )
        )
        param.args[1] = ProtoBuf.encodeToByteArray(newInfoSyncPush)
        troopList.forEach {
            onRecallMessageDetected?.onGroupChatMessageRecall(it)
        }
        friendList.forEach {
            onRecallMessageDetected?.onFriendChatMessageRecall(it)
        }
    }

    private fun rewriteSyncInfoBody(
        syncInfoBodyBytes: ByteArray,
        friendList: MutableList<FriendChatMessageRecall>,
        troopList: MutableList<GroupChatMessageRecall>,
    ): ByteArray {
        val output = ByteArrayOutputStream(syncInfoBodyBytes.size)
        var index = 0
        var changed = false

        while (index < syncInfoBodyBytes.size) {
            val fieldStart = index
            val key = readVarInt(syncInfoBodyBytes, index) ?: return syncInfoBodyBytes
            index = key.nextIndex
            val fieldNumber = key.value ushr 3
            val wireType = key.value and 0x07

            val fieldEnd = when (wireType) {
                0 -> readVarInt(syncInfoBodyBytes, index)?.nextIndex ?: return syncInfoBodyBytes
                1 -> (index + 8).takeIf { it <= syncInfoBodyBytes.size } ?: return syncInfoBodyBytes
                2 -> {
                    val length = readVarInt(syncInfoBodyBytes, index) ?: return syncInfoBodyBytes
                    val payloadEnd = length.nextIndex + length.value
                    payloadEnd.takeIf { it <= syncInfoBodyBytes.size } ?: return syncInfoBodyBytes
                }

                5 -> (index + 4).takeIf { it <= syncInfoBodyBytes.size } ?: return syncInfoBodyBytes
                else -> return syncInfoBodyBytes
            }

            if (fieldNumber == 8 && wireType == 2) {
                val length = readVarInt(syncInfoBodyBytes, index) ?: return syncInfoBodyBytes
                val payloadStart = length.nextIndex
                val payloadEnd = payloadStart + length.value
                val msgBytes = syncInfoBodyBytes.copyOfRange(payloadStart, payloadEnd)
                if (shouldRemoveRecallMessage(msgBytes, friendList, troopList)) {
                    changed = true
                } else {
                    output.write(syncInfoBodyBytes, fieldStart, fieldEnd - fieldStart)
                }
            } else {
                output.write(syncInfoBodyBytes, fieldStart, fieldEnd - fieldStart)
            }
            index = fieldEnd
        }

        return if (changed) output.toByteArray() else syncInfoBodyBytes
    }

    private fun shouldRemoveRecallMessage(
        msgBytes: ByteArray,
        friendList: MutableList<FriendChatMessageRecall>,
        troopList: MutableList<GroupChatMessageRecall>,
    ): Boolean {
        val qqMessage = runCatching {
            ProtoBuf.decodeFromByteArray<QQMessage>(msgBytes)
        }.getOrNull() ?: return false
        val messageBody = qqMessage.messageBody ?: return false
        val msgType = qqMessage.messageContentInfo.msgType
        val msgSubType = qqMessage.messageContentInfo.msgSubType

        return when (msgType) {
            528 if msgSubType == 138 -> {
                val c2cRecall = runCatching {
                    ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.C2CRecallOperationInfo>(
                        messageBody.operationInfo
                    )
                }.getOrNull() ?: return false
                friendList.add(
                    FriendChatMessageRecall(
                        qqMessage.messageHead.senderUid,
                        c2cRecall.info.msgSeq
                    )
                )
                true
            }

            732 if msgSubType == 17 -> {
                if (messageBody.operationInfo.size <= 7) {
                    return false
                }
                val groupRecall = runCatching {
                    ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.GroupRecallOperationInfo>(
                        messageBody.operationInfo.copyOfRange(7, messageBody.operationInfo.size)
                    )
                }.getOrNull() ?: return false
                troopList.add(
                    GroupChatMessageRecall(
                        groupRecall.peerId.toString(),
                        groupRecall.info.operatorUid,
                        groupRecall.info.msgInfo.msgSeq
                    )
                )
                true
            }

            else -> false
        }
    }

    private fun readVarInt(bytes: ByteArray, startIndex: Int): ProtoVarInt? {
        var result = 0
        var shift = 0
        var index = startIndex

        while (index < bytes.size && shift < Int.SIZE_BITS) {
            val byte = bytes[index].toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            index++
            if ((byte and 0x80) == 0) {
                return ProtoVarInt(result, index)
            }
            shift += 7
        }
        return null
    }


    fun handleMsgPush(buffer: ByteArray, param: XC_MethodHook.MethodHookParam) {
        val msgPush = ProtoBuf.decodeFromByteArray<MsgPush>(buffer)
        //检查messageBody是否为空
        if (msgPush.qqMessage.messageBody == null) {
            return
        }
        val msg = msgPush.qqMessage
        val msgType = msg.messageContentInfo.msgType
        val subType = msg.messageContentInfo.subSeq
        val operationInfoByteArray = msg.messageBody.operationInfo

        when (msgType) {
            528 -> if (subType == 138) onC2CRecallByMsgPush(operationInfoByteArray, msgPush, param)
            732 -> if (subType == 17) onGroupRecallByMsgPush(operationInfoByteArray, msgPush, param)
        }
    }

    private fun onGroupRecallByMsgPush(
        operationInfoByteArray: ByteArray, // 1.3.2
        msgPush: MsgPush,
        param: XC_MethodHook.MethodHookParam,
    ) {
        //断言 messageBody不为空
        check(msgPush.qqMessage.messageBody != null)
        val firstPart = operationInfoByteArray.copyOfRange(0, 7) // 1.3.2.5 and 1.3.2.0
        val secondPart = operationInfoByteArray.copyOfRange(7, operationInfoByteArray.size)
        val operationInfo =
            ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.GroupRecallOperationInfo>(secondPart)

        val operatorUid = operationInfo.info.operatorUid// 操作者UID

        //groupUin
        val groupPeerId = operationInfo.peerId.toString()
        //msg seq
        val recallMsgSeq = operationInfo.info.msgInfo.msgSeq

        val newOperationInfoByteArray = firstPart + ProtoBuf.encodeToByteArray(
            operationInfo.copy(
                msgSeq = 1,
                info = operationInfo.info.copy(
                    msgInfo = operationInfo.info.msgInfo.copy(msgSeq = 1)
                )
            )
        )

        val newMsgPush = msgPush.copy(
            qqMessage = msgPush.qqMessage.copy(
                messageBody = msgPush.qqMessage.messageBody.copy(
                    operationInfo = newOperationInfoByteArray
                )
            )
        )
        param.args[1] = ProtoBuf.encodeToByteArray(newMsgPush)
        //写入撤回缓存 给ui显示
        onRecallMessageDetected?.onGroupChatMessageRecall(
            GroupChatMessageRecall(groupPeerId, operatorUid, recallMsgSeq)
        )
    }

    private fun onC2CRecallByMsgPush(
        operationInfoByteArray: ByteArray,
        msgPush: MsgPush,
        param: XC_MethodHook.MethodHookParam,
    ) {
        //断言 messageBody不为空
        check(msgPush.qqMessage.messageBody != null)
        val operationInfo =
            ProtoBuf.decodeFromByteArray<QQMessage.MessageBody.C2CRecallOperationInfo>(
                operationInfoByteArray
            )

        //msg seq
        val recallMsgSeq = operationInfo.info.msgSeq
        //peerUid
        val operatorUid = operationInfo.info.operatorUid

        val newOperationInfoByteArray = ProtoBuf.encodeToByteArray(
            operationInfo.copy(
                info = operationInfo.info.copy(msgSeq = 1)
            )
        )

        val newMsgPush = msgPush.copy(
            qqMessage = msgPush.qqMessage.copy(
                messageBody = msgPush.qqMessage.messageBody.copy(
                    operationInfo = newOperationInfoByteArray
                )
            )
        )

        param.args[1] = ProtoBuf.encodeToByteArray(newMsgPush)
        //写入缓存
        onRecallMessageDetected?.onFriendChatMessageRecall(
            FriendChatMessageRecall(
                operatorUid,
                recallMsgSeq
            )
        )
    }
}

```
