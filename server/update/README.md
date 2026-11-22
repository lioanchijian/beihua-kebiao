# 更新清单双通道部署

站点根：`/var/www/beihua`。

## 文件对应

| 本地文件 | 服务器路径 | 作用 |
|---|---|---|
| `website/version.json` | `/var/www/beihua/version.json` | 老客户端（<=1.0.9.18）入口，固定 1.0.9.19 桥接包 |
| `website/version-latest.json` | `/var/www/beihua/version-latest.json` | 1.0.9.19+ 入口，指向 1.0.9.20 |
| `website/download/beihua-kebiao-1.0.9.19.apk` | `/var/www/beihua/download/beihua-kebiao-1.0.9.19.apk` | 桥接包 |
| `website/download/beihua-kebiao-1.0.9.20.apk` | `/var/www/beihua/download/beihua-kebiao-1.0.9.20.apk` | 正式签名包 |
| `website/download/beihua-kebiao-latest.apk` | `/var/www/beihua/download/beihua-kebiao-latest.apk` | 官网主下载按钮，1.0.9.20 |

## 上传顺序

1. 先传 `beihua-kebiao-1.0.9.19.apk`，再传 `version.json`。
2. 确认老用户检查更新能看到 1.0.9.19 和桥接说明。
3. 再传 `beihua-kebiao-1.0.9.20.apk`，覆盖 `beihua-kebiao-latest.apk`。
4. 最后传 `version-latest.json`。
5. **不要覆盖 `version.json`**：它必须一直留在 1.0.9.19。

## Nginx 配置

把 `server/update/nginx-version-cache.conf` 放进站点 `server{}` 块，或在该块内 `include`：

```nginx
include /etc/nginx/snippets/beihua-version-cache.conf;
```

它只给 `version.json`、`version-latest.json`、`latest.apk` 加 `expires -1`，不覆盖站点级 HSTS / nosniff 等 `add_header`。

## 验证

```bash
curl -sS https://buctkb.cn/version.json | python3 -m json.tool
curl -sS https://buctkb.cn/version-latest.json | python3 -m json.tool
curl -sSI https://buctkb.cn/version.json | grep -i cache-control
curl -sSI https://buctkb.cn/version-latest.json | grep -i cache-control
curl -sSI https://buctkb.cn/download/beihua-kebiao-1.0.9.19.apk
curl -sSI https://buctkb.cn/download/beihua-kebiao-latest.apk
sha256sum /var/www/beihua/download/beihua-kebiao-1.0.9.19.apk
sha256sum /var/www/beihua/download/beihua-kebiao-1.0.9.20.apk
```

期望：

- `version.json`：`versionCode=109`，sha256 `73784221…4a571d`
- `version-latest.json`：`versionCode=110`，sha256 `2720e919…bb6933`
- 两个清单响应头为 `Cache-Control: no-cache`（`expires -1` 的实际输出）。
- 两个 APK 的 sha256 与清单一致。

## 注意

- 不主动上传；只有明确说「上传」才部署。
- 不把 `version.json` 改成最新版，否则 1.0.9.18 的旧更新器会拒绝新签名包。
- `version-latest.json` 只在 1.0.9.19 桥接版铺开后才有意义；提前上传不会伤害老客户端，但会让已升级到 1.0.9.19 的用户直接看到 1.0.9.20。
