# 开发环境

## 本机实际配置（已验证可编译）

| 组件 | 路径 / 版本 |
|---|---|
| JDK | `<你的 JDK 17 路径>` |
| Gradle | `<你的 Gradle 8.4 路径>` |
| Android SDK | `<你的 Android SDK 路径>` |
| compileSdk | 33（SDK 中已装 `android-33`） |
| build-tools | 33.0.1 / 34.0.0 |
| ADB | `<你的 platform-tools 路径>` |
| 真机 | 开发者真机，Android 16 / SDK 36 / arm64-v8a |

> 项目根目录自带 Gradle Wrapper（`gradlew` / `gradlew.bat`，Gradle 8.4），Windows 用 `gradlew.bat`，Linux/macOS 与 CI 用 `./gradlew`。

## 一次性配置

`local.properties`（已在 `.gitignore` 中，不入库）：

```properties
sdk.dir=<你的 Android SDK 路径>
```

注意用**正斜杠**。反斜杠转义会导致 `IOException: 文件名、目录名或卷标语法不正确`。

## 编译

```bash
cd "<项目目录>"

export JAVA_HOME="<你的 JDK 17 路径>"
export ANDROID_HOME="<你的 Android SDK 路径>"
export PATH="$JAVA_HOME/bin:$PATH"

"<你的 Gradle 8.4 路径>/bin/gradle" assembleRelease --console=plain
```

产物：`app/build/outputs/apk/release/app-release.apk`

## 签名

`app/build.gradle` 中的 `signingConfigs.release` 目前**硬编码**了 keystore 路径与密码，这对单人自用可以接受，但上传 GitHub 前必须改为从文件读取。

### 上传前的正确做法

1. 把 `signingConfigs` 改为读取 `keystore.properties`：

```gradle
def keystorePropsFile = rootProject.file("keystore.properties")
def keystoreProps = new Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(new FileInputStream(keystorePropsFile))
}

signingConfigs {
    release {
        if (keystorePropsFile.exists()) {
            storeFile file(keystoreProps['storeFile'])
            storePassword keystoreProps['storePassword']
            keyAlias keystoreProps['keyAlias']
            keyPassword keystoreProps['keyPassword']
        }
    }
}
```

2. 创建 `keystore.properties`（不入库）：

```properties
storeFile=release.keystore
storePassword=你的密码
keyAlias=ecutcampus
keyPassword=你的密码
```

3. `.gitignore` 已包含 `*.keystore` 与 `keystore.properties`，**确认 `git status` 里看不到它们**。

### CI 签名（GitHub Secrets）

CI 出 release 包，签名材料来自 4 个 GitHub Secrets（仓库 Settings → Secrets and variables → Actions 手工填写）：

| Secret 名 | 值 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | keystore 文件的 Base64（Windows：`certutil -encode release.keystore out.txt`，取正文段；或 `base64 -w0 release.keystore`） |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | key 别名 |
| `RELEASE_KEY_PASSWORD` | key 密码 |

CI 会把 Base64 解码为 `app/release.keystore`、生成 `keystore.properties` 再构建，构建结束立即删除这两个文件。密码只存在 Secrets 里，不进仓库和聊天。

> 密码只保存在本地 `keystore.properties`（不入库）。如果 keystore 曾在公开场合泄露过，重新生成一个新的再上传。

## 真机调试

```bash
export PATH="<你的 platform-tools 路径>:$PATH"

# 安装
adb install -r app/build/outputs/apk/release/app-release.apk

# 看日志
adb logcat -c
adb logcat -v time -s WebShell:V DomainRules:V DnsFilter:V AndroidRuntime:E

# 截图
adb exec-out screencap -p > shot.png
```

### 已知坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `INSTALL_FAILED_ABORTED: User rejected permissions` | vivo 熄屏时自动拒绝安装 | 先 `adb shell input keyevent KEYCODE_WAKEUP` 再装，或在手机上点「继续安装」 |
| `adb push` 路径变成 `C:/Users/.../sdcard/...` | MSYS 路径转换 | 命令前加 `export MSYS_NO_PATHCONV=1` |
| `JAVA_HOME is set to an invalid directory` | jdk17 目录下还有一层 `jdk-17.0.16+8` | `JAVA_HOME` 要指到最内层 |

## 抓取教务数据（同步功能开发用）

真机验证过的可用地址（学校随时可能改，改了就更新 `default-targets.json`）：

```
课表  /jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default
成绩  /jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default
选课  /jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default
```

- `gnmkdm` 是正方教务的菜单权限编号，**漏了会返回「系统升级维护中」**
- aTrust 代理节点格式：`172-20-130-13.atrust.ecut.edu.cn`（IP 横杠分隔）
- 不需要先加载 `index_initMenu.html`，直接加载目标页即可（真机验证）
