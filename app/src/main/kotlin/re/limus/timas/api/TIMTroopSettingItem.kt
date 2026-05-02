package re.limus.timas.api

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import re.limus.timas.annotations.ApiItems
import re.limus.timas.hook.base.XBridge
import top.sacz.xphelper.dexkit.DexFinder
import top.sacz.xphelper.ext.toClass
import java.lang.reflect.Array as ReflectArray
import java.util.WeakHashMap

@ApiItems
object TIMTroopSettingItem : XBridge() {

    private const val GROUP_TITLE = "神秘功能"

    data class Item(
        val key: String,
        val title: CharSequence,
        val rightText: CharSequence = "",
        val index: Int = Int.MAX_VALUE,
        val onClick: (View) -> Unit
    )

    private val items = mutableListOf<Item>()

    private val troopSettingFragmentV2 =
        "com.tencent.mobileqq.troop.troopsetting.activity.TroopSettingFragmentV2".toClass()
    private val quiListItemAdapter = "com.tencent.mobileqq.widget.listitem.QUIListItemAdapter".toClass()
    private val groupClass = "com.tencent.mobileqq.widget.listitem.Group".toClass()
    private val itemConfigClass = "com.tencent.mobileqq.widget.listitem.b".toClass()
    private val singleLineConfigClass = "com.tencent.mobileqq.widget.listitem.u".toClass()
    private val leftConfigClass = "com.tencent.mobileqq.widget.listitem.u\$b".toClass()
    private val rightConfigClass = "com.tencent.mobileqq.widget.listitem.u\$c".toClass()
    private val leftTextConfigClass = "com.tencent.mobileqq.widget.listitem.u\$b\$d".toClass()
    private val rightTextConfigClass = "com.tencent.mobileqq.widget.listitem.u\$c\$f".toClass()

    private val troopSettingAdapters = WeakHashMap<Any, Unit>()

    private val groupArrayClass by lazy { ReflectArray.newInstance(groupClass, 0).javaClass }
    private val itemConfigArrayClass by lazy { ReflectArray.newInstance(itemConfigClass, 0).javaClass }

    override fun onHook(ctx: Context, loader: ClassLoader) {
        hookTroopSettingAdapter()
        hookAdapterGroups()
    }

    fun addItem(
        key: String,
        title: CharSequence,
        rightText: CharSequence = "",
        index: Int = Int.MAX_VALUE,
        onClick: (View) -> Unit
    ) {
        items.removeAll { it.key == key }
        items.add(
            Item(
                key = key,
                title = title,
                rightText = rightText,
                index = index,
                onClick = onClick
            )
        )
    }

    fun removeItem(key: String) {
        items.removeAll { it.key == key }
    }

    private fun hookTroopSettingAdapter() {
        DexFinder.findMethod {
            declaredClass = troopSettingFragmentV2
            methodName = "onCreateView"
            parameters = arrayOf(LayoutInflater::class.java, ViewGroup::class.java, Bundle::class.java)
            returnType = View::class.java
            paramCount = 3
        }.hookAfter {
            thisObject.findFirstFieldValue(quiListItemAdapter)?.let {
                troopSettingAdapters[it] = Unit
            }
        }
    }

    private fun hookAdapterGroups() {
        DexFinder.findMethod {
            declaredClass = quiListItemAdapter
            methodName = "E"
            parameters = arrayOf(groupArrayClass)
            returnType = Void.TYPE
            paramCount = 1
        }.hookBefore {
            if (thisObject !in troopSettingAdapters) return@hookBefore
            if (items.isEmpty()) return@hookBefore

            val groups = args[0] ?: return@hookBefore
            args[0] = groups.insertGroup(createGroup(items.sortedBy { it.index }), items.minOf { it.index })
        }
    }

    private fun createGroup(items: List<Item>): Any {
        val configArray = ReflectArray.newInstance(itemConfigClass, items.size)
        items.forEachIndexed { index, item ->
            ReflectArray.set(configArray, index, item.createSingleLineConfig())
        }

        return groupClass
            .getDeclaredConstructor(CharSequence::class.java, itemConfigArrayClass)
            .apply { isAccessible = true }
            .newInstance(GROUP_TITLE, configArray)
    }

    private fun Item.createSingleLineConfig(): Any {
        val left = leftTextConfigClass
            .getDeclaredConstructor(CharSequence::class.java)
            .apply { isAccessible = true }
            .newInstance(title)

        val right = rightTextConfigClass
            .getDeclaredConstructor(
                CharSequence::class.java,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!
            )
            .apply { isAccessible = true }
            .newInstance(rightText, true, false)

        return singleLineConfigClass
            .getDeclaredConstructor(leftConfigClass, rightConfigClass)
            .apply { isAccessible = true }
            .newInstance(left, right)
            .also { config ->
                config.setOnClickListener {
                    onClick(it)
                }
            }
    }

    private fun Any.insertGroup(group: Any, index: Int): Any {
        val oldSize = ReflectArray.getLength(this)
        val insertIndex = index.coerceIn(0, oldSize)
        val newGroups = ReflectArray.newInstance(groupClass, oldSize + 1)

        for (oldIndex in 0 until oldSize) {
            val targetIndex = if (oldIndex < insertIndex) oldIndex else oldIndex + 1
            ReflectArray.set(newGroups, targetIndex, ReflectArray.get(this, oldIndex))
        }
        ReflectArray.set(newGroups, insertIndex, group)

        return newGroups
    }

    private fun Any.setOnClickListener(onClick: (View) -> Unit) {
        itemConfigClass
            .getDeclaredMethod("w", View.OnClickListener::class.java)
            .apply { isAccessible = true }
            .invoke(this, View.OnClickListener { onClick(it) })
    }

    private fun Any.findFirstFieldValue(fieldClass: Class<*>): Any? {
        var current: Class<*>? = javaClass
        while (current != null) {
            current.declaredFields.firstOrNull { field ->
                fieldClass.isAssignableFrom(field.type)
            }?.let { field ->
                return runCatching {
                    field.isAccessible = true
                    field.get(this)
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }
}
