# Android 设备所有者 (Device Owner) 静默安装与自更新指南

## 一、 静默安装需要满足的条件是什么？

静默安装（用户的屏幕上不会弹出任何系统级“是否确认安装”的提示，安装包会自动在后台完成安装并可立即使用）在 Android 中属于极高权限的操作。

要实现静默安装，必须满足以下其中一种核心条件路线：

### 1. Device Owner（设备所有者）模式（适用于 Kiosk 设备）
这是企业级应用和 Kiosk 终端的唯一正规渠道。

**必须满足的前提条件：**
1. **应用拥有 Device Owner 权限：**
   必须通过 `adb shell dpm set-device-owner` 命令成功为你这款 App 设置了 `Device Owner` 权限。
   （*注意：普通的 Device Admin/设备管理器权限绝对不可以，必须是 Owner*）
2. **在清单文件（AndroidManifest.xml）中声明权限：**
   虽然不用弹窗用户确认，但在 Android 8.0 及以上，仍要在清单里写下对应的权限以允许本应用调起安装能力：
   ```xml
   <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
   ```
3. **使用 `PackageInstaller` API（基于流）：**
   不能使用常见的 `Intent.ACTION_VIEW`（此方式一定会弹出一个确认界面）。必须编写代码实例化 `PackageInstaller`，在后台创建一个 session 并把 APK 以字节流方式写入进去再提交。

### 2. 系统应用级别（System App）
如果应用被内置在操作系统里，它可以静默安装。普通第三方自己写的 APK 无法达到此级别。

**必须满足的前提条件：**
1. 获取系统级签名（System Signature），或者放置在设备的 `/system/priv-app/` 目录下。
2. 声明核心权限：`android.permission.INSTALL_PACKAGES`

### 3. Root 权限下的 Shell 命令执行
这属于硬核“破解”范畴，不适用正规的移动应用。
**必须满足的前提条件：**
1. Android 设备必须已经 Root。
2. 在代码中执行具有 Root 权限的 shell 安装命令：`Runtime.getRuntime().exec(arrayOf("su", "-c", "pm install -r apkFilePath"))`

**结论：针对 Kiosk 模式项目，采用且必须采用的就是路线一（Device Owner + PackageInstaller API）。**


## 二、 设备所有者能否静默更新自身？

**可以，但需要特殊处理，并且在更新瞬间应用会被强制终止。**

### 1. 技术上完全可行
作为 Device Owner，可以利用 `PackageInstaller` API（流式吸入新版本 APK 并 commit），将自己的包名作为目标应用提交静默更新。系统在接到指令后，发现你是 Device Owner 且目标包签名匹配，会立刻无脑地在后台开启更新，**不会弹出任何系统的“是否确认更新”弹窗**。

### 2. 更新过程中的“断崖式”重启（核心问题）
由于 Android 系统的机制，**任何应用在被更新的瞬间，其原有的进程都必须被系统彻底杀掉、清空。**
这就导致：
1. **当前界面闪退**：屏幕（如 Kiosk 主界面）会黑屏或闪回桌面。
2. **LockTask 模式（Kiosk）被短暂解除**：系统的 LockTask 会短暂脱离。
3. **无法接收更新结果回调**：`PackageInstaller.commit()` 时传入的 PendingIntent 根本不会被触发，因为应用已经被杀。

### 3. 如何解决自更新“闪退”并自动恢复 Kiosk？
必须配合系统的广播机制来让应用在更新后“复活”并重新接管设备。

**① 利用系统的应用更新广播（非常关键）**
在 `AndroidManifest.xml` 里注册监听系统的应用被替换（更新）广播：
```xml
<receiver
    android:name=".MyPackageUpdateReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>
```

**② 在复活的广播中重新启动主界面**
在广播接收器里，立刻再次启动 Kiosk 主 Activity：
```kotlin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MyPackageUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // 重新拉起主界面
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
            }
        }
    }
}
```

### 4. 终极自保措施：防止在更新期间被卸载
确保当前应用可以被更新，如果 Device Owner 设置了禁止卸载自己，连更新自己都会失败报冲突错：
```kotlin
KioskUtils.enableAppUpdate(this, null)
```

## 三、 推荐开源参考项目
1. **TestDPC (Google 官方维护)**: https://github.com/googlesamples/android-testdpc 
2. **Android-Kiosk-Mode**: https://github.com/suyashdev/Android-Kiosk-Mode
