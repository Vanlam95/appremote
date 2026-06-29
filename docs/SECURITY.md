# GitHub Private + Release APK

## 1. Đặt repo GitHub thành Private

1. Mở https://github.com/Vanlam95/appremote  
2. **Settings** (tab trên cùng)  
3. Kéo xuống cuối → **Danger Zone**  
4. **Change repository visibility** → **Change visibility**  
5. Chọn **Make private** → gõ `Vanlam95/appremote` để xác nhận  

Chỉ bạn (và người được mời) mới xem được source code.

> **Render** vẫn deploy được từ repo private nếu đã kết nối GitHub và cấp quyền truy cập repo.

---

## 2. Build APK Release (đã obfuscate)

Bản **release** dùng R8: đổi tên class/hàm, xóa code thừa — khó decompile hơn bản debug.

### Android Studio (khuyến nghị)

1. Mở project `appremote`  
2. **Build → Generate Signed App Bundle or APK**  
3. Chọn **APK** → **Next**  
4. **Create new...** keystore (lưu file `.jks` và mật khẩu cẩn thận)  
5. Chọn **release** → **Finish**  

APK: `app/release/app-release.apk`

### Dòng lệnh (sau khi có Android SDK)

```bash
cd appremote
gradlew assembleRelease
```

APK unsigned: `app/build/outputs/apk/release/app-release-unsigned.apk`  
→ Nên ký bằng Android Studio (bước trên).

---

## 3. So sánh bảo mật

| | Debug APK | Release APK |
|---|-----------|-------------|
| Obfuscate (R8) | Không | **Có** |
| Thu gọn resource | Không | **Có** |
| Dùng cài production | Không nên | **Nên dùng** |

| | Public GitHub | Private GitHub |
|---|---------------|----------------|
| Ai đọc source | Mọi người | Chỉ bạn |

---

## 4. Lưu ý

- **Không commit** file keystore (`.jks`) hoặc mật khẩu lên GitHub  
- Obfuscate **không** = mã hóa tuyệt đối — vẫn có thể reverse nhưng khó hơn nhiều  
- Kết nối `wss://` trên Render vẫn mã hóa dữ liệu truyền đi
