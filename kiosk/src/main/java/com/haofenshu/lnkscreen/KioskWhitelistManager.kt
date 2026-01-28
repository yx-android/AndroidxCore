package com.haofenshu.lnkscreen

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log

class KioskWhitelistManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "KioskWhitelistManager"
        private const val PREF_NAME = "kiosk_whitelist"
        private const val KEY_CUSTOM_WHITELIST = "custom_whitelist"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: KioskWhitelistManager? = null

        fun getInstance(context: Context): KioskWhitelistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KioskWhitelistManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // 提取静态系统白名单，减少每次调用时的集合创建开销
        private val BASE_SYSTEM_WHITELIST = setOf(
            "com.tencent.mm",
            "com.tencent.wework",
            "com.quark.browser",
            "com.baidu.netdisk",
            "com.jingzhunxue.aicoach",
            "com.jingzhunxue.tifenben",
            "com.android.inputmethod.latin",
            "cn.wps.moffice_eng",
            "com.android.modulemetadata",
            "com.android.connectivity.resources",
            "com.android.server.deviceconfig.resources",
            "com.qualcomm.qti.cne",
            "com.android.dreams.phototable",
            "com.android.providers.contacts",
            "com.android.dreams.basic",
            "com.android.companiondevicemanager",
            "com.android.cts.priv.ctsshim",
            "com.android.mms.service",
            "com.android.providers.downloads",
            "com.android.bluetoothmidiservice",
            "com.android.credentialmanager",
            "com.android.networkstack",
            "com.android.networkstack.overlay",
            "android.ext.shared",
            "com.android.networkstack.tethering",
            "com.android.keychain",
            "com.android.virtualmachine.res",
            "com.android.shell",
            "com.android.inputdevices",
            "com.qti.dpmserviceapp",
            "com.android.sharedstoragebackup",
            "com.android.providers.calendar",
            "com.android.incallui",
            "com.android.frameworkres.overlay",
            "com.android.providers.blockednumber",
            "com.android.statementservice",
            "com.android.printservice.recommendation",
            "com.android.proxyhandler",
            "com.android.safetycenter.resources",
            "com.android.managedprovisioning",
            "com.android.emergency",
            "com.android.healthconnect.controller",
            "com.android.cellbroadcastreceiver.module",
            "com.qualcomm.location",
            "com.unionpay.tsmservice",
            "com.android.backupconfirm",
            "com.android.mtp",
            "com.android.cellbroadcastservice",
            "com.android.appsearch.apk",
            "com.android.internal.systemui.navbar.hide",
            "com.android.systemui.overlay",
            "com.android.wallpapercropper",
            "com.android.systemui",
            "com.android.internal.systemui.navbar.gestural",
            "com.qualcomm.timeservice",
            "cn.honor.qinxuan",
            "com.android.wallpaperbackup",
            "com.android.internal.systemui.navbar.threebutton",
            "com.android.localtransport",
            "android",
            "com.android.rkpdapp",
            "com.android.permissioncontroller",
            "com.baidu.swan",
            "com.android.se",
            "com.android.pacprocessor",
            "com.android.wifi.resources.overlay",
            "com.honor.yoyoappsug",
            "com.android.providers.media.module",
            "com.android.settings",
            "com.android.cameraextensions",
            "com.android.devicelockcontroller",
            "com.android.documentsui",
            "com.android.adservices.api",
            "com.android.health.connect.backuprestore",
            "vendor.qti.hardware.cacert.server",
            "com.android.networkstack.tethering.overlay",
            "com.android.providers.downloads.ui",
            "com.android.ons",
            "com.android.intentresolver",
            "com.android.certinstaller",
            "android.ext.services",
            "com.android.wifi.resources",
            "com.android.wifi.dialog",
            "com.android.captiveportallogin",
            "com.android.providers.telephony",
            "com.android.wallpaper.livepicker",
            "com.android.sdksandbox",
            "vendor.qti.iwlan",
            "com.android.providers.settings",
            "com.android.phone",
            "com.android.location.fused",
            "com.android.vpndialogs",
            "com.android.ondevicepersonalization.services",
            "com.android.htmlviewer",
            "com.qti.qcc",
            "com.hihonor.printservice",
            "com.android.providers.userdictionary",
            "com.android.providers.contactkeys",
            "com.android.cts.ctsshim",
            "com.android.bluetooth",
            "com.hihonor.cameraextension",
            "com.android.packageinstaller",
            "com.hihonor.photos",
            "com.android.printspooler",
            "androidhnext",
            "com.android.providers.partnerbookmarks",
            "com.android.dynsystem",
            "com.android.microdroid.empty_payload",
            "com.android.hotspot2.osulogin",
            "com.android.externalstorage",
            "vendor.qti.imsdatachannel",
            "com.android.server.telecom",
            "com.coloros.wirelesssettings",
            "com.coloros.simsettings",
            "com.android.settings.intelligence",
            "com.android.providers.settings",
            "com.android.theme.icon_pack.filled.settings",
            "com.android.theme.icon_pack.circular.settings",
            "com.android.theme.icon_pack.rounded.settings",
            "com.oplus.wirelesssettings",
            "com.android.settings.overlay.common",
            "com.oplus.camera",
            "com.coloros.gallery3d",
            "com.coloros.filemanager"
        )
    }

    // 内存缓存，减少频繁读取 SP 和合并集合的开销
    @Volatile
    private var cachedCustomWhitelist: Set<String> = emptySet()

    @Volatile
    private var cachedFullWhitelist: Set<String> = emptySet()

    private val finalSystemWhitelist: Set<String> by lazy {
        BASE_SYSTEM_WHITELIST + context.packageName
    }

    init {
        refreshCache()
    }

    private fun refreshCache() {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        cachedCustomWhitelist =
            prefs.getStringSet(KEY_CUSTOM_WHITELIST, emptySet())?.toSet() ?: emptySet()
        cachedFullWhitelist = finalSystemWhitelist + cachedCustomWhitelist
    }

    fun getAllWhitelistPackages(): Set<String> = cachedFullWhitelist

    @Synchronized
    fun addToWhitelist(packageName: String): Boolean {
        return addToWhitelist(listOf(packageName))
    }

    @Synchronized
    fun addToWhitelist(packageNames: Collection<String>): Boolean {
        return try {
            val currentList = cachedCustomWhitelist.toMutableSet()
            val toAdd = packageNames.filter { !isInWhitelist(it) }
            if (toAdd.isEmpty()) return false

            if (currentList.addAll(toAdd)) {
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putStringSet(KEY_CUSTOM_WHITELIST, currentList)
                    .apply()
                refreshCache()
                Log.d(TAG, "已添加到自定义白名单: ${toAdd.size} 个包")
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "批量添加白名单失败", e)
            false
        }
    }

    @Synchronized
    fun removeFromWhitelist(packageName: String): Boolean {
        return removeFromWhitelist(listOf(packageName))
    }

    @Synchronized
    fun removeFromWhitelist(packageNames: Collection<String>): Boolean {
        return try {
            val currentList = cachedCustomWhitelist.toMutableSet()
            if (currentList.removeAll(packageNames.toSet())) {
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putStringSet(KEY_CUSTOM_WHITELIST, currentList)
                    .apply()
                refreshCache()
                Log.d(TAG, "已从自定义白名单移除: ${packageNames.size} 个包")
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "批量移除白名单失败", e)
            false
        }
    }

    fun isInWhitelist(packageName: String): Boolean = cachedFullWhitelist.contains(packageName)

    fun getWhitelistAppsForKiosk(): Array<String> {
        val pm = context.packageManager
        return cachedFullWhitelist.filter { packageName ->
            try {
                pm.getApplicationInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }.toTypedArray()
    }

    fun getWhitelistStats(): String {
        return """
            白名单统计:
            - 系统白名单: ${finalSystemWhitelist.size} 个应用
            - 自定义白名单: ${cachedCustomWhitelist.size} 个应用
            - 总计: ${cachedFullWhitelist.size} 个应用
        """.trimIndent()
    }
}