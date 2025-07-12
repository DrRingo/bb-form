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

### Các loại field hỗ trợ:
- `text`: Input văn bản
- `number`: Input số nguyên với validation
- `date`: Input ngày tháng (format YYYY-MM-DD)
- `select`: Dropdown chọn một lựa chọn
- `multiselect`: Chọn nhiều lựa chọn

### Thuộc tính field:
- `id`: Định danh duy nhất cho field
- `label`: Nhãn hiển thị cho người dùng
- `type`: Loại field (text, number, date, select, multiselect)
- `required`: Kiểm tra ràng buộc đầu vào (true/false)
- `options`: Danh sách lựa chọn cho select/multiselect
- `branch`: Logic phân nhánh - hiển thị field con dựa trên lựa chọn

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

## File kết quả

Kết quả được lưu vào file `result.json` với cấu trúc:
```json
{
  "selectedByUser": {
    "field1": "value1",
    "field2": "value2"
  },
  "field1": {
    "value1": {
      "subfield": "subvalue"
    }
  }
}
```

### Lưu ý:
- Các field ở level cao nhất được nhóm vào key `"selectedByUser"`
- Các field con được tổ chức theo cấu trúc phân cấp
- Hỗ trợ lưu trữ dữ liệu phức tạp với nhiều cấp độ
