# AppRemote Relay Server

Server trung gian giúp hai điện thoại Android kết nối qua **internet** (không cần cùng WiFi).

## Chạy local (test)

```bash
cd relay-server
npm install
npm start
```

Server lắng nghe cổng **8765**. Trong app Android, nhập URL relay:

- Máy tính cùng WiFi với điện thoại: `ws://192.168.x.x:8765`
- Emulator Android → máy host: `ws://10.0.2.2:8765`

## Deploy lên internet (VPS / Render / Railway)

### VPS (Ubuntu)

```bash
git clone <repo>
cd appremote/relay-server
npm install
npm install -g pm2
pm2 start server.js --name appremote-relay
pm2 save
```

Mở cổng **8765** trên firewall. URL trong app: `ws://YOUR_VPS_IP:8765`

### Render.com (miễn phí)

Hướng dẫn chi tiết từng bước: **[docs/DEPLOY-RENDER.md](../docs/DEPLOY-RENDER.md)**

Tóm tắt:
1. Push code lên GitHub
2. Render → **New Web Service** → root: `relay-server`
3. Build: `npm install` | Start: `npm start` | Health: `/health`
4. URL app: `wss://your-service.onrender.com`

### Railway / Fly.io

Tương tự: deploy thư mục `relay-server`, set `PORT` env nếu platform yêu cầu.

## Giao thức

| Message | Mô tả |
|---------|--------|
| `{type:"register", role:"host", room:"123456"}` | Máy bị điều khiển đăng ký phòng |
| `{type:"register", role:"client", room:"123456"}` | Máy điều khiển tham gia phòng |
| `{type:"paired"}` | Cả hai đã kết nối |
| `{type:"command", data:"..."}` | Lệnh từ client → host |
| `{type:"response", data:"OK"}` | Phản hồi từ host → client |

Mã phòng: **6 chữ số** (vd: `482913`).
