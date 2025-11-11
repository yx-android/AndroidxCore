package com.haofenshu.lnkscreen

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

class KioskWhitelistManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "KioskWhitelistManager"
        private const val PREF_NAME = "kiosk_whitelist"
        private const val KEY_CUSTOM_WHITELIST = "custom_whitelist"

        @Volatile
        private var INSTANCE: KioskWhitelistManager? = null

        fun getInstance(context: Context): KioskWhitelistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KioskWhitelistManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getSystemWhitelistPackages(): Set<String> {
        return setOf(
            // 主应用
            "com.jingzhunxue.aicoach",
            "com.jingzhunxue.tifenben",
            // AI教练主应用
            context.packageName,                       // 当前kiosk应用

            // 输入法
            "com.android.inputmethod.latin",          // 系统输入法
//            "com.google.android.inputmethod.latin",   // Google输入法
//            "com.sohu.inputmethod.sogou",             // 搜狗输入法
//            "com.baidu.input",                        // 百度输入法
//            "com.baidu.input_hihonor",                // 百度输入法Honor版
//            "com.iflytek.inputmethod",                // 讯飞输入法

            // WPS
            "cn.wps.moffice_eng",

            // Honor系统应用（根据readme.md的完整列表）
//            "com.hihonor.trustspace",
            "com.android.modulemetadata",
            "com.android.connectivity.resources",
            "com.android.server.deviceconfig.resources",
//            "com.hihonor.systemserver",
//            "com.hihonor.quickengine",
//            "com.hihonor.quickgame",
//            "com.hihonor.hiview",
//            "com.hihonor.filemanager",
//            "com.hihonor.secime",
//            "com.hihonor.fitness.healthservice",
//            "com.hihonor.notepad",
            "com.qualcomm.qti.cne",
//            "com.hihonor.contacts.sync",
//            "com.hihonor.edulauncher",
            "com.android.dreams.phototable",
//            "com.hihonor.android.instantshare",
//            "com.hihonor.cooperatedconfig",
            "com.android.providers.contacts",
//            "com.hihonor.smartiot",
//            "com.hihonor.assistant",
//            "com.hihonor.hnvideoplayer",
            "com.android.dreams.basic",
            "com.android.companiondevicemanager",
            "com.android.cts.priv.ctsshim",
//            "com.hihonor.brain",
            "com.android.mms.service",
            "com.android.providers.downloads",
//            "com.hihonor.globalwrite",
            "com.android.bluetoothmidiservice",
            "com.android.credentialmanager",
            "com.android.networkstack",
            "com.android.networkstack.overlay",
            "android.ext.shared",
//            "com.hihonor.ddmp",
//            "com.hihonor.koBackup",
//            "com.hihonor.audioaccessorymanager",
            "com.android.networkstack.tethering",
//            "com.hihonor.android.hnaps",
            "com.android.keychain",
//            "com.hihonor.voiceengine",
//            "com.hihonor.associateassistant",
//            "com.hihonor.stylus.mpenzone",
//            "com.hihonor.android.thememanager",
            "com.android.virtualmachine.res",
            "com.android.shell",
//            "com.hihonor.credentialmanager.overlay",
//            "com.hihonor.servicecenter",
//            "com.hihonor.aipluginengine",
            "com.android.inputdevices",
//            "com.hihonor.hiviewtunnel",
//            "com.hihonor.securitypluginbase",
            "com.qti.dpmserviceapp",
            "com.android.sharedstoragebackup",
            "com.android.providers.calendar",
//            "com.hihonor.android.chr",
            "com.android.incallui",
//            "com.hihonor.phoneservice",
            "com.android.frameworkres.overlay",
            "com.android.providers.blockednumber",
//            "com.hihonor.android.pushagent",
            "com.android.statementservice",
//            "com.hihonor.assetsyncservice",
            "com.android.printservice.recommendation",
//            "com.hihonor.devicefinder",
//            "com.hihonor.vmall",
//            "com.hihonor.hndockbar",
            "com.android.proxyhandler",
//            "com.hihonor.dz.reader",
            "com.android.safetycenter.resources",
            "com.android.managedprovisioning",
            "com.android.emergency",
            "com.android.healthconnect.controller",
//            "com.hihonor.maplib",
//            "com.hihonor.android.totemweather",
//            "com.hihonor.detectrepair",
//            "com.hihonor.kidsmode",
            "com.android.cellbroadcastreceiver.module",
            "com.qualcomm.location",
            "com.unionpay.tsmservice",
//            "com.hihonor.sceneservice",
            "com.android.backupconfirm",
//            "com.hihonor.hnofficelauncher",
            "com.android.mtp",
            "com.android.cellbroadcastservice",
//            "com.hihonor.email",
            "com.android.appsearch.apk",
            "com.android.internal.systemui.navbar.hide",
//            "com.hihonor.mediadatacenter",
//            "com.hihonor.android.internal.app",
//            "com.hihonor.magichome",
//            "com.hihonor.mediaprocessor",
//            "com.hihonor.youku.video",
            "com.android.systemui.overlay",
            "com.android.wallpapercropper",
            "com.android.systemui",
//            "com.hihonor.hnstartupguide",
//            "com.hihonor.keychain",
            "com.android.internal.systemui.navbar.gestural",
            "com.qualcomm.timeservice",
//            "com.hihonor.collect",
//            "com.hihonor.mediamaterial",
//            "com.hihonor.hncloud",
//            "com.hihonor.ouc",
//            "com.hihonor.magicvoice",
            "cn.honor.qinxuan",
            "com.android.wallpaperbackup",
            "com.android.internal.systemui.navbar.threebutton",
//            "com.honor.yoyocards",
//            "com.hihonor.wallpapereditor",
//            "com.hihonor.hnmusicplayer",
            "com.android.localtransport",
            "android",
            "com.android.rkpdapp",
            "com.android.permissioncontroller",
//            "com.hihonor.devicegroupmanage",
            "com.baidu.swan",
//            "com.hihonor.HnMultiScreenShot",
//            "com.hihonor.deskclock",
            "com.android.se",
//            "com.hihonor.numberidentity",
            "com.android.pacprocessor",
            "com.android.wifi.resources.overlay",
            "com.honor.yoyoappsug",
//            "com.hihonor.airlink",
            "com.android.providers.media.module",
//            "com.hihonor.controlcenter",
//            "com.hihonor.iconnect",
//            "com.hihonor.soundrecorder",
//            "com.hihonor.cloudmusic",
//            "com.hihonor.mcs.media.avfoundation",
            "com.android.settings",
//            "com.hihonor.medialibrary",
//            "com.android.frameworkreshnext.overlay",
//            "com.hihonor.iaware",
//            "com.hihonor.tips",
            "com.android.cameraextensions",
//            "com.hihonor.systemappsupdater",
//            "com.hihonor.popularapps",
//            "com.hihonor.trustagent",
//            "com.hihonor.baidu.browser",
//            "com.hihonor.trustcircle",
//            "com.hihonor.lens",
//            "com.android.federatedcompute.services",
//            "com.hihonor.screenrecorder",
//            "com.hihonor.android.FloatTasks",
//            "com.hihonor.remotepassword",
//            "org.codeaurora.ims",
//            "com.qualcomm.qcrilmsgtunnel",
//            "com.hihonor.devicemanager",
//            "com.hihonor.parentcontrol",
//            "com.hihonor.android.magicx.media.audioengine",
//            "com.hihonor.handoff",
            "com.android.devicelockcontroller",
            "com.android.documentsui",
            "com.android.adservices.api",
            "com.android.health.connect.backuprestore",
//            "com.hihonor.searchservice",
//            "com.hihonor.android.wfdft",
            "vendor.qti.hardware.cacert.server",
//            "com.hihonor.gamecenter",
            "com.android.networkstack.tethering.overlay",
            "com.android.providers.downloads.ui",
            "com.android.ons",
            "com.android.intentresolver",
//            "com.hihonor.contacts",
//            "com.hihonor.lbs",
            "com.android.certinstaller",
//            "com.hihonor.videoeditor",
//            "com.hihonor.synergy",
//            "com.hihonor.easygo",
            "android.ext.services",
            "com.android.wifi.resources",
            "com.android.wifi.dialog",
            "com.android.captiveportallogin",
            "com.android.providers.telephony",
//            "com.hihonor.android.projectmenu",
            "com.android.wallpaper.livepicker",
//            "com.hihonor.coauthservice",
            "com.android.sdksandbox",
//            "com.hihonor.awareness",
//            "com.hihonor.calendar",
            "vendor.qti.iwlan",
            "com.android.providers.settings",
            "com.android.phone",
//            "com.hihonor.gameassistant",
//            "com.hihonor.android.airsharing",
//            "com.hihonor.imedia.sws",
//            "com.hihonor.fileservice",
//            "com.hihonor.behaviorauth",
//            "com.hihonor.mmitest",
//            "com.hihonor.suggestion",
//            "com.hihonor.smartshot",
            "com.android.location.fused",
            "com.android.vpndialogs",
//            "com.hihonor.msdp",
//            "com.hihonor.android.launcher",
//            "com.hihonor.webview",
            "com.android.ondevicepersonalization.services",
            "com.android.htmlviewer",
//            "com.hihonor.featurelayer.sharedfeature.stylus",
//            "com.hihonor.hnbluetoothpencilmanager",
//            "com.hihonor.hisight",
//            "com.hihonor.systemmanager",
//            "com.hihonor.assetsync",
//            "com.hihonor.findmydevice",
//            "com.hihonor.deviceauth",
            "com.qti.qcc",
//            "com.hihonor.hnoffice",
//            "com.bjbyhd.screenreader_hihonor",
//            "com.hihonor.desktop.explorer",
//            "com.hihonor.bluetooth",
            "com.hihonor.printservice",
//            "com.hihonor.nearby",
            "com.android.providers.userdictionary",
            "com.android.providers.contactkeys",
            "com.android.cts.ctsshim",
            "com.android.bluetooth",
//            "com.hihonor.android.clone",
//            "com.hihonor.motionservice",
            "com.hihonor.cameraextension",
//            "com.hihonor.calculator",
//            "com.hihonor.id",
            "com.android.packageinstaller",
//            "com.hihonor.magazine",
            "com.hihonor.photos",
//            "com.hihonor.linktime",
//            "com.hihonor.game.kitserver",
//            "com.hihonor.crossdeviceserviceshare",
            "com.android.printspooler",
//            "com.hihonor.powergenie",
            "androidhnext",
//            "com.hihonor.stylus.floatmenu",
            "com.android.providers.partnerbookmarks",
//            "com.hihonor.visionengine",
//            "com.hihonor.dmsdp",
//            "com.hihonor.appmarket",
//            "com.hihonor.browserhomepage",
            "com.android.dynsystem",
//            "com.hihonor.camera",
//            "com.hihonor.printassistant",
            "com.android.microdroid.empty_payload",
//            "com.hihonor.pcassistant",
//            "com.hihonor.securityserver",
//            "com.hihonor.search",
            "com.android.hotspot2.osulogin",
//            "com.hihonor.collectcenter",
            "com.android.externalstorage",
            "vendor.qti.imsdatachannel",
            "com.android.server.telecom"
        )
    }

    fun getCustomWhitelistPackages(): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val customList = prefs.getStringSet(KEY_CUSTOM_WHITELIST, emptySet()) ?: emptySet()
        return customList.toSet()
    }

    fun getAllWhitelistPackages(): Set<String> {
        return getSystemWhitelistPackages() + getCustomWhitelistPackages()
    }

    fun addToWhitelist(packageName: String): Boolean {
        return try {
            val currentList = getCustomWhitelistPackages().toMutableSet()
            currentList.add(packageName)

            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putStringSet(KEY_CUSTOM_WHITELIST, currentList)
                .apply()

            Log.d(TAG, "已添加到白名单: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "添加白名单失败: $packageName", e)
            false
        }
    }

    fun removeFromWhitelist(packageName: String): Boolean {
        return try {
            val currentList = getCustomWhitelistPackages().toMutableSet()
            val removed = currentList.remove(packageName)

            if (removed) {
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putStringSet(KEY_CUSTOM_WHITELIST, currentList)
                    .apply()

                Log.d(TAG, "已从白名单移除: $packageName")
            }

            removed
        } catch (e: Exception) {
            Log.e(TAG, "移除白名单失败: $packageName", e)
            false
        }
    }

    fun isInWhitelist(packageName: String): Boolean {
        return getAllWhitelistPackages().contains(packageName)
    }

    fun isSystemApp(packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    fun getInstalledAppsInfo(): List<AppInfo> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps.mapNotNull { appInfo ->
            try {
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isWhitelisted = isInWhitelist(appInfo.packageName)

                AppInfo(
                    packageName = appInfo.packageName,
                    appName = appName,
                    isSystemApp = isSystem,
                    isWhitelisted = isWhitelisted,
                    icon = packageManager.getApplicationIcon(appInfo)
                )
            } catch (e: Exception) {
                Log.w(TAG, "获取应用信息失败: ${appInfo.packageName}")
                null
            }
        }.sortedWith(compareBy({ !it.isWhitelisted }, { !it.isSystemApp }, { it.appName }))
    }

    fun getWhitelistAppsForKiosk(): Array<String> {
        val whitelistPackages = getAllWhitelistPackages()
        val packageManager = context.packageManager

        return whitelistPackages.filter { packageName ->
            try {
                packageManager.getApplicationInfo(packageName, 0)
                true
            } catch (e: Exception) {
                Log.w(TAG, "白名单中的应用未安装: $packageName")
                false
            }
        }.toTypedArray()
    }

    fun clearCustomWhitelist(): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_CUSTOM_WHITELIST)
                .apply()

            Log.d(TAG, "已清空自定义白名单")
            true
        } catch (e: Exception) {
            Log.e(TAG, "清空白名单失败", e)
            false
        }
    }

    fun exportWhitelist(): String {
        val allPackages = getAllWhitelistPackages()
        return allPackages.joinToString("\n")
    }

    fun importWhitelist(whitelistText: String): Boolean {
        return try {
            val packages = whitelistText.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains(".") }
                .toSet()

            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putStringSet(KEY_CUSTOM_WHITELIST, packages)
                .apply()

            Log.d(TAG, "已导入白名单，共${packages.size}个应用")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导入白名单失败", e)
            false
        }
    }

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean,
        val isWhitelisted: Boolean,
        val icon: android.graphics.drawable.Drawable
    )

    fun getWhitelistStats(): String {
        val systemCount = getSystemWhitelistPackages().size
        val customCount = getCustomWhitelistPackages().size
        val totalCount = getAllWhitelistPackages().size

        return """
            白名单统计:
            - 系统白名单: $systemCount 个应用
            - 自定义白名单: $customCount 个应用
            - 总计: $totalCount 个应用
        """.trimIndent()
    }
}