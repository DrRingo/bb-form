#! id = sanh

-> start -> Bắt đầu
# Đánh Giá Tình Trạng Chuyển Dạ
Biểu mẫu thu thập thông tin lâm sàng chuyển dạ của sản phụ.
---

ngoi_thai* = SelectBox(
  | question = Ngôi thai
  | options = Đầu, Mông, Ngang, Chưa xác định
)

::: [{$ ngoi_thai $}]
{% if (ngoi_thai == "Đầu" or ngoi_thai == "Mông") %}
do_mo_ctc* = SelectBox(
  | question = Độ mở CTC
  | options = Đóng, Mở 2cm, Mở 3cm, Mở 4cm, Mở 5cm, Mở 6cm, Mở 7cm, Mở 8cm, Gần trọn, Trọn
)
{% endif %}
:::

::: [{$ do_mo_ctc ngoi_thai $}]
{% if ((ngoi_thai == "Đầu" or ngoi_thai == "Mông") and (do_mo_ctc == "Mở 2cm" or do_mo_ctc == "Mở 3cm" or do_mo_ctc == "Mở 4cm" or do_mo_ctc == "Mở 5cm" or do_mo_ctc == "Mở 6cm")) %}
do_xoa_ctc* = SelectBox(
  | question = Độ xóa CTC
  | options = 50%, 60%, 80%
)
{% endif %}
:::

::: [{$ do_mo_ctc ngoi_thai $}]
{% if (ngoi_thai == "Đầu" and (do_mo_ctc == "Mở 6cm" or do_mo_ctc == "Mở 7cm" or do_mo_ctc == "Mở 8cm" or do_mo_ctc == "Gần trọn" or do_mo_ctc == "Trọn")) %}
kieu_the_dau* = SelectBox(
  | question = Kiểu thế (Ngôi Đầu)
  | options = Chẩm trái trước, Chẩm trái sau, Chẩm phải trước, Chẩm phải sau, Chẩm cùng, Kiểu thế ngang
)
{% endif %}
:::

::: [{$ do_mo_ctc ngoi_thai $}]
{% if (ngoi_thai == "Mông" and (do_mo_ctc == "Mở 6cm" or do_mo_ctc == "Mở 7cm" or do_mo_ctc == "Mở 8cm" or do_mo_ctc == "Gần trọn" or do_mo_ctc == "Trọn")) %}
kieu_the_mong* = SelectBox(
  | question = Kiểu thế (Ngôi Mông)
  | options = Mông thiếu kiểu mông, Mông thiếu kiểu chân, Mông đủ
)
{% endif %}
:::

::: [{$ do_mo_ctc ngoi_thai $}]
{% if ((ngoi_thai == "Đầu" or ngoi_thai == "Mông") and (do_mo_ctc == "Gần trọn" or do_mo_ctc == "Trọn")) %}
do_lot* = NumberInput(
  | question = Độ lọt (nhập số từ -2 đến +3)
)
{% endif %}
:::

::: [{$ do_mo_ctc ngoi_thai $}]
{% if ((ngoi_thai == "Đầu" or ngoi_thai == "Mông") and (do_mo_ctc == "Gần trọn" or do_mo_ctc == "Trọn")) %}
buou_huyet_thanh* = SelectBox(
  | question = Tình trạng bướu huyết thanh
  | options = Nhỏ, Trung bình, Lớn
)
{% endif %}
:::

nuoc_oi* = SelectBox(
  | question = Tình trạng nước ối
  | options = Còn, Đã vỡ
)

::: [{$ nuoc_oi $}]
{% if nuoc_oi == "Đã vỡ" %}
gio_vo_oi* = DatetimeInput(
  | question = Ngày giờ vỡ ối (DD-MM-YYYY HH:mm)
  | pattern = ^\d{2}-\d{2}-\d{4} \d{2}:\d{2}$
)
{% endif %}
:::

::: [{$ nuoc_oi $}]
{% if nuoc_oi == "Đã vỡ" %}
⏱️  Số giờ vỡ ối đến hiện tại: {$ sanh.tinh_gio_vo_oi(gio_vo_oi) $} giờ
{% endif %}
:::

khung_chau* = SelectBox(
  | question = Tình trạng khung chậu
  | options = Hẹp, Giới hạn, Bình thường
)

can_nang_thai* = NumberInput(
  | question = Ước lượng cân nặng thai nhi (gram, từ 500 đến 6000)
)
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
