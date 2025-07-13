# bb-form

Script sử dụng Clojure Babashka và Charm-gum để thu thập dữ liệu từ form đẹp mắt trong terminal

# Yêu cầu hệ thống

- [Clojure babashka](https://babashka.org/)
- [Charm-gum](https://github.com/charmbracelet/gum)

# Giải thích các file

- `core.clj` :: File Clojure chính chứa các hàm xử lý form
- `run-form.clj` :: File chạy script với các tùy chọn command line
- `form.json` :: File cấu hình form, hỗ trợ câu hỏi phân nhánh
- `values.json` :: File chứa giá trị mặc định cho form
- `result.json` :: File kết quả sau khi điền form trong terminal

# Hướng dẫn sử dụng

## Hướng dẫn tạo file `form.json`

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

### Tính năng phân nhánh (Branching):
- Cho phép hiển thị field con dựa trên lựa chọn của field cha
- Hỗ trợ nhiều cấp độ phân nhánh
- Tự động ẩn/hiện field dựa trên logic

## Cách chạy script

### Chạy cơ bản:
```bash
bb run-form.clj form.json
```

### Chạy với giá trị mặc định:
```bash
bb run-form.clj form.json --values values.json
```

### Chạy với tham số command line:
```bash
bb run-form.clj form.json field1:value1 field2:value2
```

### Chạy với tùy chọn xuất file tùy chỉnh:
```bash
bb run-form.clj form.json --out path/myfilename.json
```

### Kết hợp các tùy chọn:
```bash
bb run-form.clj form.json --values values.json --out output.json field1:value1
```

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
