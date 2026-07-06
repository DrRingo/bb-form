#! id = medical_screening

-> start -> Bắt đầu
# Phiếu Khám Sàng Lọc
Thu thập thông tin sức khỏe ban đầu trước khi gặp bác sĩ
---

::: [{$ muc_do_kho_tho nhiet_do $}]
{% if (nhiet_do >= 39 or muc_do_kho_tho == "Nặng (không nói được)") %}
yeu_cau_cap_cuu* = SelectBox(
  | question = ⚠️ CẢNH BÁO TÌNH TRẠNG NGUY HIỂM: Bệnh nhân có dấu hiệu trở nặng (sốt cao > 39°C hoặc khó thở nặng). Cần gọi xe cấp cứu ngay không?
  | options = Có, gọi cấp cứu ngay lập tức, Không, đang tự xử lý / đi khám
)
{% endif %}
:::

ho_ten* = TextInput(
  | question = Họ và tên bệnh nhân
)

ngay_sinh* = DateInput(
  | question = Ngày sinh
)

gioi_tinh* = SelectBox(
  | question = Giới tính
  | options = Nam, Nữ
)

::: [{$ gioi_tinh $}]
{% if gioi_tinh == "Nữ" %}
co_thai* = SelectBox(
  | question = Hiện đang mang thai?
  | options = Có, Không, Không chắc
)
{% endif %}
:::

::: [{$ co_thai gioi_tinh $}]
{% if (gioi_tinh == "Nữ" and co_thai == "Có") %}
tuan_thai* = NumberInput(
  | question = Tuần thai hiện tại (ước tính)
)
{% endif %}
:::

trieu_chung = ChoiceInput(
  | question = Triệu chứng hiện tại (chọn tất cả các triệu chứng)
  | choices = Sốt, Ho, Khó thở, Đau ngực, Chóng mặt, Buồn nôn, Đau bụng, Mệt mỏi, Đau đầu, Không có triệu chứng
  | multiple = true
)

::: [{$ trieu_chung $}]
{% if (trieu_chung and ("Sốt" in trieu_chung)) %}
nhiet_do* = NumberInput(
  | question = Nhiệt độ đo được (°C)
)
{% endif %}
:::

::: [{$ trieu_chung $}]
{% if (trieu_chung and ("Sốt" in trieu_chung)) %}
so_ngay_sot* = NumberInput(
  | question = Đã sốt bao nhiêu ngày?
)
{% endif %}
:::

::: [{$ trieu_chung $}]
{% if (trieu_chung and ("Khó thở" in trieu_chung)) %}
muc_do_kho_tho* = SelectBox(
  | question = Mức độ khó thở
  | options = Nhẹ (vẫn nói chuyện được), Vừa (khó nói chuyện), Nặng (không nói được)
)
{% endif %}
:::

::: [{$ trieu_chung $}]
{% if (trieu_chung and ("Đau ngực" in trieu_chung)) %}
dau_nguc_kieu* = SelectBox(
  | question = Tính chất đau ngực
  | options = Đau tức, Đau nhói, Đau âm ỉ, Đau lan ra vai/cánh tay
)
{% endif %}
:::

tien_su_benh = ChoiceInput(
  | question = Tiền sử bệnh mãn tính (chọn nhiều nếu có)
  | choices = Tiểu đường, Tăng huyết áp, Tim mạch, Hen suyễn, Bệnh thận, Bệnh gan, Không có
  | multiple = true
)

so_dien_thoai_lien_he* = TextInput(
  | question = Số điện thoại liên hệ
  | pattern = ^[0-9]{10,11}$
)

ngay_kham* = DateInput(
  | question = Ngày khám
)

ghi_chu_them = TextInput(
  | question = Thông tin thêm muốn thông báo với bác sĩ
)
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
