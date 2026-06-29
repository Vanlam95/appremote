# AppRemote - Điều khiển Android từ xa

Ứng dụng Android điều khiển một điện thoại từ điện thoại khác. Mục đích chính: **mở trình duyệt mua hàng** (Shopee, Lazada, Tiki, Sendo) và thao tác cơ bản từ xa.

Hỗ trợ **hai chế độ kết nối**:
- **Qua Internet** — không cần cùng WiFi (dùng server relay)
- **WiFi LAN** — cùng mạng WiFi, kết nối trực tiếp qua IP

## Tính năng

- Mở trình duyệt mua hàng từ xa
- Thao tác: Trang chủ, Quay lại, Ứng dụng gần đây, Cuộn lên/xuống
- Ghép nối qua **mã phòng 6 số** (chế độ Internet)

## Yêu cầu

- Android 7.0+ (API 24)
- Quyền **Trợ năng** trên máy bị điều khiển
- **Server relay** (cho chế độ Internet) — xem `relay-server/`

## Bước 1: Deploy server relay (Internet)

**Hướng dẫn Render miễn phí (khuyến nghị):** [docs/DEPLOY-RENDER.md](docs/DEPLOY-RENDER.md)

Test local:

```bash
cd relay-server
npm install
npm start
```

URL ví dụ trong app:
- Render: `wss://appremote-relay-xxxx.onrender.com`
- Local WiFi: `ws://192.168.1.100:8765`

## Bước 2: Build APK Release (obfuscate)

**Không dùng debug APK cho production.** Xem [docs/SECURITY.md](docs/SECURITY.md).

### Release (obfuscate R8 — khuyến dùng)

Android Studio → **Build → Generate Signed App Bundle or APK** → APK → tạo keystore → **release**.

### Debug (test nhanh, không obfuscate)

```bash
gradlew assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Private

Repo nên để **Private**: GitHub → Settings → Danger Zone → Make private.  
Chi tiết: [docs/SECURITY.md](docs/SECURITY.md)

## Bước 3: Sử dụng qua Internet

### Máy bị điều khiển

1. AppRemote → **Máy bị điều khiển**
2. Chọn **Qua Internet**
3. Nhập URL relay + **Tạo mã** phòng (6 số)
4. Bật **Trợ năng** → **Bắt đầu lắng nghe**
5. Gửi **mã phòng** cho người điều khiển (Zalo, SMS, …)

### Máy điều khiển

1. AppRemote → **Máy điều khiển**
2. Chọn **Qua Internet**
3. Nhập **cùng URL relay**
4. Nhập **mã phòng riêng** cho từng máy (tối đa 5) trong danh sách
5. **Kết nối tất cả** → nhấn Shopee/Lazada/… (gửi tới mọi máy đang online)

## Chế độ WiFi LAN (tùy chọn)

1. Cả hai chọn **WiFi LAN**
2. Máy bị điều khiển: bắt đầu lắng nghe, ghi IP
3. Máy điều khiển: nhập IP → kết nối

## Cấu trúc project

```
appremote/
├── relay-server/          # Server trung gian (Node.js)
├── app/                   # Ứng dụng Android
│   └── network/
│       ├── RelayConnection.kt
│       └── RelayProtocol.kt
```

## Lưu ý

- Chỉ dùng trên thiết bị của bạn hoặc khi được phép
- Server relay cần chạy 24/7 nếu điều khiển từ xa thường xuyên
- Dùng `wss://` (HTTPS) khi deploy production để bảo mật hơn
