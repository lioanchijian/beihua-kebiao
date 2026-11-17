# 北化课表

北京化工大学的课表 App：课表、成绩、空闲教室、抢课，外加好友与课表互传。

- 官网：https://buctkb.cn
- 仓库：https://github.com/lioanchijian/beihua-kebiao
- 许可证：[GPL-3.0](LICENSE)

> **关于来源**：本项目是基于 [znjhahaha/zhengfang-apk](https://github.com/znjhahaha/zhengfang-apk)（教务助手，GPL-3.0）的
> **修改版**，不是原创项目。上游版权归原作者所有，我们做了哪些改动、如何遵守 GPL-3.0，
> 都写在 [CREDITS.md](CREDITS.md) 里，二次分发前请务必读一遍。

这个仓库是北化课表项目的唯一工作目录，App 源码、网站源码、统计服务、发布包和调试资料都集中在这里。

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
cd sources/android-app
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

- App：`1.0.9.13 (103)`
- 网站：`1.0.9.13`
- 统计接口：`https://buctkb.cn/api/stats/ping`

## 开源协议

本项目以 **GPL-3.0** 发布，完整源码即本仓库。再分发请保留 `LICENSE`、[CREDITS.md](CREDITS.md)
以及源码中的原始版权声明，并说明你的修改。