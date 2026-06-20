# Deploy AppRemote Relay lên Render (miễn phí)

Hướng dẫn chi tiết để chạy server trung gian trên [Render.com](https://render.com), giúp hai điện thoại Android kết nối qua **internet**.

---

## Tổng quan

Sau khi deploy, bạn sẽ có URL dạng:

```
wss://appremote-relay-xxxx.onrender.com
```

Nhập URL này vào app Android (cả hai máy), kèm **cùng mã phòng 6 số**.

---

## Chuẩn bị

1. Tài khoản **GitHub** (miễn phí): https://github.com/signup  
2. Tài khoản **Render** (miễn phí): https://dashboard.render.com/register  

3. Đưa project lên GitHub (nếu chưa có):

```bash
cd c:\Users\User\Desktop\appremote
git init
git add .
git commit -m "AppRemote with relay server"
```

Tạo repo mới trên GitHub (vd: `appremote`), rồi:

```bash
git remote add origin https://github.com/TEN-GITHUB/appremote.git
git branch -M main
git push -u origin main
```

> Thay `TEN-GITHUB` bằng username GitHub của bạn.

---

## Cách 1: Deploy bằng Blueprint (nhanh nhất)

1. Vào https://dashboard.render.com  
2. Nhấn **New +** → **Blueprint**  
3. Chọn repo GitHub `appremote`  
4. Render đọc file `render.yaml` ở root project  
5. Nhấn **Apply** → đợi deploy xong (~2–5 phút)  
6. Vào service **appremote-relay** → copy **URL** (vd: `https://appremote-relay-xxxx.onrender.com`)

**URL dùng trong app Android:**

```
wss://appremote-relay-xxxx.onrender.com
```

(Dùng `wss://` thay vì `https://` — không thêm cổng, không thêm `/health`)

---

## Cách 2: Deploy thủ công (từng bước)

### Bước 1: Tạo Web Service

1. https://dashboard.render.com → **New +** → **Web Service**  
2. **Connect** repo GitHub `appremote`  
3. Cấu hình:

| Mục | Giá trị |
|-----|---------|
| **Name** | `appremote-relay` |
| **Region** | Singapore (gần VN nhất) |
| **Branch** | `main` |
| **Root Directory** | `relay-server` |
| **Runtime** | Node |
| **Build Command** | `npm install` |
| **Start Command** | `npm start` |
| **Instance Type** | Free |

### Bước 2: Health check

Trong **Settings** → **Health Check Path**:

```
/health
```

### Bước 3: Deploy

Nhấn **Create Web Service** → đợi trạng thái **Live** (màu xanh).

### Bước 4: Kiểm tra

Mở trình duyệt:

```
https://appremote-relay-xxxx.onrender.com/health
```

Kết quả đúng:

```json
{"status":"ok","service":"appremote-relay","rooms":0}
```

---

## Cấu hình app Android

### Máy bị điều khiển

1. AppRemote → **Máy bị điều khiển**  
2. Chọn **Qua Internet**  
3. **URL relay:** `wss://appremote-relay-xxxx.onrender.com`  
4. Nhấn **Tạo mã** (vd: `482913`)  
5. Bật **Trợ năng** → **Bắt đầu lắng nghe**  
6. Gửi mã phòng cho máy điều khiển (Zalo, SMS…)

### Máy điều khiển

1. AppRemote → **Máy điều khiển**  
2. Chọn **Qua Internet**  
3. **Cùng URL relay** + **cùng mã phòng**  
4. **Kết nối** → thử mở **Shopee**

---

## Lưu ý gói Free Render

| Vấn đề | Giải pháp |
|--------|-----------|
| **Ngủ sau 15 phút không dùng** | Lần kết nối đầu mất ~30–60 giây để server thức dậy. Thử **Kết nối** lại sau 1 phút. |
| **Giới hạn băng thông** | Đủ cho lệnh mở trình duyệt / thao tác cơ bản. |
| **WebSocket** | Render hỗ trợ `wss://` trên cùng domain — không cần cấu hình thêm. |

**Mẹo giữ server thức (tùy chọn):** Dùng [UptimeRobot](https://uptimerobot.com) ping `https://your-url.onrender.com/health` mỗi 5 phút (miễn phí).

---

## Xử lý lỗi thường gặp

### "Không thể kết nối" trên app

- Kiểm tra URL: phải là `wss://...` (không phải `https://`)  
- Không thêm `/health` hay cổng `:8765`  
- Đợi server Render wake up (free tier) rồi thử lại  
- Hai máy phải dùng **đúng cùng mã phòng 6 số**

### Deploy fail trên Render

- **Root Directory** phải là `relay-server`  
- **Start Command:** `npm start`  
- Xem **Logs** tab trên Render để đọc lỗi cụ thể

### "Room already has a host"

- Máy bị điều khiển đã có phiên cũ — **Dừng dịch vụ** rồi **Bắt đầu lắng nghe** lại  
- Hoặc tạo **mã phòng mới**

### Trợ năng chưa bật

- Máy bị điều khiển: Cài đặt → Trợ năng → bật **AppRemote**

---

## Cập nhật server sau này

Sau khi sửa code trong `relay-server/`:

```bash
git add .
git commit -m "Update relay server"
git push
```

Render tự deploy lại (Auto-Deploy mặc định bật).

---

## So sánh URL

| Môi trường | URL trong app |
|------------|----------------|
| Máy tính local (test WiFi) | `ws://192.168.1.100:8765` |
| Render production | `wss://appremote-relay-xxxx.onrender.com` |
| VPS riêng | `ws://IP-VPS:8765` hoặc `wss://domain.com` |

---

## Bảo mật (khuyến nghị)

- Mã phòng 6 số là lớp bảo vệ cơ bản — **đổi mã mỗi lần** sử dụng  
- Không chia sẻ URL relay công khai nếu không cần  
- Chỉ điều khiển thiết bị của bạn hoặc khi được phép
