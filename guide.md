# Hướng Dẫn Thiết Kế Form Bằng EDN (EDN Form Guide)

Tài liệu này hướng dẫn chi tiết cách viết file cấu hình `.edn` để thiết kế các biểu mẫu (form) thông minh, từ cơ bản đến nâng cao. 

Dự án sử dụng **Clojure EDN** (Extensible Data Notation) kết hợp với một **EDN Logic Engine** nội bộ, cho phép bạn thiết kế các form có khả năng tính toán, phân nhánh logic phức tạp, chia giai đoạn (stages) và quản lý trạng thái ngầm (hidden variables) giống như một ứng dụng thực thụ.

---

## 1. Cách Chạy Form Bằng Command Line

Bạn có thể chạy file form thông qua CLI với các tham số hữu ích:

```bash
# Chạy cơ bản (mặc định dùng engine gum)
bb-form form.edn

# Sử dụng engine TUI Clojure gốc (dùng JLine3, không phụ thuộc gum)
bb-form form.edn --engine tui

# Chạy form, truyền file chứa giá trị mặc định có sẵn (giúp user không phải nhập lại từ đầu)
bb-form form.edn --values user_data.edn

# Chạy form, chỉ định nơi lưu file kết quả sau khi user điền xong
bb-form form.edn --out result.edn

# Ép định dạng xuất ra là JSON (mặc định là EDN)
bb-form form.edn --out result.json --format json

# Điền trước một số field ngay từ dòng lệnh (Vd: truyền user_id ẩn từ hệ thống ngoài vào)
bb-form form.edn user_id:12345 session_token:"abcxyz"
```

---

## 2. Cấu Trúc Tổng Thể Của Một File EDN Form

Một file form EDN thường bao gồm các "miền mặc định" (default domains/keys) sau:

```clojure
{:title "Tiêu đề biểu mẫu"
 :description "Mô tả ngắn gọn"
 
 ;; [TÙY CHỌN] Miền Biến Ẩn Global (Global Variables)
 :variables {:diem_tin_dung 100
             :trang_thai "Chờ duyệt"}

 ;; MIỀN CÂU HỎI (Fields) HOẶC CÁC GIAI ĐOẠN (Stages)
 ;; Bạn có thể dùng :fields trực tiếp nếu form đơn giản, 
 ;; hoặc dùng :stages nếu form dài và cần chia nhiều bước.
 :stages [ ... ] 
 ;; hoặc :fields [ ... ]
 }
```

---

## 3. Hệ Thống Các Giai Đoạn (Stages)

Đối với các form phức tạp, bạn có thể chia ra thành nhiều Giai đoạn (Stages). Form Engine sẽ chạy tuần tự từng Stage một. Mỗi Stage có thể chứa các hành động thực thi trước khi bắt đầu (`:on-begin`) và sau khi hoàn thành (`:on-complete`).

```clojure
:stages
[{:id :stage_1_ca_nhan
  ;; Hành động trước khi vào Stage
  :on-begin [[:print "▶ Bắt đầu Giai đoạn 1: Thông tin Cá nhân"]]
  
  ;; Danh sách phẳng các câu hỏi trong Stage này
  :fields
  [{:id :ho_ten
    :label "Họ và tên"
    :type :text
    :required true}]
    
  ;; Hành động sau khi hoàn thành Stage
  :on-complete [[:print "Hoàn tất Giai đoạn 1!"]]}
  
 {:id :stage_2_khao_sat
  :on-begin [[:print "▶ Bắt đầu Giai đoạn 2: Khảo sát"]]
  :fields [ ... ]}]
```

---

## 4. Biến Ẩn (Hidden Variables)

Biến ẩn là những dữ liệu không hiển thị trực tiếp cho người dùng nhập, nhưng được dùng để theo dõi tiến trình, điểm số, hoặc làm logic rẽ nhánh. Có 2 loại:

### A. Global Variables (Biến Toàn Cục)
Được khai báo ở đầu file (root) trong key `:variables`. Sống xuyên suốt tất cả các Stages.

```clojure
:variables {:tong_diem 0
            :muc_do_rui_ro "Thấp"}
```

### B. Local Hidden Fields (Biến Địa Phương Tính Toán Động)
Được khai báo ngay bên trong mảng `:fields` với `:type :hidden`. Giá trị của nó được tính toán tự động dựa trên các câu trả lời khác nhờ thuộc tính `:value` kết hợp toán tử.

```clojure
{:id :local_diem_kinh_nghiem
 :type :hidden
 ;; Tính điểm = số năm kinh nghiệm * 10
 :value [:* [:var :kinh_nghiem] 10]}
```

---

## 5. Logic Toán Tử & Tính Toán (EDN Interpreter)

Hệ thống cung cấp một trình thông dịch (Interpreter) để bạn viết các biểu thức tính toán và so sánh dạng danh sách LISP. 

**Cú pháp chung:** `[:toan_tu tham_so_1 tham_so_2 ...]`

Để lấy giá trị của một biến, dùng `[:var :ten_bien]`.

**Danh sách các toán tử hỗ trợ:**
- So sánh: `:=` (bằng), `:!=` (khác), `:>` (lớn hơn), `:<` (nhỏ hơn), `:>=`, `:<=`
- Logic: `:and` (VÀ), `:or` (HOẶC), `:not` (PHỦ ĐỊNH)
- Toán học: `:+` (Cộng), `:-` (Trừ), `:*` (Nhân), `:/` (Chia)
- Mảng/Tập hợp: `:contains?` (Kiểm tra chứa phần tử)
- Rẽ nhánh nhúng: `[:if dieu_kien gia_tri_dung gia_tri_sai]`

**Ví dụ tính toán động:**
```clojure
;; Nếu nghề nghiệp là văn phòng thì cộng 20 điểm, ngược lại giữ nguyên điểm cũ
[:if [:= [:var :nghe_nghiep] "Văn phòng"]
     [:+ [:var :tong_diem] 20]
     [:var :tong_diem]]
```

---

## 6. Logic Rẽ Nhánh - Ẩn/Hiện Động (`:show-if`)

Dự án dùng kiến trúc **Danh Sách Phẳng (Flat List)**. Thay vì lồng ghép câu hỏi này trong câu hỏi kia, bạn liệt kê tất cả các câu hỏi, và dùng `:show-if` để quyết định nó có hiện ra hay không.

```clojure
{:id :cau_hoi_phu_nu
 :label "Bạn đang mang thai phải không?"
 :type :select
 :options ["Có" "Không"]
 :required true
 ;; Chỉ hiển thị nếu: Giới tính = Nữ VÀ Tuổi >= 18
 :show-if [:and 
            [:= [:var :gioi_tinh] "Nữ"]
            [:>= [:var :tuoi] 18]]}
```

> **Mẹo (Backward Questions - Câu hỏi ngược):**  
> Nhờ hệ thống "Restarting Loop", bạn có thể đặt một câu hỏi ở ĐẦU danh sách `:fields`, nhưng cấu hình `:show-if` dựa trên một biến sẽ được tính ở CUỐI danh sách. Khi user trả lời câu cuối làm biến thay đổi, Form Engine sẽ quét lại từ đầu và "đột ngột" làm xuất hiện câu hỏi đó!

---

## 7. Hiệu Ứng Phụ & Chỉnh Sửa Trạng Thái (`:actions`)

Mỗi khi người dùng chọn một đáp án trong `:fields` (ví dụ `select` hoặc `radio`), bạn có thể đính kèm `:actions` để thực thi các hiệu ứng phụ, chủ yếu là thay đổi biến ẩn (`:set`).

**Cú pháp Action:**
```clojure
:actions [[:set :ten_bien <gia_tri_hoac_bieu_thuc>]
          [:print "Một câu thông báo ra màn hình"]]
```

**Ví dụ:**
```clojure
{:id :lich_su_no_xau
 :label "Bạn có từng bị nợ xấu không?"
 :type :select
 :options ["Không" "Có"]
 :required true
 :actions [;; Nếu chọn Có, trừ 50 điểm tín dụng
           [:set :diem_tin_dung 
                 [:if [:= [:var :lich_su_no_xau] "Có"] 
                      [:- [:var :diem_tin_dung] 50] 
                      [:var :diem_tin_dung]]]]}
```

---

## 8. Các Miền (Thuộc Tính) Mặc Định Của Một Field

Dưới đây là một bộ khung đầy đủ của một field thông thường:

```clojure
{:id       :id_bien_du_lieu      ; (Bắt buộc) Tên biến lưu vào kết quả
 :label    "Nội dung câu hỏi"    ; (Bắt buộc) Nội dung hiển thị
 :type     :text                 ; (Bắt buộc) Loại: text, number, date, datetime, time, select, multiselect, hidden
 :required true                  ; Có bắt buộc trả lời không
 :options  ["A" "B" "C"]         ; (Dùng cho select/multiselect)
 
 ;; Các miền nâng cao:
 :show-if  [:= [:var :x] 1]      ; Điều kiện hiển thị
 :actions  [[:set :y 2]]         ; Hiệu ứng thay đổi biến ẩn sau khi trả lời
 
 ;; Miền dành riêng cho :type :text
 :regex      "^[0-9]+$"          ; Validate định dạng chuỗi
 :regexError "Chỉ được nhập số"  ; Báo lỗi hiển thị nếu sai regex
 
 ;; Miền dành riêng cho :type :hidden
  :value    [:+ [:var :a] 1]      ; Biểu thức tự động tính toán giá trị
}
```

---

## 9. Import Thư Viện Hàm Bên Ngoài (`:import` & `[:call]`)

Để tránh làm file form quá phức tạp bởi các công thức toán học khổng lồ, hệ thống hỗ trợ import các thư viện bên ngoài.

**Cú pháp import:** Thêm thuộc tính `:import` ở đầu file form. Bạn có thể truyền tên file hoặc dùng mảng để tạo **alias (rút gọn namespace)**.
```clojure
:import ["../formulas/cardio_risk.edn"
         ["../formulas/advanced_math.clj" :as :math]]
```

Hệ thống hỗ trợ 2 loại file import:

### 9.1 File Clojure chuẩn (`.clj`)
Môi trường sẽ nạp toàn bộ file script Clojure (hỗ trợ đầy đủ namespace, `require` các thư viện bên ngoài). Đây là định dạng mạnh mẽ nhất để xây dựng Logic phức tạp.
```clojure
;; file: formulas/advanced_math.clj
(ns my.company.advanced.math)

(defn tinh_toan_phuc_tap [a b]
  (* a b 100))
```

### 9.2 File EDN Formula (`.edn`)
File EDN khai báo các hàm ẩn danh và hằng số tĩnh gọn nhẹ. Phù hợp cho những form nhỏ không cần tạo hẳn một file code Clojure.
```clojure
;; file: formulas/cardio_risk.edn
{:ns :cardio-risk
 :consts {:he_so 10}
 :fns {:tinh_toan (fn [a b] (* a b (:he_so consts)))}}
```

**Gọi hàm bằng toán tử `[:call]`:**
Sau khi import, bạn có thể gọi hàm ở bất kỳ đâu (`:show-if`, `:actions`, `:value`) thông qua namespace của hàm đó.
Nếu bạn đã dùng `:as :math` khi import, bạn có thể dùng thẳng `:math` thay vì `my.company.advanced.math`.
```clojure
:actions [
  ;; Gọi hàm từ file .clj qua alias :math
  [:set :diem_so [:call :math/tinh_toan_phuc_tap [:var :a] [:var :b]]]
  
  ;; Gọi hàm từ file .edn qua namespace trực tiếp
  [:set :diem_so_2 [:call :cardio-risk/tinh_toan [:var :a] [:var :b]]]
]
```

---

## 10. Hệ Thống Engine Hiển Thị (Rendering Engines)

Từ phiên bản `v2.1.0`, `bb-form` đã ảo hoá tầng kết xuất giao diện để hỗ trợ nhiều engine khác nhau. Bạn có thể chỉ định engine chạy bằng cờ `--engine <tên_engine>`:

### 10.1 Engine `gum` (Mặc định)
- **Cú pháp:** `bb-form form.edn --engine gum`
- **Đặc điểm:** Sử dụng công cụ dòng lệnh `gum` của hãng Charm. Thích hợp cho môi trường Unix tiêu chuẩn, mang lại giao diện terminal màu sắc và mượt mà.
- **Yêu cầu:** Cần cài đặt sẵn công cụ `gum` trên hệ thống.

### 10.2 Engine `tui` (Native Clojure TUI)
- **Cú pháp:** `bb-form form.edn --engine tui`
- **Đặc điểm:** Sử dụng thư viện `JLine3` được tích hợp sẵn trong Babashka. Giao diện TUI nhẹ hơn, tương tác nhanh, độc lập và không phụ thuộc vào bất kỳ công cụ dòng lệnh nào khác. Rất phù hợp cho các môi trường Docker, CI/CD hoặc máy tính tối giản.
- **Điều khiển:**
  - Với câu hỏi nhập văn bản/số/ngày: gõ đáp án bình thường và nhấn `Enter`.
  - Với câu hỏi chọn lựa (`:select`/`:radio`): dùng phím mũi tên `Lên` / `Xuống` để di chuyển và nhấn `Enter` để chọn.
  - Với câu hỏi chọn nhiều (`:multiselect`): dùng phím mũi tên `Lên` / `Xuống` để di chuyển, nhấn `Space` để tích chọn/bỏ chọn phần tử, và nhấn `Enter` để xác nhận toàn bộ.

### 10.3 Các Engine Placeholder (`winform` & `web`)
- Được thiết kế làm khuôn mẫu cho việc biên dịch form sang giao diện Windows Forms (`--engine winform`) hoặc chạy Web server cục bộ kết xuất form dạng HTML/CSS/JS (`--engine web`) trong các giai đoạn phát triển tương lai.

---

## 11. Biên dịch và Xuất sang Forms.md (Web Engine)

Bắt đầu từ phiên bản `v2.2.0`, `bb-form` hỗ trợ biên dịch các biểu mẫu EDN sang định dạng Markdown-like tương thích hoàn chỉnh với **Forms.md** (một web form engine nguồn mở cao cấp). File Markdown kết xuất ra sẽ được nhúng trong một file HTML tĩnh đi kèm, cho phép chạy trực tiếp trên trình duyệt web.

### 11.1 Cách Chạy Xuất sang Forms.md

Để xuất biểu mẫu sang formsmd, sử dụng cờ `--engine formsmd`:

```bash
# Biên dịch sang file markdown và html trong thư mục exports/
bb-form forms/job_application.edn --engine formsmd

# Biên dịch và khởi động máy chủ Web cục bộ ở cổng 8080 để xem trước ngay lập tức
bb-form forms/job_application.edn --engine formsmd --serve
```
Hệ thống sẽ tạo ra 2 file trong thư mục `exports/`:
- `<tên_form>.md`: File Markdown theo chuẩn cú pháp của Forms.md.
- `<tên_form>.html`: File HTML hoàn chỉnh có nhúng thư viện Forms.md và chứa toàn bộ logic hoạt động.

### 11.2 Các Kiểu Nhập Liệu Chuyên Biệt (`:form`)

Để đảm bảo các biểu mẫu EDN có thể tận dụng các thành phần giao diện web cao cấp của Forms.md (như chọn số sao đánh giá, bàn phím chuyên biệt cho Email/SĐT) mà **không làm phá vỡ tính tương thích ngược** của các engine terminal truyền thống (Gum, TUI), `bb-form` giới thiệu thuộc tính `:form`.

Khi viết form EDN, bạn khai báo `:type` theo các miền cơ bản (`:text`, `:number`), đồng thời khai báo thêm thuộc tính `:form` là một chuỗi mô tả định dạng (không phân biệt chữ hoa/thường):

```clojure
{:id :test_email
 :type :text
 :form "Email"
 :label "Địa chỉ Email của bạn"}
```

#### Bảng Ánh Xạ Giữa `:form` và Forms.md Constructors

| Giá trị `:form` | Type nền tảng | Constructor của Forms.md | Hành vi trên Web |
|:---|:---|:---|:---|
| `"Email"` | `:text` | `EmailInput` | Kiểm tra định dạng Email chuẩn, hiện bàn phím `@` trên mobile. |
| `"Tel"` / `"Telephone"` | `:text` | `TelInput` | Hiện bàn phím số điện thoại trên di động. |
| `"URL"` | `:text` | `URLInput` | Yêu cầu định dạng URL hợp lệ. |
| `"Password"` | `:text` | `PasswordInput` | Ẩn ký tự nhập (dạng dấu chấm `*`). |
| `"Rating"` | `:number` | `RatingInput` | Hiển thị giao diện chọn số sao (1-5 sao). |
| `"OpinionScale"` | `:number` | `OpinionScale` | Hiển thị thang điểm tuyến tính từ bé đến lớn. |
| `"Datetime"` | `:datetime` | `DatetimeInput` | Chọn ngày giờ đầy đủ. |
| `"Time"` | `:datetime` | `TimeInput` | Chọn giờ (HH:MM). |

*Các engine Gum và TUI sẽ bỏ qua thuộc tính `:form` và dùng `:type` cơ bản kết hợp `:regex` (nếu có) để hoạt động bình thường trên terminal.*

---

## 12. Chế Độ Hệ Chuyên Gia (Expert System Mode)

Bắt đầu từ phiên bản `v2.3.0`, `bb-form` tích hợp một **Hệ Chuyên Gia (Expert System)** hoàn chỉnh sử dụng thuật toán duyệt ngược (Backward-Chaining Solver) kết hợp với động cơ suy diễn tiến RETE (`net.sekao/odoyle-rules`).

### 12.1 Tại Sao Cần Chế Độ Hệ Chuyên Gia?
Ở chế độ chạy form thông thường (Restarting Loop / show-if), form engine hiển thị câu hỏi tuần tự từ trên xuống dưới theo danh sách phẳng hoặc phân bước.
Chế độ chuyên gia tự động kích hoạt khi phát hiện cấu hình `:format :expert` (hoặc `:format "expert"`) trong file EDN của bộ câu hỏi (không cần sử dụng cờ CLI `--expert`). Ở chế độ này, hệ thống không duyệt tuần tự nữa. Nó bắt đầu từ một danh sách các thuộc tính mục tiêu cần đạt được (khai báo trong `:goals`). Từ đó, hệ chuyên gia sẽ:
1. Tìm các luật có khả năng sinh ra các thuộc tính `:goals` này.
2. Xác định các biến đầu vào (primary variables - cần người dùng điền) hoặc biến trung gian (derived/intermediate variables) cần thiết để chạy luật đó.
3. Chỉ hỏi các câu hỏi thực sự liên quan để đi tới kết luận mục tiêu. Cơ chế này giúp tối giản hóa số lượng câu hỏi cần hỏi người dùng (ví dụ: trong sản khoa, nếu ngôi thai là ngôi ngang, hệ thống đưa ra kết luận mổ khẩn cấp ngay mà không cần hỏi các câu hỏi tiếp theo về khung chậu, CTC hay ối).

### 12.2 Cấu Hình File EDN Có Luật (Rules)

Để chạy biểu mẫu ở chế độ Hệ chuyên gia, file EDN form cần bổ sung khai báo định dạng `:format` và hai thuộc tính mới cấp cao nhất:

1. **`:format`**: Khai báo `:expert` để kích hoạt chế độ Hệ chuyên gia (mặc định nếu không khai báo hoặc chọn `:normal` thì hệ thống sẽ chạy ở chế độ câu hỏi rẽ nhánh thông thường).
2. **`:goals`**: Một vector chứa các keyword định danh các biến mục tiêu cần kết luận cuối cùng.
3. **`:rules`**: Một vector chứa danh sách các luật suy diễn.

#### Cấu Trúc Chi Tiết Của Một Rule:
```clojure
{:id       :tên-luật-duy-nhất ; (Keyword - Bắt buộc)
 :priority mức-ưu-tiên        ; (Number - Tùy chọn, mặc định 0) Độ ưu tiên giải quyết xung đột
 :require  [:biến1 :biến2]     ; (Vector - Tùy chọn) Khai báo thủ công các biến phụ thuộc
 :if       [:biểu-thức-logic] ; (Vector - Tùy chọn) Điều kiện kích hoạt luật (LHS)
 :then     {:biến-kết-quả [:biểu-thức-tính]}} ; (Map - Bắt buộc) Gán kết quả khi luật thỏa mãn (RHS)
```

- **`:if` (Left-Hand Side)**: Biểu thức logic EDN thông thường. Nếu `:if` bị bỏ qua, luật sẽ luôn được coi là thỏa mãn điều kiện khi tất cả các biến phụ thuộc được giải quyết.
- **`:then` (Right-Hand Side)**: Gán giá trị hoặc biểu thức tính toán động cho biến kết quả.
- **`:require`**: Khai báo danh sách các biến cần thiết mà không xuất hiện trực tiếp trong công thức của `:if` hoặc `:then` (ví dụ: các rule fallback cần chạy sau khi các biến đầu vào đã được nhập đầy đủ).
- **`:priority`**: Giá trị số nguyên. Khi nhiều luật ghi đè kết quả cho cùng một thuộc tính mục tiêu, luật nào có `:priority` cao hơn sẽ giành chiến thắng (đè giá trị).

### 12.3 Cơ Chế Tự Động Phân Tích Phụ Thuộc (Auto Dependency Detection)
Hệ chuyên gia sẽ tự động phân tích cú pháp biểu thức trong `:if` và `:then` để nhận diện tất cả các biến số được tham chiếu (nhận dạng qua từ khóa `:ten_bien` hoặc `[:var :ten_bien]`).
Bạn **không cần** viết các kiểm tra `:exists` rườm rà. Solver sẽ tự động bảo đảm tất cả các biến phụ thuộc này được điền đầy đủ thông tin (không ở trạng thái chưa trả lời `:not-answered`) trước khi cho phép luật đó kích hoạt.

### 12.4 Cú Pháp Gọi Hàm Rút Gọn (Shorthand Call)
Trong phần công thức `:then`, bạn có thể gọi trực tiếp hàm Clojure từ các namespace ngoài đã import (ví dụ: `[:triage/tinh-news2 :nhip_tho :nhip_tim]`) thay vì phải bọc qua toán tử `[:call :triage/tinh-news2 ...]`, giúp biểu thức cực kỳ ngắn gọn và trực quan.

### 12.5 Ví Dụ Biểu Mẫu Hệ Chuyên Gia Đơn Giản

```clojure
{:title "Hệ thống duyệt vay tín dụng chuyên gia"
 :goals [:ket_luan_vay]

 :import [["../formulas/loan_utils.clj" :as :loan]]

 :fields
 [{:id       :tuoi
   :label    "Tuổi người vay?"
   :type     :number
   :required true}
  {:id       :thu_nhap
   :label    "Thu nhập hàng tháng (triệu)?"
   :type     :number
   :required true}
  {:id       :no_xau
   :label    "Có lịch sử nợ xấu không?"
   :type     :select
   :options  ["Có" "Không"]
   :required true}]

 :rules
 [;; Luật 1: Nếu quá tuổi hoặc chưa đủ tuổi → Từ chối thẳng (Ưu tiên cao 50)
  {:id       :rule-tu-choi-tuoi
   :priority 50
   :if       [:or [:< :tuoi 18] [:> :tuoi 70]]
   :then     {:ket_luan_vay "❌ TỪ CHỐI - Độ tuổi không hợp lệ."}}

  ;; Luật 2: Nếu có nợ xấu → Từ chối thẳng (Ưu tiên cao 40)
  {:id       :rule-tu-choi-no-xau
   :priority 40
   :if       [:= :no_xau "Có"]
   :then     {:ket_luan_vay "❌ TỪ CHỐI - Có lịch sử nợ xấu."}}

  ;; Luật 3: Duyệt vay tự động qua hàm Clojure (Ưu tiên thấp 10)
  {:id       :rule-tinh-duyet-vay
   :priority 10
   :then     {:ket_luan_vay [:loan/tinh-duyet :tuoi :thu_nhap]}}]
}
```

Khi chạy biểu mẫu này với lệnh:
```bash
bb-form forms/expert_loan.edn
```
- Nếu người dùng nhập `tuoi: 75`, hệ thống lập tức kích hoạt `rule-tu-choi-tuoi` và trả ra kết luận từ chối, kết thúc chương trình mà **không bao giờ hỏi** câu hỏi về `thu_nhap` hay `no_xau`.

---

## Tổng Kết Luồng Hoạt Động Của Hệ Thống

1. **Nạp biến:** Engine nạp `:variables` Global và dữ liệu từ dòng lệnh (`--values`).
2. **Duyệt Stages:** Bắt đầu vào từng `:stage`. Chạy lệnh `:on-begin`.
3. **Restarting Loop:** Engine liên tục quét từ đầu đến cuối danh sách `:fields` hiện tại. Nó sẽ in ra câu hỏi đầu tiên mà `:show-if` thoả mãn và chưa được trả lời.
4. **Nhận đáp án & Thực thi:** Người dùng nhập. Hệ thống lưu kết quả, chạy biểu thức trong `:actions` (nếu có), tính toán lại `:type :hidden` (nếu có).
5. **Lặp lại:** Engine quay ngoắt lại đầu mảng `:fields` để tìm xem có câu hỏi nào vừa mới thỏa mãn điều kiện `:show-if` do sự biến đổi của bước 4 hay không.
6. **Chuyển Stage:** Khi không còn field nào hiển thị được nữa, chạy lệnh `:on-complete` của Stage hiện tại và chuyển sang Stage tiếp theo.
7. **Xuất file:** Hoàn tất, ghi lại file `--out` với cấu trúc JSON phẳng (chỉ bao gồm các giá trị user trả lời và local hidden vars, thường Global vars được lưu tách biệt hoặc có logic xuất riêng tùy thiết kế).
