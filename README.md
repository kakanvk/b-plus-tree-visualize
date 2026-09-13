# Trình trực quan hóa cây B+

Ứng dụng desktop JavaFX phong cách production để học và thuyết trình các thao tác trên cây B+. Ứng dụng tách riêng cấu trúc dữ liệu đã kiểm chứng khỏi việc truy vết thao tác, dàn layout, render và phát lại, nhờ đó mỗi bước thuật toán đều có thể giải thích độc lập.

## Yêu cầu

- Java 21 (đặt `JAVA_HOME` trỏ tới bản cài JDK 21; `mvn -version` phải báo Java 21)
- Maven 3.9+

JavaFX 21 và AtlantaFX 2.1.0 được tải từ Maven Central. Không cần cài JavaFX SDK riêng hay cấu hình module-path thủ công. Giao diện dùng theme AtlantaFX Primer Light làm nền và CSS riêng của project cho bản sắc trực quan.

## Chạy

```bash
mvn clean javafx:run
```

## Build và kiểm thử

```bash
mvn clean test
mvn clean package
```

Trên Windows, `mvn clean package` còn gọi `jpackage` của Java 21 và tạo ứng dụng portable tự chứa tại:

```text
release/BPlusTreeVisualizer/BPlusTreeVisualizer.exe
```

Chạy trực tiếp file thực thi hoặc phân phối toàn bộ thư mục `BPlusTreeVisualizer`. Không copy riêng file `.exe` vì nó cần các thư mục `app` và `runtime` nằm cạnh bên. `mvn verify` sẽ tạo thêm gói phân phối tại:

```text
target/BPlusTreeVisualizer-1.0.0-windows.zip
```

Lưu ý: `jpackage` không ghi đè thư mục đầu ra đã tồn tại, vì vậy hãy xóa `release/` trước khi build lại (và tắt app vì Windows khóa file khi app đang chạy).

## Tính năng

- Chèn khóa số nguyên và từ chối khóa trùng
- Tìm kiếm với các bước so sánh và đường đi
- Xóa với tái phân phối/mượn, gộp nút, sửa khóa phân cách và co gốc
- Cân bằng median-first, không bao giờ làm tăng chiều cao hay số nút
- Tùy chỉnh bậc (order) cây B+ từ 3 đến 8
- Tự động dàn layout các cây con không chồng lấn
- Phân biệt nút trong, nút lá, cạnh nút con và cạnh liên kết nút lá
- Checkpoint thuật toán trực tiếp cho duyệt cây, chèn, tách, mượn, gộp và đổi gốc
- Snapshot cây bất biến theo từng bước, ID nút ổn định, highlight đúng cạnh/khóa
- Danh sách bước đánh số, bấm để nhảy tới bước, phát, tạm dừng, lùi, tới, chạy lại và chỉnh tốc độ
- Switch bỏ qua animation để nhảy thẳng tới khung hình cuối
- Thanh sticky dưới đáy hiển thị dãy khóa đã sắp xếp hiện tại
- Ô nhập chèn inline, kiểm tra số nguyên có dấu đầy đủ
- 12 bộ dữ liệu mẫu cho tách, gộp, số âm và khóa dài
- Chèn ngẫu nhiên theo số lượng, kèm tùy chọn khoảng số nâng cao
- Phản hồi kiểm tra inline, empty state, thống kê và đếm số phép so sánh
- Dialog thông tin nhóm kèm nguồn tham khảo thuật toán
- Kiểm tra toàn bộ bất biến ngay trong tầng thuật toán
- Mở app là full màn hình

## Cấu trúc project

```text
src/main/java/com/bplustree/visualizer/
├── model/          Nút cây B+, đột biến, bất biến và thống kê
├── service/        Thao tác và sinh event giáo dục
├── event/          Hợp đồng event animation độc lập UI
├── visualization/ Engine dàn layout, node/cạnh JavaFX, renderer, phát lại
├── controller/     Nhập liệu dialog, kiểm tra, điều phối UI
└── ui/             Điểm khởi chạy ứng dụng JavaFX
```

Stylesheet token hóa nằm riêng tại `src/main/resources/styles/app.css`. Test thuật toán nằm dưới `src/test/java` và cố ý không phụ thuộc JavaFX.

Các bước animation được ghi lại đồng bộ bên trong thuật toán cây B+, theo ranh giới checkpoint được minh họa bởi `ref/BPlusTree.js`. Mỗi event lưu snapshot cấu trúc bất biến, mục tiêu node/khóa/cạnh chính xác và số phép so sánh tích lũy. Nhờ đó phát lại nhảy thẳng tới snapshot mà không bao giờ phát lại hay đảo ngược đột biến trên cây chuẩn.

## Tổng quan cây B+

Cây B+ là cây tìm kiếm đa phân nhánh cân bằng, được thiết kế để giữ chiều cao nhỏ.

- **Nút trong** chứa khóa phân cách và con trỏ nút con. Trong cài đặt này, khóa phân cách bằng khóa nhỏ nhất của cây con bên phải.
- **Nút lá** chứa toàn bộ khóa đã lưu theo thứ tự tăng dần. Các lá nối với nhau hai chiều nên truy cập tuần tự rất hiệu quả.
- **Bậc (order)** là số nút con tối đa của một nút trong. Nút lá chứa tối đa `order - 1` khóa.
- **Tách (split)** xảy ra khi tràn. Nút đầy được chia đôi và một khóa phân cách được sao chép lên nút cha; tách có thể lan tới tận gốc mới.
- **Tái phân phối** mượn khóa hoặc nút con từ nút anh em khi xóa gây thiếu hụt mà anh em còn dư chỗ.
- **Gộp (merge)** kết hợp các anh em khi không bên nào cho mượn được. Việc này có thể lan lên trên và làm co gốc.

Tìm kiếm, chèn và xóa tốn thời gian duyệt cây `O(log n)`.

## Mẹo demo

1. Chọn **Tách nút · nhiều tầng**, rồi chèn thêm các khóa lân cận để thấy tách nút lá và nút trong.
2. Chọn **Gộp nút · xóa khóa 30**, rồi xóa `30` để minh họa sửa thiếu hụt.
3. Dùng **Tạm dừng** và các nút lùi/tới khi thuyết trình từng bước thuật toán.
4. Bật **Bỏ qua animation** để nhảy thẳng tới kết quả khi đã hiểu các bước.
5. Kéo mặt vẽ trực quan hoặc dùng thanh cuộn khi cây lớn vượt khung nhìn.
