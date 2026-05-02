/**
 * Copyright 2024-2026 github@Suzhelan,github@leafmoes
 *
 * suzhelan@gmail.com
 *
 * It is forbidden to use the file and this source code for commercial purposes
 * please let me know before modifying the file and source code
 * If your project is open source, please indicate that this feature is from https://github.com/suzhelan/TimTool
 */
package top.artmoe.inao.item

import de.robv.android.xposed.XC_MethodHook
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import re.limus.timas.api.TIMEnvTool
import re.limus.timas.hook.items.message.helper.GroupNickResolver
import re.limus.timas.hook.items.message.helper.LocalGrayTips
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
        val targetUid = operationInfo.info.msgInfo.senderUid

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

        // 过滤“我撤回我自己”
        runCatching {
            val myUid = TIMEnvTool.getCurrentUin()?.let { TIMEnvTool.getUidFromUin(it) }
            if (!myUid.isNullOrBlank() && operatorUid == myUid && targetUid == myUid) return
        }

        val operatorUin = TIMEnvTool.getUinFromUid(operatorUid) ?: "0"
        val targetUin = TIMEnvTool.getUinFromUid(targetUid) ?: "0"
        val operatorNick = GroupNickResolver.getNickOrNull(groupPeerId, operatorUin)?.text ?: operatorUin

        val nodes = mutableListOf<LocalGrayTips.Node>()
        nodes.add(LocalGrayTips.Node.Member(operatorUid, operatorUin, operatorNick))
        nodes.add(LocalGrayTips.Node.Text(" 尝试撤回 "))

        val recentAbstract: String
        if (operatorUid == targetUid) {
            nodes.add(LocalGrayTips.Node.MsgRef("一条消息", recallMsgSeq.toLong()))
            recentAbstract = "$operatorNick 尝试撤回 一条消息"
        } else {
            val targetNick = GroupNickResolver.getNickOrNull(groupPeerId, targetUin)?.text ?: targetUin
            nodes.add(LocalGrayTips.Node.Member(targetUid, targetUin, targetNick))
            nodes.add(LocalGrayTips.Node.Text(" 的 "))
            nodes.add(LocalGrayTips.Node.MsgRef("消息", recallMsgSeq.toLong()))
            recentAbstract = "$operatorNick 尝试撤回 $targetNick 的 消息"
        }

        LocalGrayTips.send(
            wrapperSession = param.thisObject,
            chatTypeField = "KCHATTYPEGROUP",
            id = groupPeerId,
            items = nodes,
            recentAbstract = recentAbstract
        )

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

        // 使用统一的 LocalGrayTips 发送提示
        LocalGrayTips.send(
            wrapperSession = param.thisObject,
            chatTypeField = "KCHATTYPEC2C",
            id = operatorUid,
            items = listOf(
                LocalGrayTips.Node.Text("对方尝试撤回 "),
                LocalGrayTips.Node.MsgRef("一条消息", recallMsgSeq.toLong())
            ),
            recentAbstract = "对方尝试撤回一条消息"
        )

        //写入缓存
        onRecallMessageDetected?.onFriendChatMessageRecall(
            FriendChatMessageRecall(
                operatorUid,
                recallMsgSeq
            )
        )
    }
}