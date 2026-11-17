#!/bin/bash
set -euo pipefail

if [ -z "${STATS_TOKEN:-}" ]; then
  echo "请先设置 STATS_TOKEN，例如："
  echo "  STATS_TOKEN='你的随机长 token' sudo -E bash enable_stats.sh"
  exit 1
fi

HERE="$(cd "$(dirname "$0")" && pwd)"
sudo mkdir -p /opt/beihua-stats
sudo cp "$HERE/server.py" /opt/beihua-stats/server.py
sudo cp "$HERE/patch_nginx.py" /tmp/patch_nginx.py
printf '%s' "$STATS_TOKEN" | sudo tee /opt/beihua-stats/token >/dev/null
sudo chmod 600 /opt/beihua-stats/token

sudo tee /etc/systemd/system/beihua-stats.service >/dev/null <<'EOF'
[Unit]
Description=Beihua Kebiao anonymous usage stats
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/beihua-stats
ExecStart=/usr/bin/python3 /opt/beihua-stats/server.py
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

sudo chown -R www-data:www-data /opt/beihua-stats
sudo chmod -R 750 /opt/beihua-stats
sudo systemctl daemon-reload
sudo systemctl enable beihua-stats
sudo systemctl restart beihua-stats
sleep 2
sudo python3 /tmp/patch_nginx.py
sudo nginx -t
sudo systemctl reload nginx
echo "STATS_OK"
curl -sS http://127.0.0.1:8090/health
echo