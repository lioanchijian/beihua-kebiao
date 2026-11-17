# 北化课表工作区

这个目录是北化课表项目的唯一工作目录，App 源码、网站源码、统计服务、发布包和调试资料都集中在这里。

## 目录结构

- `sources/android-app/`：Android 主工程源码（Gradle 根目录）
- `website/`：官网静态源码，可直接部署到 Nginx
- `server/usage-stats/`：匿名使用统计服务（Python + SQLite）
- `server/usage-stats/private/`：统计 token、部署脚本等私有文件，不要打包外发
- `release/current/`：当前发布 APK
- `release/archive/`：历史 APK
- `packages/`：可直接发给同学的项目压缩包和网站部署包
- `assets/source-screenshots/`：官网截图的原图
- `docs/`：项目文档、更新日志、旧交接文档
- `tools/`：截图处理、部署辅助脚本
- `archive/`：旧版本、浏览器临时配置、调试截图和日志

## App 构建

```powershell
cd "D:\lwh\北化课表 - 副本\sources\android-app"
.\gradlew.bat assembleRelease
```

产物默认在：

```text
app\build\outputs\apk\release\app-release.apk
```

## 网站部署

网站根目录是 `website/`，包含：

- `index.html`
- `privacy.html`
- `version.json`
- `assets/`
- `download/beihua-kebiao-latest.apk`

把 `website/` 内容同步到服务器 `/var/www/beihua` 即可。

## 匿名统计

统计服务端口为 `127.0.0.1:8090`，Nginx 通过 `/api/` 和 `/stats` 反向代理。

注意：`server/usage-stats/private/` 内包含真实 token，外发项目包时不要带上该目录。

## 当前版本

- App：`1.0.9.10 (100)`
- 网站：`1.0.9.10`
- 统计接口：`https://buctkb.cn/api/stats/ping`