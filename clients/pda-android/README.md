# CSSD PDA Android客户端

这是 PDA 手持终端 Android APP 源码。它独立于 Web 后台和 Windows 触摸台，以 APK 形式安装到 Android PDA。

## 功能定位

- PDA 下收：无网络时缓存扫码回收记录
- PDA 下送：后续接入下送任务下载与科室确认
- 回到医院内网 WiFi 后，调用后台 `/api/pda/sync` 上传缓存

## 后台地址

当前在 `MainActivity.java` 中默认：

```java
private static final String SERVER_BASE = "http://10.0.2.2:8080/cssd-trace/api";
```

真机 PDA 使用时需要改为服务器内网 IP，例如：

```java
private static final String SERVER_BASE = "http://192.168.1.10:8080/cssd-trace/api";
```

## 构建要求

当前电脑没有 Android SDK/Gradle，所以这里先提交 Android 项目源码。安装 Android Studio 或 Android SDK 后可执行：

```powershell
gradle assembleDebug
```

产物位置通常为：

```text
app/build/outputs/apk/debug/app-debug.apk
```

