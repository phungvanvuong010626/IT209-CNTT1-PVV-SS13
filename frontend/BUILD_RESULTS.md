# Kết quả lint và build frontend

Môi trường: Node 26, npm, Vite 8.2.2, React 19. Thực hiện trong thư mục `frontend/`.

## Lint

```
$ npm run lint
> campus-hub-frontend@1.0.0 lint
> eslint .
```

Kết quả: không có lỗi, không có cảnh báo.

## Build

```
$ npm run build
> campus-hub-frontend@1.0.0 build
> vite build

vite v8.2.2 building client environment for production...
✓ 29 modules transformed.
dist/index.html                   0.49 kB │ gzip:  0.32 kB
dist/assets/index-DxsXgaip.css   12.43 kB │ gzip:  3.44 kB
dist/assets/index-BK-x5B99.js   219.01 kB │ gzip: 67.59 kB
✓ built in 742ms
```

Kết quả: build thành công.

## Kiểm thử nhanh với máy chủ giả

Backend thật chưa chạy nên giao diện được thử trên trình duyệt với một máy chủ giả lập theo hợp đồng API trong `docs/SPEC.md`. Đã xác nhận:

- Thẻ bài hiển thị nhãn "Đang kiểm duyệt", "Đã khóa" và "n báo cáo chờ" cho ADMIN.
- Nút "Thông báo (n)" hiển thị số chưa đọc, bấm một mục sẽ đánh dấu đã đọc, cập nhật số và mở bài liên quan.
- Trang Kiểm duyệt hiển thị 4 ô tổng quan, bảng bài chờ xử lý, panel chi tiết gồm báo cáo và lịch sử; Ẩn bài báo lỗi tại chỗ khi thiếu ghi chú, thành công thì hiện thông báo và tải lại.
- Hộp thoại báo cáo hiển thị lỗi API (409 "Bạn đã báo cáo bài đăng này").
- Bài bị khóa ẩn form bình luận, nút quan tâm và hiện dòng "Bài đăng đã bị khóa bởi quản trị viên".

Cần kiểm thử tích hợp lại với backend thật.
