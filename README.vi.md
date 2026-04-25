# bb-form

Script sử dụng Clojure Babashka và Charm-gum để thu thập dữ liệu từ form đẹp mắt trong terminal với giao diện người dùng được tối ưu hóa

# Yêu cầu hệ thống

- [Clojure Babashka](https://babashka.org/) — có thể cài tự động qua Homebrew / Scoop
- [Charm Gum](https://github.com/charmbracelet/gum) — có thể cài tự động qua Homebrew / Scoop

# Cài đặt

## Homebrew (macOS / Linux)

```bash
# Thêm tap (chỉ cần làm một lần)
brew tap drringo/bb-form https://github.com/drringo/bb-form

# Cài đặt bb-form (babashka và gum sẽ được cài tự động nếu chưa có)
brew install bb-form
```

Cập nhật lên phiên bản mới:
```bash
brew upgrade bb-form
```

## Scoop (Windows)

```powershell
# 1. Cài gum trước (không có trong Scoop, dùng winget)
winget install charmbracelet.gum

# 2. Thêm bucket bb-form (chỉ cần làm một lần)
scoop bucket add drringo https://github.com/drringo/bb-form

# 3. Cài bb-form (babashka sẽ được cài tự động)
scoop install bb-form
```

> **Lưu ý:** `gum` không có trong bất kỳ Scoop bucket nào. Cài qua `winget install charmbracelet.gum` hoặc tải từ [GitHub Releases](https://github.com/charmbracelet/gum/releases).

Cập nhật:
```powershell
scoop update bb-form
```

## Cài đặt bằng bbin (dành cho developer Babashka)

Nếu đã cài [bbin](https://github.com/babashka/bbin):

```bash
# Cài đặt từ GitHub
bbin install io.github.drringo/bb-form

# Hoặc cài đặt từ thư mục local (nếu đang phát triển)
bbin install .
```

Sau đó bạn có thể chạy lệnh:
```bash
bb-form form.json [--values values.json] [--out output.json] [field1:value1 ...]
```

## Tải thủ công

Tải file phù hợp với nền tảng của bạn từ [trang Releases](https://github.com/drringo/bb-form/releases):

| Nền tảng | File |
|---|---|
| Linux x86_64 | `bb-form-linux-x86_64.tar.gz` |
| macOS x86_64 (Intel) | `bb-form-macos-x86_64.tar.gz` |
| macOS arm64 (Apple Silicon) | `bb-form-macos-arm64.tar.gz` |
| Windows x86_64 | `bb-form-windows-x86_64.zip` |

Giải nén và đặt `bb-form` (hoặc `bb-form.bat` trên Windows) vào thư mục có trong `PATH`.

# Tính năng giao diện người dùng

## Màn hình sạch sẽ
- Tự động xóa màn hình trước khi hiển thị form đầu tiên
- Giao diện sạch sẽ, không có nội dung cũ từ terminal

## Dòng trạng thái cố định
- Hiển thị thông báo lỗi và trạng thái ở vị trí cố định sau tiêu đề form
- Thông báo lỗi bắt đầu với "::::" để dễ nhận biết
- Thông báo lỗi chỉ biến mất khi người dùng nhập đúng giá trị
- Sử dụng GUM style để hiển thị thông báo lỗi đẹp mắt với viền đỏ

## Trải nghiệm người dùng được cải thiện
- Màn hình được cập nhật liên tục để giữ giao diện sạch sẽ
- Thông báo lỗi rõ ràng và dễ đọc
- Giao diện nhất quán trong suốt quá trình điền form
- Khoảng cách hợp lý giữa các thành phần giao diện

# Giải thích các file

- `src/com/drbinhthanh/bb_form.clj` — File Clojure chính chứa toàn bộ logic xử lý form và giao diện người dùng
- `bb.edn` — File cấu hình cho bbin/babashka (bao gồm build tasks)
- `Formula/bb-form.rb` — Homebrew formula để cài đặt trên macOS/Linux
- `bucket/bb-form.json` — Scoop manifest để cài đặt trên Windows
- `scripts/build.sh` — Script build Unix: tạo uberscript và đóng gói cho mọi nền tảng
- `scripts/build.ps1` — Script build Windows (PowerShell)
- `.github/workflows/release.yml` — GitHub Actions: tự động build và publish release
- `form.json` — File cấu hình form, hỗ trợ câu hỏi phân nhánh
- `values.json` — File chứa giá trị mặc định cho form
- `result.json` — File kết quả sau khi điền form trong terminal

## Cấu hình bb.edn

File `bb.edn` được cấu hình để tương thích với bbin:

```clojure
{:deps {io.github.drringo/bb-form {:local/root "."}
        cheshire/cheshire {:mvn/version "5.11.0"}
        babashka/process {:mvn/version "0.5.22"}}
 :paths [".","src"] 
 :bbin/bin {bb-form {:main-opts ["-m" "com.drbinhthanh.bb-form"]}}}
```

### Giải thích cấu hình:
- `:deps`: Các dependencies cần thiết (cheshire cho JSON, babashka/process cho subprocess)
- `:paths`: Đường dẫn tìm kiếm source code
- `:bbin/bin`: Cấu hình cho bbin với `:main-opts` để gọi đúng namespace

# Hướng dẫn sử dụng thủ công (không qua bbin)

```bash
bb src/com/drbinhthanh/bb_form.clj form.json [--values values.json] [--out output.json] [field1:value1 ...]
```

## Ví dụ sử dụng

```bash
# Chạy form cơ bản
bb-form form_sample.json

# Chạy form với file values
bb-form form_sample.json --values values.json

# Chạy form với output file tùy chỉnh
bb-form form_sample.json --out my_result.json

# Chạy form với cả values và output
bb-form form_sample.json --values values.json --out custom_output.json

# Chạy form với giá trị từ command line
bb-form form_sample.json name:"Nguyễn Văn A" age:25
```

# Hướng dẫn tạo file `form.json`

### Cấu trúc cơ bản:
```json
{
  "title": "Tiêu đề form",
  "description": "Mô tả form",
  "fields": [
    {
      "id": "tên_field",
      "label": "Nhãn hiển thị",
      "type": "loại_field",
      "required": true/false,
      "options": ["lựa chọn 1", "lựa chọn 2"],
      "regex": "^[a-zA-Z0-9]+$",
      "branch": {
        "lựa chọn": [
          {
            "id": "field_con",
            "label": "Nhãn field con",
            "type": "loại_field",
            "required": true/false
          }
        ]
      }
    }
  ]
}
```

### Ví dụ field text với regex:
```json
{
  "id": "email",
  "label": "Email",
  "type": "text",
  "required": true,
  "regex": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
  "regexError": "Email không đúng định dạng. Ví dụ: user@example.com"
}
```

### Các loại field hỗ trợ:
- `text`: Input văn bản (hỗ trợ validation regex)
- `number`: Input số nguyên với validation
- `date`: Input ngày tháng (format DD-MM-YYYY) với validation
- `select`: Dropdown chọn một lựa chọn
- `multiselect`: Chọn nhiều lựa chọn

### Thuộc tính field:
- `id`: Định danh duy nhất cho field
- `label`: Nhãn hiển thị cho người dùng
- `type`: Loại field (text, number, date, select, multiselect)
- `required`: Kiểm tra ràng buộc đầu vào (true/false)
- `options`: Danh sách lựa chọn cho select/multiselect
- `branch`: Logic phân nhánh - hiển thị field con dựa trên lựa chọn
- `regex`: Biểu thức chính quy để validate field text (tùy chọn)
- `regexError`: Thông báo lỗi tùy chỉnh khi regex không thỏa mãn (tùy chọn)

### Validation rules:
- **text**: Hỗ trợ regex validation với thông báo lỗi tùy chỉnh
- **number**: Bắt buộc nhập số nguyên
- **date**: Bắt buộc nhập đúng định dạng DD-MM-YYYY, nếu để trống sẽ tự động lấy ngày hôm nay
  - Hỗ trợ gõ tắt: `04` (ngày 04 tháng hiện tại năm hiện tại), `1204` (ngày 12 tháng 04 năm hiện tại)

### Cải tiến validation:
- Thông báo lỗi hiển thị ngay lập tức khi người dùng nhập sai
- Màn hình được cập nhật để hiển thị thông báo lỗi rõ ràng
- Validation được thực hiện real-time với giao diện phản hồi nhanh

### Tính năng phân nhánh (Branching):
- Cho phép hiển thị field con dựa trên lựa chọn của field cha
- Hỗ trợ nhiều cấp độ phân nhánh
- Tự động ẩn/hiện field dựa trên logic

## File kết quả

Kết quả được lưu vào file `result.json` với cấu trúc:
```json
{
  "selectedByUser": {
    "field1": "value1",
    "field2": "value2",
    "field_with_branch": "selected_option",
    "field_with_branch_branch": {
      "selected_option": {
        "subfield1": "subvalue1",
        "subfield2": "subvalue2"
      }
    },
    "multiselect_field": ["option1", "option2"],
    "multiselect_field_branch": {
      "option1": {
        "subfield1": "subvalue1"
      },
      "option2": {
        "subfield2": "subvalue2"
      }
    }
  }
}
```

### Cấu trúc dữ liệu:
- **Level cao nhất**: Tất cả các field được nhóm vào key `"selectedByUser"`
- **Field đơn giản**: Giá trị trực tiếp (text, number, date, select)
- **Field có branching**: 
  - Giá trị gốc được lưu trực tiếp
  - Field con được lưu trong key `"{field_id}_branch"`
- **Field multiselect**: 
  - Danh sách các lựa chọn được lưu trực tiếp
  - Field con được lưu trong key `"{field_id}_branch"`
- **Cấu trúc thống nhất**: Cả select và multiselect đều dùng suffix `"_branch"`
- **Hỗ trợ nhiều cấp**: Có thể có field con của field con
- **Lồng nhiều cấp**: Field con có thể tiếp tục có nhánh, tạo ra `{subfield_id}_branch`

### Ví dụ cấu trúc phức tạp:
```json
{
  "selectedByUser": {
    "name": "Nguyễn Văn A",
    "age": 25,
    "gender": "Nữ",
    "gender_branch": {
      "Nữ": {
        "is_pregnant": "Có",
        "is_pregnant_branch": {
          "Có": {
            "gestational_age": 20
          }
        }
      }
    },
    "symptoms": ["Sốt", "Khó thở"],
    "symptoms_branch": {
      "Sốt": {
        "temperature": 38.5
      },
      "Khó thở": {
        "breath_level": "Vừa"
      }
    },
    "exam_date": "2024-01-15",
    "notes": "Ghi chú thêm"
  }
}
```

### Cách truy cập dữ liệu:
```javascript
// Giá trị gốc
const gender = result.selectedByUser.gender; // "Nữ"
const symptoms = result.selectedByUser.symptoms; // ["Sốt", "Khó thở"]

// Field con của select
const isPregnant = result.selectedByUser.gender_branch["Nữ"].is_pregnant; // "Có"
const gestationalAge = result.selectedByUser.gender_branch["Nữ"].is_pregnant_branch["Có"].gestational_age; // 20

// Field con của multiselect
const temperature = result.selectedByUser.symptoms_branch["Sốt"].temperature; // 38.5
const breathLevel = result.selectedByUser.symptoms_branch["Khó thở"].breath_level; // "Vừa"
```

### Lưu ý:
- Tất cả các field ở level cao nhất được nhóm vào key `"selectedByUser"`
- **Giá trị gốc luôn nhất quán**: Field có branching vẫn lưu giá trị được chọn trực tiếp
- **Cấu trúc thống nhất**: Cả select và multiselect đều dùng suffix `"_branch"`
- **Key trong _branch**: Là giá trị được chọn (ví dụ "Nữ", "Sốt", "Khó thở")
- **Dễ xử lý**: Không cần kiểm tra kiểu dữ liệu khi truy cập giá trị gốc
- **Cấu trúc rõ ràng**: Field con được tổ chức theo logic phân cấp
- **Lồng nhiều cấp**: Hỗ trợ branching không giới hạn độ sâu

# Cập nhật gần đây

## Phiên bản hiện tại
- ✅ **Dòng trạng thái cố định**: Hiển thị thông báo lỗi ở vị trí cố định với prefix "::::"
- ✅ **Giao diện GUM**: Sử dụng Charm-gum để hiển thị thông báo lỗi đẹp mắt
- ✅ **Validation real-time**: Thông báo lỗi hiển thị ngay lập tức khi nhập sai
- ✅ **Trải nghiệm người dùng**: Giao diện nhất quán và dễ sử dụng
- ✅ **Hỗ trợ bbin hoàn chỉnh**: Cài đặt và chạy thông qua bbin

## Cải tiến kỹ thuật
- Sửa lỗi syntax trong các hàm validation
- Tối ưu hóa hiển thị thông báo lỗi
- Cải thiện logic clear màn hình và re-render
- Tăng cường tính ổn định của giao diện
``` 
