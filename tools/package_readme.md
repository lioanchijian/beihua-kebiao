# 北化课表项目包 1.0.9.8

这是北化课表 App 源码、官网源码和匿名统计服务的完整项目包。

## 目录

- `android-app/`：Android 主工程源码
- `website/`：官网源码，已包含本次 APK 下载文件
- `server/usage-stats/`：匿名使用统计服务（Python + SQLite）
- `release/beihua-kebiao-1.0.9.8.apk`：当前发布 APK

## 构建 App

```powershell
cd android-app
# 先创建 local.properties，写入自己的 Android SDK 路径：
# sdk.dir=C:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
.\gradlew.bat assembleRelease
```

产物在：

```text
android-app\app\build\outputs\apk\release\app-release.apk
```

如果没有配置正式签名，构建脚本会使用 debug 签名，方便本地安装测试。正式分发请配置自己的 keystore。

## 部署网站

把 `website/` 里的所有内容上传到服务器网站根目录，例如：

```text
/var/www/beihua
```

网站依赖：

- `index.html`
- `privacy.html`
- `version.json`
- `assets/`
- `download/beihua-kebiao-latest.apk`

## 匿名统计

统计服务说明见：

```text
server/usage-stats/README.md
```

真实统计 token 不包含在项目包内；部署时自行生成一个随机长 token。

## 隐私

App 只会上报随机安装 ID、版本、平台和启动日期，不上报姓名、学号、账号、密码、Cookie、课程或成绩内容。用户可在设置中关闭匿名使用统计。

## 当前版本

- App：`1.0.9.8 (98)`
- 网站：`1.0.9.8`
- APK SHA256：`429504897d730ae16e2581b948ab76b4b6484872582d6e66b66b7bdefb4baea5`