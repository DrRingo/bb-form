# Hướng Dẫn Thiết Kế Form Bằng EDN (EDN Form Guide)

Tài liệu này hướng dẫn chi tiết cách viết file cấu hình `.edn` để thiết kế các biểu mẫu (form) thông minh, từ cơ bản đến nâng cao. 

Dự án sử dụng **Clojure EDN** (Extensible Data Notation) kết hợp với một **EDN Logic Engine** nội bộ, cho phép bạn thiết kế các form có khả năng tính toán, phân nhánh logic phức tạp, chia giai đoạn (stages) và quản lý trạng thái ngầm (hidden variables) giống như một ứng dụng thực thụ.

---

## 1. Cách Chạy Form Bằng Command Line

Bạn có thể chạy file form thông qua CLI với các tham số hữu ích:

```bash
# Chạy cơ bản
bb-form form.edn

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
 :type     :text                 ; (Bắt buộc) Loại: text, number, date, select, multiselect, hidden
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

## Tổng Kết Luồng Hoạt Động Của Hệ Thống

1. **Nạp biến:** Engine nạp `:variables` Global và dữ liệu từ dòng lệnh (`--values`).
2. **Duyệt Stages:** Bắt đầu vào từng `:stage`. Chạy lệnh `:on-begin`.
3. **Restarting Loop:** Engine liên tục quét từ đầu đến cuối danh sách `:fields` hiện tại. Nó sẽ in ra câu hỏi đầu tiên mà `:show-if` thoả mãn và chưa được trả lời.
4. **Nhận đáp án & Thực thi:** Người dùng nhập. Hệ thống lưu kết quả, chạy biểu thức trong `:actions` (nếu có), tính toán lại `:type :hidden` (nếu có).
5. **Lặp lại:** Engine quay ngoắt lại đầu mảng `:fields` để tìm xem có câu hỏi nào vừa mới thỏa mãn điều kiện `:show-if` do sự biến đổi của bước 4 hay không.
6. **Chuyển Stage:** Khi không còn field nào hiển thị được nữa, chạy lệnh `:on-complete` của Stage hiện tại và chuyển sang Stage tiếp theo.
7. **Xuất file:** Hoàn tất, ghi lại file `--out` với cấu trúc JSON phẳng (chỉ bao gồm các giá trị user trả lời và local hidden vars, thường Global vars được lưu tách biệt hoặc có logic xuất riêng tùy thiết kế).
