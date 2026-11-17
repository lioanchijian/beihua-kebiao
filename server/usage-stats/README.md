# 匿名使用统计服务

轻量 Python + SQLite 服务，监听 `127.0.0.1:8090`，由 Nginx 反向代理。

## 接口

- `POST /api/stats/ping`：App 每日启动上报
- `POST /api/stats/download`：网站下载点击
- `GET /stats?token=...`：私有统计页
- `GET /api/stats/summary?token=...`：JSON 汇总
- `GET /health`：健康检查

## 部署

```bash
STATS_TOKEN='换成随机长 token' sudo -E bash enable_stats.sh
```

脚本会创建 `/opt/beihua-stats`、systemd 服务，并给 Nginx 增加 `/api/` 与 `/stats` 反代。

## 隐私

只记录随机安装 ID、版本、平台、启动日期和下载点击；不记录姓名、学号、账号、密码、Cookie、课程或成绩内容。

真实 token 放在 `private/`，该目录不要放入外发压缩包。