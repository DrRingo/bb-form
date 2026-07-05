---
name: formsmd
description: Hướng dẫn viết mã nguồn Markdown theo chuẩn của Forms.md. Bao gồm cấu trúc slide, cấu hình toàn cục, các loại trường nhập liệu, liên kết dữ liệu (data binding) và logic phản ứng (reactive blocks).
---

# Hướng Dẫn Cú Pháp Markdown Forms.md (Forms.md Syntax Guide)

Forms.md là một thư viện cho phép xây dựng các biểu mẫu nhiều bước (multi-step forms), khảo sát tương tác và ứng dụng thu thập dữ liệu bằng cách sử dụng kết hợp giữa cú pháp Markdown mở rộng và các hàm khởi tạo (constructors).

Tài liệu này được thiết kế để phục vụ cả con người (lập trình viên) và AI (các tác nhân mã hóa) trong việc đọc hiểu, thiết kế và phát sinh tự động các file `.md` hợp lệ cho Forms.md.

---

## 1. Cấu Hình Toàn Cục (Global Settings)

Các cấu hình điều khiển hành vi và giao diện của toàn bộ biểu mẫu được đặt ở đầu file Markdown, sử dụng tiền tố `#!`. Mỗi dòng cấu hình tuân theo định dạng `#! {tên-thuộc-tính} = {giá-trị}`.

| Thuộc tính | Kiểu dữ liệu | Giá trị mặc định | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | Chuỗi (string) | *(Không có)* | Định danh duy nhất cho biểu mẫu (khuyến nghị luôn luôn khai báo). |
| `post-url` | Chuỗi (string) | *(Không có)* | Endpoint URL để gửi dữ liệu form qua phương thức POST khi hoàn thành. |
| `submit-button-text`| Chuỗi (string) | `"Submit"` | Nhãn hiển thị trên nút gửi biểu mẫu ở bước cuối cùng. |
| `restart-button` | `"show"` / `"hide"` | `"hide"` | Hiển thị nút bắt đầu lại biểu mẫu ở slide kết thúc. |
| `formsmd-branding` | `"show"` / `"hide"` | `"show"` | Ẩn/hiện dòng chữ "Powered by Forms.md" (yêu cầu gói Pro trở lên). |
| `footer` | `"show"` / `"hide"` | `"show"` | Ẩn/hiện phần chân trang (chứa nút Next/Previous). |
| `autofocus` | `"all-slides"` | *(Không có)* | Nếu đặt là `all-slides`, trường nhập liệu đầu tiên của slide sẽ tự động được focus khi chuyển slide. |
| `button-alignment` | `"center"` / `"end"` / `"stretch"` | `"stretch"` | Cách căn lề cho các nút bấm hành động ở cuối mỗi slide. |
| `dir` | `"ltr"` / `"rtl"` | `"ltr"` | Hướng viết văn bản (Trái-qua-Phải hoặc Phải-qua-Trái). |
| `font-size` | `"sm"` / `"lg"` | *(Mặc định)* | Điều chỉnh kích thước phông chữ toàn biểu mẫu. |
| `field-size` | `"sm"` | *(Mặc định)* | Thu nhỏ kích thước của các trường nhập liệu nếu đặt là `sm`. |
| `form-style` | `"classic"` | *(Mặc định)* | Giao diện hiển thị dạng truyền thống cho biểu mẫu. |
| `label-style` | `"classic"` | *(Mặc định)* | Làm nhỏ tiêu đề câu hỏi và mô tả bên dưới. |
| `page` | `"form-slides"` / `"slides"` / `"single"` | `"form-slides"` | Bố cục biểu mẫu: trượt từng bước, trượt tự do, hoặc hiển thị tất cả trên một trang đơn. |
| `page-progress` | `"show"` / `"hide"` / `"decorative"` | `"show"` | Hiển thị thanh tiến trình hoàn thành biểu mẫu. |
| `vertical-alignment` | `"start"` | *(Mặc định)* | Căn lề nội dung biểu mẫu lên sát mép trên của khung chứa. |
| `slide-delimiter` | Chuỗi (string) | `"---"` | Ký tự dùng để ngăn cách giữa các slide. |
| `form-delimiter` | Chuỗi (string) | `"|"` | Ký tự ngăn cách giữa các tham số bên trong hàm khởi tạo. |

**Ví dụ:**
```markdown
#! id = khao-sat-dich-vu
#! post-url = /api/v1/survey
#! submit-button-text = Gửi Đánh Giá
#! restart-button = show
#! autofocus = all-slides
```

---

## 2. Cấu Trúc Slide và Phân Trang

Một biểu mẫu nhiều bước được chia thành các Slide riêng biệt thông qua đường kẻ ngang `---` (trừ khi `slide-delimiter` được cấu hình khác). Có 3 loại slide chính:

### 2.1. Slide Bắt Đầu (Start Slide)
Dùng làm trang chào mừng (landing page) của biểu mẫu. Slide này chứa nút bấm để kích hoạt bắt đầu điền form.
- **Cú pháp**: `-> start [-> {Tên nút bấm}] [=| {căn-lề-nút}]`
- **Căn lề nút**: `start`, `center`, `end`, `stretch`.

**Ví dụ:**
```markdown
-> start -> Bắt đầu khảo sát =| center
# Chào mừng bạn đến với Khảo sát Khách hàng
Hãy dành 2 phút giúp chúng tôi cải thiện dịch vụ.
---
```

### 2.2. Slide Nội Dung và Nhập Liệu (Standard Slides)
Chứa các câu hỏi, văn bản mô tả hoặc khối reactive logic. Ngăn cách nhau bằng `---`.

**Ví dụ:**
```markdown
ho_ten* = TextInput(| question = Họ tên của bạn)
---
email* = EmailInput(| question = Địa chỉ Email)
```

### 2.3. Slide Kết Thúc (End Slide)
Xuất hiện sau khi người dùng điền và gửi biểu mẫu thành công.
- **Cú pháp**: `-> end [-> {URL chuyển hướng}]`

**Ví dụ:**
```markdown
-> end -> https://drbinhthanh.com/thankyou
# Cảm ơn bạn rất nhiều!
Phản hồi của bạn đã được ghi lại thành công.
```

---

## 3. Các Loại Trường Nhập Liệu (Input Types)

Mỗi trường dữ liệu được định nghĩa dưới dạng một biến và hàm khởi tạo:
```markdown
tên_biến[*] = TênConstructor(
  | tham_số_1 = giá_trị_1
  | tham_số_2 = giá_trị_2
)
```
*Lưu ý: Thêm dấu hoa thị `*` ngay sau tên biến (ví dụ: `ho_ten* = ...`) để đánh dấu trường đó là **bắt buộc (required)**.*

### 3.1. Các tham số chung (Common Parameters)
- `question` (chuỗi): Nhãn/câu hỏi hiển thị phía trên input.
- `description` (chuỗi): Dòng mô tả chi tiết, hướng dẫn nhỏ bên dưới câu hỏi.
- `placeholder` (chuỗi): Văn bản gợi ý mờ trong input.
- `value` (bất kỳ): Giá trị mặc định khi khởi tạo.
- `disabled` (boolean): Vô hiệu hóa trường nhập liệu (`true` hoặc `false`).
- `classNames` (danh sách): Các class CSS tùy chỉnh (ví dụ: `[.col-6]`).

---

### 3.2. Chi tiết các Constructor

#### 1. TextInput (Nhập văn bản thường)
Dùng cho văn bản một dòng hoặc nhiều dòng.
```markdown
dia_chi* = TextInput(
  | question = Địa chỉ hiện tại của bạn
  | placeholder = Số nhà, tên đường, quận/huyện...
  | pattern = ^[a-zA-Z0-9\s,.-]+$
  | maxlength = 200
)
```
- `multiline = true`: Chuyển trường nhập liệu thành một khung nhập văn bản lớn `<textarea>`.
- `pattern` (chuỗi): Biểu thức chính quy (Regex) để kiểm tra tính hợp lệ của dữ liệu đầu vào.
- `maxlength` / `minlength` (số): Giới hạn độ dài ký tự tối đa/tối thiểu.

#### 2. EmailInput (Nhập Email)
Tương tự như `TextInput` nhưng tự động tích hợp xác thực định dạng email chuẩn.
```markdown
email* = EmailInput(
  | question = Địa chỉ Email liên hệ
  | placeholder = name@example.com
)
```

#### 3. URLInput (Nhập đường dẫn web)
Tự động xác thực định dạng URL (phải bắt đầu bằng http:// hoặc https://).
```markdown
website = URLInput(
  | question = Trang web cá nhân của bạn
  | placeholder = https://example.com
)
```

#### 4. TelephoneInput / TelInput (Nhập số điện thoại)
Hỗ trợ nhập số điện thoại kèm mã quốc gia.
```markdown
sdt* = TelInput(
  | question = Số điện thoại di động
  | placeholder = 0901234567
)
```

#### 5. PasswordInput (Nhập mật khẩu)
Khung nhập ẩn ký tự để bảo mật thông tin.
```markdown
mat_khau* = PasswordInput(
  | question = Tạo mật khẩu của bạn
  | placeholder = Nhập ít nhất 8 ký tự
)
```

#### 6. NumberInput (Nhập số)
Trường chỉ chấp nhận giá trị số nguyên hoặc số thực.
```markdown
so_luong* = NumberInput(
  | question = Số lượng đăng ký
  | min = 1
  | max = 50
  | step = 1
  | unitEnd = Vé
)
```
- `min` / `max` (số): Ngưỡng giá trị nhỏ nhất / lớn nhất.
- `step` (số): Khoảng bước tăng/giảm (ví dụ: `0.5`, `1`).
- `unitEnd` (chuỗi): Đơn vị hiển thị phía sau số (ví dụ: `Kg`, `Vé`, `VND`).

#### 7. SelectBox (Hộp lựa chọn - Dropdown)
Dịch vụ cung cấp danh sách tùy chọn cho phép chọn 1 giá trị dưới dạng menu thả xuống.
```markdown
quoc_tich* = SelectBox(
  | question = Quốc tịch của bạn
  | choices = Việt Nam, Nhật Bản, Hoa Kỳ, Hàn Quốc
)
```
- `choices` (chuỗi phân cách bằng dấu phẩy): Danh sách các tùy chọn.

#### 8. ChoiceInput (Lựa chọn dạng Radio / Checkbox)
Hiển thị danh sách các hộp chọn trực quan.
```markdown
chu_de* = ChoiceInput(
  | question = Các chủ đề bạn quan tâm
  | choices = Clojure, Babashka, Lisp, React, Java
  | multiple = true
  | layout = vertical
)
```
- `choices` (chuỗi phân cách bằng dấu phẩy): Danh sách các tùy chọn.
- `multiple = true`: Cho phép chọn nhiều tùy chọn (dạng Checkboxes). Mặc định là `false` (dạng Radio Buttons - chọn một).
- `layout`: Hướng sắp xếp các ô chọn (`horizontal` hoặc `vertical`).

#### 9. PictureChoice (Lựa chọn có hình ảnh đi kèm)
Cho phép lựa chọn các ô có chứa hình ảnh minh họa sinh động.
```markdown
mau_sac* = PictureChoice(
  | question = Chọn giao diện yêu thích
  | choices = Sáng, Tối
  | images = https://img.com/light.png, https://img.com/dark.png
)
```
- `images` (chuỗi phân cách bằng dấu phẩy): Các đường dẫn URL tới ảnh tương ứng với từng lựa chọn trong `choices`.

#### 10. RatingInput (Đánh giá bằng Sao / Biểu tượng)
Biểu mẫu đánh giá mức độ hài lòng dạng sao.
```markdown
muc_do_hai_long* = RatingInput(
  | question = Bạn đánh giá thế nào về chất lượng dịch vụ?
  | max = 5
)
```
- `max` (số): Số lượng ngôi sao tối đa (thường là 5 hoặc 10).

#### 11. OpinionScale (Thang điểm đánh giá / NPS)
Thang điểm ngang từ 0 đến 10 để đo lường chỉ số hài lòng.
```markdown
diem_nps* = OpinionScale(
  | question = Khả năng bạn giới thiệu chúng tôi cho bạn bè là bao nhiêu?
  | min = 0
  | max = 10
  | labelStart = Hoàn toàn không
  | labelCenter = Trung bình
  | labelEnd = Cực kỳ sẵn lòng
)
```
- `min` / `max` (số): Khoảng điểm (ví dụ: `0` đến `10`).
- `labelStart` / `labelCenter` / `labelEnd` (chuỗi): Các nhãn ghi chú tương ứng ở đầu, giữa và cuối thanh thang điểm.

#### 12. DateInput / DatetimeInput / TimeInput (Nhập ngày và giờ)
```markdown
ngay_sinh* = DateInput(
  | question = Ngày sinh của bạn
)

thoi_gian_hen* = DatetimeInput(
  | question = Chọn ngày và giờ hẹn
)

gio_don = TimeInput(
  | question = Thời gian đón xe
)
```

#### 13. FileInput (Tải tập tin lên)
Khung để đính kèm và tải lên tệp tin từ thiết bị.
```markdown
dinh_kem* = FileInput(
  | question = Tải CV của bạn lên (Định dạng PDF/Word)
  | accept = .pdf, .docx, .doc
  | sizeLimit = 5
)
```
- `accept` (chuỗi): Các đuôi mở rộng hoặc loại MIME được chấp nhận.
- `sizeLimit` (số): Giới hạn dung lượng tệp tin tối đa tính bằng MB (ví dụ: `5` nghĩa là tối đa 5MB).

---

## 4. Định Dạng Văn Bản và CSS Class Mở Rộng

Forms.md hỗ trợ định nghĩa CSS class, ID và các thuộc tính HTML trực tiếp trên các thẻ Markdown bằng cú pháp khối đặt trước: `[.class-name #id-name attribute="value"]`.

### 4.1. Định dạng Tiêu đề và Đoạn văn
```markdown
# [.text-center .text-primary] Khảo sát Sức Khỏe

[.fs-lead .text-muted] Hãy điền trung thực các thông tin dưới đây để nhận kết quả đánh giá chính xác nhất.
```

### 4.2. Đường kẻ phân cách trang trí
Sử dụng `***` hoặc `___` để tạo đường kẻ ngang trang trí trong một slide (tránh dùng `---` vì nó sẽ kích hoạt ngắt slide mới).
```markdown
Nội dung phần trên
***
Nội dung phần dưới
```

---

## 5. Liên Kết Dữ Liệu (Data Binding) & Khối Logic Phản Ứng (Reactive Blocks)

Đây là tính năng mạnh mẽ nhất của Forms.md, cho phép biểu mẫu tự thay đổi giao diện, ẩn/hiện trường nhập liệu, tính toán giá trị dựa trên các câu trả lời trước đó của người dùng.

### 5.1. Chèn giá trị biến động (Inline Interpolation)
Sử dụng biểu thức `{$ tên_biến $}` để hiển thị trực tiếp giá trị của một câu trả lời lên slide. Giá trị này sẽ cập nhật ngay lập tức khi người dùng nhập liệu.
```markdown
Chào mừng **{$ ho_ten $}** đến với hội thảo!
```

### 5.2. Khối Group chứa CSS (Division Containers)
Dùng để nhóm các thẻ Markdown lại với nhau nhằm áp dụng thuộc tính hoặc bố cục dạng cột (Grid).
```markdown
::: [.grid .col-2]
ho_ten* = TextInput(| question = Họ tên)
sdt* = TelInput(| question = Số điện thoại)
:::
```

### 5.3. Khối Logic Phản Ứng (Reactive Blocks)
Khối này được định nghĩa bằng cấu trúc dấu ba dấu hai chấm `:::` đi kèm danh sách biến phụ thuộc nằm trong ngoặc vuông:
`::: [{$ bien1 bien2 $}]`
Mọi nội dung bên trong khối này sẽ được tự động biên dịch lại khi giá trị của bất kỳ biến nào trong ngoặc vuông thay đổi.
Bên trong khối này, chúng ta sử dụng **cú pháp Nunjucks** để lập trình điều kiện và gán giá trị:

#### A. Ẩn/Hiện trường dựa vào điều kiện (Conditional Rendering)
```markdown
loai_ve* = SelectBox(
  | question = Chọn loại vé tham dự
  | choices = Tham dự trực tiếp, Xem trực tuyến
)

::: [{$ loai_ve $}]
{% if loai_ve == "Tham dự trực tiếp" %}
che_do_an* = SelectBox(
  | question = Chế độ ăn uống yêu thích
  | choices = Bình thường, Chay, Ăn kiêng
)
{% endif %}
:::
```

#### B. Tính toán giá trị động (Dynamic Computations)
Ta có thể thực hiện tính toán số học hoặc gọi các hàm Javascript toàn cục, sau đó gán kết quả vào một biến bằng cú pháp `{% set tên_biến = biểu_thức %}`.
```markdown
so_tuoi* = NumberInput(| question = Nhập tuổi của bạn)

::: [{$ so_tuoi $}]
{% set nhom_tuoi = "Trẻ em" %}
{% if so_tuoi >= 60 %}
  {% set nhom_tuoi = "Người cao tuổi" %}
{% elseif so_tuoi >= 18 %}
  {% set nhom_tuoi = "Thành niên" %}
{% endif %}

Phân loại độ tuổi của bạn: **{{ nhom_tuoi }}**
:::
```

#### C. Gọi hàm Javascript tùy chỉnh (Custom JS Interoperability)
Khi cần tính toán phức tạp (như tính toán chỉ số sức khỏe, điểm rủi ro), ta khai báo thư viện Javascript chứa các hàm xử lý ở phía HTML ngoài (gán vào đối tượng `window`), sau đó gọi trực tiếp từ bên trong khối Nunjucks.
*Chú ý: Cú pháp gọi hàm JS trong Nunjucks sử dụng dấu chấm để gọi thuộc tính (ví dụ: `cardio_risk.calc_risk` thay vì dấu gạch chéo).*

```markdown
tuoi* = NumberInput(| question = Tuổi)
bmi* = NumberInput(| question = Chỉ số BMI)
hut_thuoc* = ChoiceInput(| question = Có hút thuốc không? | choices = Có, Không)

::: [{$ tuoi bmi hut_thuoc $}]
{% set is_smoking = (hut_thuoc == "Có") %}
{% set muc_do_rui_ro = cardio_risk.calc_risk(tuoi, 22, bmi, is_smoking, 1.2) %}
{% set phan_loai = cardio_risk.risk_category(muc_do_rui_ro) %}

Chỉ số rủi ro tim mạch tính toán được: **{{ muc_do_rui_ro | round(1) }}%**
Đánh giá mức độ rủi ro: **{{ phan_loai }}**
:::
```

---

## 6. Hướng Dẫn Dành Cho Tác Nhân AI (AI Coding Guidelines)

Khi sinh mã nguồn Markdown hoặc tích hợp trình biên dịch từ EDN sang Forms.md, AI cần tuyệt đối tuân thủ các quy tắc sau:

1. **Slide ngăn cách rõ ràng**: Mỗi slide chứa các câu hỏi tương tác phải được ngăn cách bằng một dòng duy nhất chứa `---` kèm theo các dòng trống.
2. **Khai báo biến phụ thuộc**: Ở các khối `::: [{$ bien1 bien2 $}]`, bắt buộc phải liệt kê đầy đủ tất cả các biến xuất hiện trong điều kiện `{% if %}` hoặc các phép tính bên trong. Nếu thiếu, khối đó sẽ không bao giờ được cập nhật khi người dùng thay đổi dữ liệu của biến bị bỏ sót.
3. **Gọi hàm Javascript**: Luôn ánh xạ các ký hiệu namespace của Clojure (như `:cardio-risk/calc-risk`) thành tên gọi dạng Javascript tương thích (như `cardio_risk.calc_risk(...)`).
4. **Không chèn ký tự lạ**: Hãy chắc chắn các giá trị của tham số trong hàm khởi tạo được đặt trên một dòng độc lập bắt đầu bằng `| `. Không chèn dấu cách thừa giữa ký tự `|` và tên tham số.
5. **Escape ký tự backtick**: Khi nhúng Markdown vào chuỗi JS template trong HTML, hãy chắc chắn escape tất cả các ký tự backtick (`` ` ``) thành `\`` để tránh lỗi cú pháp Javascript.
6. **Required field mapping**: Đảm bảo ánh xạ chính xác thuộc tính bắt buộc từ EDN sang dấu hoa thị `*` sau tên biến của input trong Forms.md.
