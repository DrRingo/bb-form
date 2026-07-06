#! id = hr_survey

-> start -> Bắt đầu
# Khảo Sát Nhân Sự
Thu thập thông tin nhân viên và đánh giá nội bộ
---

ho_ten* = TextInput(
  | question = Họ và tên đầy đủ
)

::: [{$ ngon_ngu_chinh $}]
{% if ngon_ngu_chinh == "Clojure" %}
cap_macbook_pro* = SelectBox(
  | question = 🚀 ĐẶC QUYỀN: Nhân sự lập trình Clojure được cấp sẵn Macbook Pro. Bạn có muốn nhận máy của công ty không?
  | options = Có, nhận Macbook Pro, Không, tôi dùng máy cá nhân
)
{% endif %}
:::

email_cong_ty* = EmailInput(
  | question = Email công ty
  | pattern = ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$
)

ma_nhan_vien* = TextInput(
  | question = Mã nhân viên (VD: NV001)
  | pattern = ^NV[0-9]{3,6}$
)

phong_ban* = SelectBox(
  | question = Phòng ban
  | options = Kỹ thuật, Sản phẩm, Kinh doanh, Nhân sự, Tài chính, Marketing
)

::: [{$ phong_ban $}]
{% if phong_ban == "Kỹ thuật" %}
ngon_ngu_chinh* = SelectBox(
  | question = Ngôn ngữ lập trình chính
  | options = Clojure, Python, Go, Rust, TypeScript, Khác
)
{% endif %}
:::

::: [{$ phong_ban $}]
{% if phong_ban == "Kỹ thuật" %}
nam_kinh_nghiem_kt* = NumberInput(
  | question = Số năm kinh nghiệm lập trình
)
{% endif %}
:::

::: [{$ phong_ban $}]
{% if phong_ban == "Kinh doanh" %}
vung_phu_trach* = SelectBox(
  | question = Vùng phụ trách
  | options = Miền Bắc, Miền Trung, Miền Nam, Toàn quốc
)
{% endif %}
:::

::: [{$ phong_ban $}]
{% if phong_ban == "Nhân sự" %}
chuyen_mon_hr = ChoiceInput(
  | question = Chuyên môn HR
  | choices = Tuyển dụng, Đào tạo, C&B, Quan hệ lao động
  | multiple = true
)
{% endif %}
:::

trinh_do_hoc_van* = SelectBox(
  | question = Trình độ học vấn
  | options = Trung cấp / Cao đẳng, Đại học, Thạc sĩ, Tiến sĩ
)

::: [{$ trinh_do_hoc_van $}]
{% if trinh_do_hoc_van == "Thạc sĩ" %}
chuyen_nganh_thac_si* = TextInput(
  | question = Chuyên ngành Thạc sĩ
)
{% endif %}
:::

::: [{$ trinh_do_hoc_van $}]
{% if trinh_do_hoc_van == "Tiến sĩ" %}
chuyen_nganh_tien_si* = TextInput(
  | question = Chuyên ngành Tiến sĩ
)
{% endif %}
:::

::: [{$ trinh_do_hoc_van $}]
{% if trinh_do_hoc_van == "Tiến sĩ" %}
truong_dao_tao* = TextInput(
  | question = Trường đào tạo
)
{% endif %}
:::

ky_nang = ChoiceInput(
  | question = Kỹ năng nổi bật (chọn nhiều)
  | choices = Lãnh đạo, Giao tiếp, Phân tích dữ liệu, Ngoại ngữ, Quản lý dự án, Sáng tạo
  | multiple = true
)

::: [{$ ky_nang $}]
{% if (ky_nang and ("Ngoại ngữ" in ky_nang)) %}
ngoai_ngu_chinh* = SelectBox(
  | question = Ngoại ngữ sử dụng tốt nhất
  | options = Tiếng Anh, Tiếng Nhật, Tiếng Trung, Tiếng Hàn, Khác
)
{% endif %}
:::

ngay_vao_lam* = DateInput(
  | question = Ngày vào làm
)

muc_do_hai_long* = SelectBox(
  | question = Mức độ hài lòng với công việc hiện tại
  | options = Rất hài lòng, Hài lòng, Bình thường, Không hài lòng
)

::: [{$ muc_do_hai_long $}]
{% if muc_do_hai_long == "Không hài lòng" %}
ly_do_khong_hai_long* = SelectBox(
  | question = Lý do chính không hài lòng
  | options = Lương thưởng, Môi trường làm việc, Cơ hội thăng tiến, Quản lý, Khác
)
{% endif %}
:::

ghi_chu = TextInput(
  | question = Góp ý / Ghi chú thêm (tùy chọn)
)
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
