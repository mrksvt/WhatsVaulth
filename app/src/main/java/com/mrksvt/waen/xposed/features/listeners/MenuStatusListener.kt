package com.mrksvt.waen.xposed.features.listeners

import android.view.Menu
import android.view.MenuItem
import com.mrksvt.waen.xposed.core.Feature
import com.mrksvt.waen.xposed.core.components.StatusItemWpp
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator
import com.mrksvt.waen.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.query.enums.StringMatchType

class MenuStatusListener(classLoader: ClassLoader, preferences:SharedPreferences) :
    Feature(classLoader, preferences) {

    companion object {
        @JvmStatic
        val menuStatuses = LinkedHashSet<OnMenuItemStatusListener>()

        @JvmStatic
        lateinit var statusData: StatusData
    }


    override fun doHook() {

        val menuStatusMethod = Unobfuscator.loadMenuStatusMethod(classLoader)
        val menuManagerClass = Unobfuscator.loadMenuManagerClass(classLoader)
        XposedBridge.log("[MenuStatus] method=${menuStatusMethod.declaringClass.name}.${menuStatusMethod.name}")

        val statusPlaybackBaseFragmentClass =Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith,"StatusPlaybackBaseFragment")
        val statusPlaybackContactFragmentClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith,"StatusPlaybackContactFragment")
        val listStatusField = ReflectionUtils.getFieldByExtendType(
            statusPlaybackContactFragmentClass,
            List::class.java
        )

        XposedBridge.hookMethod(menuStatusMethod, object : XC_MethodHook() {

            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val fieldObjects = param.method.declaringClass.declaredFields
                    .mapNotNull { field -> ReflectionUtils.getObjectField(field, param.thisObject) }

                val fragmentInstance: Any =
                    if (param.thisObject != null && statusPlaybackContactFragmentClass.isInstance(
                            param.thisObject
                        )
                    ) {
                        param.thisObject
                    } else {
                        fieldObjects.firstOrNull { statusPlaybackBaseFragmentClass.isInstance(it) }
                            ?: return
                    }

                val menu: Menu = if (param.args.isNotEmpty() && param.args[0] is Menu) {
                    param.args[0] as Menu
                } else {
                    val menuManager = fieldObjects.firstOrNull { menuManagerClass.isInstance(it) }
                    val menuField =
                        ReflectionUtils.getFieldByExtendType(menuManagerClass, Menu::class.java)
                    ReflectionUtils.getObjectField(menuField, menuManager) as Menu
                }

                val listStatus = listStatusField?.get(fragmentInstance) as List<*>

                statusData = StatusData(listStatus, fragmentInstance)

                XposedBridge.log(
                    "[MenuStatus] triggered frag=${fragmentInstance.javaClass.name} statusSize=${listStatus?.size ?: "NULL"} idx=${runCatching { XposedHelpers.getObjectField(fragmentInstance, "A02") }.getOrNull()}"
                )

                var added = 0
                try {
                    for (menuStatus in menuStatuses) {
                        val menuItem = menuStatus.addMenu(menu, statusData) ?: continue

                        menuItem.setOnMenuItemClickListener { item ->
                            menuStatus.onClick(item, statusData)
                            true
                        }
                        added++
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("[MenuStatus] addMenu EX: ${e.javaClass.simpleName}: ${e.message}")
                }
                XposedBridge.log("[MenuStatus] done statusSize=${listStatus?.size ?: "NULL"} added=$added")
            }
        })
    }

    override fun getPluginName(): String {
        return "Menu Status"
    }

    open class StatusData(private val listStatus: List<*>, private val fragmentInstance: Any) {

        private var cachedItemList: List<StatusItemWpp>? = null

        val currentItem: StatusItemWpp
            get() = getCurrentItemList()[currentIndex]

        val currentIndex: Int
            get() = XposedHelpers.getObjectField(fragmentInstance, "A02") as Int

        fun getCurrentItemList(): List<StatusItemWpp> {
            return cachedItemList ?: listStatus.mapNotNull { obj ->
                try {
                    StatusItemWpp.from(obj)
                } catch (e: Throwable) {
                    XposedBridge.log("[MenuStatus] from fail cls=${obj?.javaClass?.name}: ${e}")
                    null
                }
            }.also { cachedItemList = it }
        }
    }

    abstract class OnMenuItemStatusListener {

        abstract fun addMenu(
            menu: Menu,
            statusData: StatusData,
        ): MenuItem?

        abstract fun onClick(
            item: MenuItem,
            statusData: StatusData
        )
    }
}