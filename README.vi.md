# bb-form

**English guide: [README.md](./README.md)**

Công cụ CLI dùng Babashka + Charm Gum để thu thập dữ liệu từ các form đẹp mắt trên terminal, được tích hợp **EDN Logic Engine** toàn diện hỗ trợ import công thức, nhãn động và suy luận Bayesian.

---

## ✨ Tính Năng

| Tính năng | Mô tả |
|---|---|
| 🧠 **EDN Logic Engine** | Flat-list fields với bộ thông dịch `eval-expr` (`and/or/not/=/>/<`) |
| 🔀 **`:show-if` động** | Hiện/ẩn câu hỏi theo trạng thái runtime |
| 🗄️ **Biến ẩn** | Khai báo `:variables` để theo dõi trạng thái nền |
| ⚡ **Hiệu ứng phụ** | `:actions` để thay đổi state sau mỗi câu trả lời |
| 📦 **Import công thức** | Import thư viện `.edn` hoặc `.clj` với alias namespace |
| 🔣 **Toán tử `[:call]`** | Gọi bất kỳ hàm nào từ thư viện đã import |
| 🔍 **Toán tử `[:get]`** | Trích xuất thuộc tính từ map kết quả phức tạp |
| 🎲 **Hỗ trợ xác suất** | Tích hợp mô hình Bayesian Stan qua Clojure bridge |
| 🏷️ **Nhãn động** | Nội suy giá trị tính toán vào nhãn với `{{expr}}` |
| 📋 **Field `:info`** | Hiển thị kết quả chỉ đọc, lưu vào output |
| 📁 **Multi-Stage Forms** | Chia form phức tạp thành `:stages` với hooks |

---

## 📦 Cài Đặt

### Cách 1 — bbin (khuyến nghị, tất cả nền tảng)

```bash
# Yêu cầu: babashka + bbin
bbin install io.github.drringo/bb-form
```

### Cách 2 — Scoop (Windows)

```powershell
scoop bucket add drringo https://github.com/drringo/bb-form
scoop install bb-form
```

### Cách 3 — Homebrew (macOS / Linux)

```bash
brew tap drringo/bb-form https://github.com/drringo/bb-form
brew install bb-form
```

### Cách 4 — Thủ công (mọi nền tảng)

```bash
git clone https://github.com/drringo/bb-form
cd bb-form
bb src/com/drbinhthanh/bb_form.clj <form.edn>
```

### Điều kiện tiên quyết

| Công cụ | Mục đích | Cài đặt |
|---|---|---|
| [Babashka](https://babashka.org/) | Clojure runtime | `scoop install babashka` / `brew install borkdude/brew/babashka` |
| [Charm Gum](https://github.com/charmbracelet/gum) | Giao diện Terminal | `winget install charmbracelet.gum` / `brew install charmbracelet/tap/gum` |

---

## 🚀 Cách Sử Dụng

```bash
bb-form <form.edn> [OPTIONS]
```

| Option | Mô tả |
|---|---|
| `--values <file.edn>` | Điền sẵn giá trị từ file EDN (chạy tự động / batch mode) |
| `--out <file.edn>` | Đường dẫn kết quả (mặc định: `result.edn`) |
| `--engine <gum|tui|formsmd|winform|web>` | Chọn engine hiển thị giao diện: `gum` (yêu cầu cài đặt Gum, mặc định), `tui` (TUI Clojure gốc qua JLine3), `formsmd` (biên dịch sang Markdown/HTML web của Forms.md), `winform` (placeholder), hoặc `web` (placeholder) |

**Ví dụ:**

```bash
# Chạy form tương tác thông thường (dùng engine Gum mặc định)
bb-form forms/job_application.edn

# Sử dụng engine JLine3 TUI Clojure gốc
bb-form forms/job_application.edn --engine tui

# Chạy batch tự động
bb-form forms/job_application.edn --values forms/values.edn --out result.edn

# Ví dụ tuyển dụng Bayesian với engine TUI gốc
bb-form forms/bayesian_recruitment.edn --engine tui
```

---

## 📝 Cấu Trúc Form

Form là một EDN map. Ví dụ tối giản:

```clojure
{:title       "Form của tôi"
 :description "Vui lòng điền thông tin bên dưới."

 ;; Biến trạng thái ẩn (không hiển thị cho người dùng)
 :variables {:diem 0}

 :fields
 [{:id       :ten
   :label    "Họ tên của bạn?"
   :type     :text
   :required true}

  {:id      :tuoi
   :label   "Tuổi của bạn?"
   :type    :number
   :show-if [:>= [:var :diem] 0]}

  {:id     :ket_qua
   :type   :info
   :label  "Điểm của bạn là {{[:var :diem]}} điểm."}]}
```

### Các loại field hỗ trợ

| Loại | Mô tả |
|---|---|
| `:text` | Nhập văn bản, hỗ trợ `:regex` validation |
| `:number` | Nhập số nguyên |
| `:date` | Nhập ngày (DD-MM-YYYY) với gõ tắt (`DD`, `DDMM`, `DDMMYYYY`, `DD[+-]N`, `DDMM[+-]N`) |
| `:datetime` | Nhập ngày giờ (DD-MM-YYYY HH:MM) với gõ tắt (hỗ trợ cả gõ tắt riêng giờ) |
| `:select` | Chọn một lựa chọn (dropdown) |
| `:multiselect` | Chọn nhiều lựa chọn |
| `:hidden` | Giá trị tính toán, không hiển thị |
| `:info` | Nhãn chỉ đọc được tính toán, lưu vào kết quả |

### Toán tử EDN Logic (`eval-expr`)

```clojure
[:var :ten_field]              ; lấy giá trị biến
[:= expr expr]                 ; so sánh bằng
[:!= expr expr]                ; so sánh khác
[:> :< :>= :<=]                ; so sánh số
[:and e1 e2 ...]               ; AND logic
[:or  e1 e2 ...]               ; OR logic
[:not e]                       ; NOT logic
[:+ :- :* :/]                  ; toán học
[:if dieu_kien then else]      ; rẽ nhánh
[:get map-expr :key]           ; lấy thuộc tính map
[:contains? [:var :list] val]  ; kiểm tra phần tử
[:call :alias/ham arg ...]     ; gọi hàm từ thư viện đã import
[:str/includes? s sub]         ; kiểm tra chuỗi con
```

---

## 📦 Import Thư Viện Công Thức

Import file `.edn` hoặc script Clojure `.clj` đầy đủ:

```clojure
:import ["../formulas/cardio_risk.edn"
         ["../formulas/candidate_score.clj" :as :score]]
```

Dùng `:as :alias` để rút gọn namespace. Sau đó gọi hàm:

```clojure
:actions [[:set :ket_qua [:call :score/analyze-candidate [:var :x] [:var :y]]]]
```

### Cấu trúc file `.edn` công thức

```clojure
{:ns :cardio-risk
 :consts {:he_so 10}
 :fns {:tinh_nguy_co (fn [tuoi bmi] (* tuoi bmi (:he_so consts)))}}
```

### Cấu trúc file `.clj` Clojure

```clojure
(ns my.company.math)

(defn tinh_diem [a b]
  (* a b 100))
```

---

## 🎲 Tích Hợp Bayesian / Stan

Kết nối mô hình Stan qua một script `.clj` đóng vai bridge:

```clojure
;; formulas/bayesian_hiring.clj
(ns formulas.bayesian-hiring)
(defn run-stan-model [kinh_nghiem diem_test]
  ;; gọi cmdstan qua shell, trả về map phân phối xác suất
  {:prob 78 :variance 0.02 :lower_bound 50 :upper_bound 100 :confidence "95%"})
```

Import và gọi trong form:

```clojure
:import [["../formulas/bayesian_hiring.clj" :as :stan]]
:actions [[:set :ket_qua [:call :stan/run-stan-model [:var :kinh_nghiem] [:var :diem]]]]
:label   "Xác suất tuyển dụng: {{[:get [:var :ket_qua] :prob]}}%
          (Độ tin cậy {{[:get [:var :ket_qua] :confidence]}}: 
           từ {{[:get [:var :ket_qua] :lower_bound]}}% đến {{[:get [:var :ket_qua] :upper_bound]}}%)"
```

---

## 📂 Cấu Trúc Dự Án

```
bb-form/
├── src/com/drbinhthanh/bb_form.clj   # Core engine
├── forms/                             # Các form mẫu
│   ├── job_application.edn            # Ví dụ tính điểm ma trận
│   ├── bayesian_recruitment.edn       # Ví dụ tuyển dụng Bayesian
│   └── health_check.edn
├── formulas/                          # Thư viện công thức
│   ├── candidate_score.clj
│   ├── bayesian_hiring.clj
│   ├── cardio_risk.edn
│   └── stan_models/hiring_model.stan
├── bucket/bb-form.json                # Scoop manifest
├── Formula/bb-form.rb                 # Homebrew formula
└── guide.md                           # Hướng dẫn đầy đủ tiếng Việt
```

---

## 🗓️ Lịch Sử Phiên Bản

### v2.2.0
- ✅ **Hỗ trợ Web Engine Forms.md (`--engine formsmd`)**:
  - Hỗ trợ xuất biểu mẫu EDN sang file Markdown-like và file HTML tĩnh tương thích hoàn toàn với web engine Forms.md.
  - Hỗ trợ xem trước giao diện web thông qua cờ `--serve` (khởi chạy máy chủ httpkit nội bộ tại cổng 8080).
  - Tích hợp rẽ nhánh cấp độ slide (Stage-level skip logic) sử dụng cú pháp jump condition chuẩn (`-> conditionExpr`).
  - Hỗ trợ thuộc tính `:form` (`"Email"`, `"Tel"`, `"URL"`, `"Rating"`, v.v.) giúp khai báo các miền nhập liệu chuyên biệt của Forms.md trên web trong khi vẫn bảo toàn khả năng tương thích ngược trên terminal cho các engine Gum và TUI.

### v2.1.0
- ✅ **Giai đoạn 7**: Ảo hóa giao diện hiển thị & Hỗ trợ TUI JLine3 gốc
  - Tách rời các logic hiển thị terminal thành bộ điều phối tập trung `com.drbinhthanh.bb-form.core`.
  - Thêm engine TUI thuần Clojure qua JLine3 (`--engine tui`) hỗ trợ chọn menu bằng phím mũi tên và nhập liệu tương tác không cần cài đặt Gum bên ngoài.
  - Giữ nguyên khả năng tương thích ngược với engine Charm Gum mặc định (`--engine gum`).
  - Hỗ trợ các engine placeholder/nháp cho Windows Forms (`winform`) và ứng dụng Web/HTML (`web`).
  - Sửa lỗi tự động ép kiểu dữ liệu từ tham số dòng lệnh CLI cho các câu hỏi bị bỏ qua (skipped) hoặc điền sẵn (prefilled).

### v2.0.0
- ✅ **Giai đoạn 5**: `:import` thư viện công thức (`.edn` & `.clj`) với alias `:as`
- ✅ **Giai đoạn 6**: Hỗ trợ biến xác suất qua tích hợp Bayesian Stan bridge
- ✅ Toán tử `[:call]` với phân giải alias (ưu tiên: Clojure ns → EDN registry)
- ✅ Toán tử `[:get]` để trích xuất thuộc tính map
- ✅ Field type `:info` — hiển thị chỉ đọc, lưu vào kết quả
- ✅ Nội suy nhãn động `{{expr}}`
- ✅ Sửa lỗi: actions chạy đúng trong chế độ batch `--values`

### v1.0.0 (Giai đoạn 1–4)
- ✅ Chuyển từ `.json` sang `.edn`
- ✅ Flat-list fields + biểu thức `:show-if` EDN
- ✅ `eval-expr` Logic Engine + thuật toán Restarting Loop
- ✅ `:variables` trạng thái ẩn + `:actions` hiệu ứng phụ
- ✅ Multi-stage forms với hooks `on-begin`/`on-end`

---

## 📖 Tài Liệu

- **Hướng dẫn đầy đủ tiếng Việt**: [`guide.md`](./guide.md)
- **Thiết kế & concept**: [`jsonlogic_concept.md`](./jsonlogic_concept.md)
