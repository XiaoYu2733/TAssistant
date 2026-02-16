package re.limus.timas.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import re.limus.timas.R
import re.limus.timas.api.ContactUtils
import re.limus.timas.api.CreateElement
import re.limus.timas.api.TIMSendMsgTool
import re.limus.timas.hook.utils.XLog
import re.limus.timas.util.PathTool
import top.sacz.xphelper.activity.BaseActivity
import java.io.File
import java.io.FileOutputStream

private const val SAF_REQUEST_CODE = 0x5AF1
private const val SAF_CACHE_DIR = "saf_send"
private const val EXTRA_ORIGINAL_BUNDLE = "ta_saf_original"

class SAFAgentActivity : BaseActivity() {
    
    companion object {
        fun launch(context: Context, extras: Bundle) {
            context.startActivity(
                Intent(context, SAFAgentActivity::class.java).apply {
                    putExtra(
                        "proxy_target_activity",
                        "cooperation.qlink.QlinkStandardDialogActivity"
                    )
                    putExtra(EXTRA_ORIGINAL_BUNDLE, extras)
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        setVisible(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.NoDisplay)
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                SAF_REQUEST_CODE
            )
        } catch (e: Throwable) {
            XLog.e("SAFPicker: open failed", e)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SAF_REQUEST_CODE || resultCode != RESULT_OK || data == null) {
            finish()
            return
        }

        val uris = collectUris(data)
        if (uris.isEmpty()) {
            finish()
            return
        }

        val contact = resolveContact()
        if (contact == null) {
            Toast.makeText(this, "无法获取当前会话", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Thread {
            try {
                val cacheDir = File(PathTool.getModuleCachePath(SAF_CACHE_DIR))
                cleanOldCache(cacheDir)

                for ((i, uri) in uris.withIndex()) {
                    try {
                        val file = copyUriToCache(uri, cacheDir, i) ?: continue
                        val element = CreateElement.createFileElement(file.absolutePath)
                        TIMSendMsgTool.sendMsg(contact, arrayListOf(element))
                        if (uris.size > 1) Thread.sleep(500)
                    } catch (e: Throwable) {
                        XLog.e("SAFPicker: send file #$i failed", e)
                    }
                }
            } catch (e: Throwable) {
                XLog.e("SAFPicker: send failed", e)
            } finally {
                runOnUiThread { finish() }
            }
        }.start()
    }

    private fun collectUris(data: Intent): List<Uri> {
        val result = LinkedHashSet<Uri>()
        data.data?.let { result.add(it) }
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { result.add(it) }
            }
        }
        return result.toList()
    }

    private fun resolveContact(): Any? {
        val extras = intent.getBundleExtra(EXTRA_ORIGINAL_BUNDLE)
            ?: return ContactUtils.getCurrentContact()

        val uin = extras.getString("targetUin")
            ?: extras.getString("key_peerUin")
            ?: extras.getLong("key_peerUin_long", 0).takeIf { it > 0 }?.toString()
            ?: return ContactUtils.getCurrentContact()

        var peerType = extras.getInt("peerType", -1)
        if (peerType < 0) {
            val keyChatType = extras.getInt("key_chat_type", -1)
            if (keyChatType > 0) peerType = keyChatType - 1
        }
        if (peerType < 0) return ContactUtils.getCurrentContact()

        val chatType = peerType + 1
        return try {
            ContactUtils.getContact(chatType, uin)
        } catch (_: Throwable) {
            ContactUtils.getCurrentContact()
        }
    }

    private fun copyUriToCache(uri: Uri, cacheDir: File, index: Int): File? {
        val name = queryFileName(uri) ?: "file_$index"
        val safeName = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val target = File(cacheDir, "${System.currentTimeMillis()}_${index}_$safeName")

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: return null

        return if (target.length() > 0) target else { target.delete(); null }
    }

    private fun queryFileName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (col >= 0) return cursor.getString(col)?.takeIf { it.isNotBlank() }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun cleanOldCache(dir: File) {
        if (!dir.exists()) return
        val threshold = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        dir.listFiles()?.filter { it.lastModified() < threshold }?.forEach { it.delete() }
    }
}
