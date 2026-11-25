# BB Solver — Liquid Glass 1.1

Ứng dụng Android hỗ trợ nhận diện bàn Block Blast, chỉnh sửa vùng nhận diện và hiển thị gợi ý nước đi bằng lớp phủ nổi.

## Tạo APK bằng GitHub Actions

1. Tạo một repository GitHub mới.
2. Giải nén toàn bộ ZIP này rồi tải **các tệp và thư mục bên trong** lên repository (giữ nguyên thư mục `.github/workflows`).
3. Mở tab **Actions** và bật workflow nếu GitHub yêu cầu.
4. Đẩy commit lên bất kỳ nhánh nào hoặc chọn **Actions → Build Android APK → Run workflow**.
5. Khi workflow chạy thành công:
   - Vào **Actions → lần chạy mới nhất → Artifacts** để tải `BB-Solver-APK`.
   - Hoặc vào **Releases → BB Solver - Latest APK** để tải trực tiếp `BB-Solver-debug.apk`.

Workflow tự cài JDK 17, Android SDK 34 và Gradle 8.7; build APK debug, kiểm tra APK không rỗng, tạo SHA-256 và cập nhật Release `latest` sau mỗi lần push nhánh. Không cần tự tạo GitHub token: workflow dùng `GITHUB_TOKEN` có quyền `contents: write`.

## Build trên máy bằng Android Studio

1. Mở thư mục dự án bằng Android Studio.
2. Chờ Gradle Sync hoàn tất.
3. Chọn **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Thông tin build

- Android Gradle Plugin: 8.5.2
- Gradle: 8.7
- Kotlin: 1.9.24
- Compile SDK: 34
- Min SDK: 26
- Target SDK: 34
- JDK: 17

## Lưu ý

Đây là APK debug, không phải bản phát hành đã ký bằng khóa riêng. Android có thể yêu cầu xác nhận khi cài APK ngoài Play Store. Các quyền chụp màn hình và hiển thị lớp phủ cần được cấp trong phần Cài đặt Android.

Bản build cần được kiểm tra trên thiết bị thực tế và đúng phiên bản Block Blast đang chơi; kết quả nhận diện còn phụ thuộc hiệu chỉnh tọa độ và giao diện trò chơi.
