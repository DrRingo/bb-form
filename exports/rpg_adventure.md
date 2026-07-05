#! id = rpg_adventure

-> start -> Bắt đầu
# Vụ Án Mất Tích
Bạn là thám tử. Mọi lựa chọn đều dẫn đến những câu hỏi khác nhau.
---

ten_tham_tu* = TextInput(
  | question = Tên của bạn là gì, thám tử?
)

do_nghe = ChoiceInput(
  | question = Bạn mang theo dụng cụ gì khi tới hiện trường?
  | choices = Kính lúp, Đèn pin, Súng, Găng tay cao su
  | multiple = true
)

hanh_dong_dau_tien* = SelectBox(
  | question = Cánh cửa căn nhà gỗ đang hé mở. Bạn làm gì?
  | choices = Đạp cửa xông vào, Gõ cửa cẩn thận, Nhìn qua khe cửa
)

::: [{$ do_nghe hanh_dong_dau_tien $}]
{% if ((hanh_dong_dau_tien == "Đạp cửa xông vào") and do_nghe contains "Súng") %}
rut_sung* = SelectBox(
  | question = Bên trong tối om, có tiếng động lạ! Bạn có rút súng không?
  | choices = Có, Không
)
{% endif %}
:::

::: [{$ do_nghe hanh_dong_dau_tien $}]
{% if ((hanh_dong_dau_tien == "Đạp cửa xông vào") and not (do_nghe contains "Súng")) %}
bi_tan_cong* = SelectBox(
  | question = Bạn không có súng! Một bóng đen lao ra. Bạn làm gì?
  | choices = Đỡ đòn, Bỏ chạy
)
{% endif %}
:::

::: [{$ do_nghe hanh_dong_dau_tien $}]
{% if ((hanh_dong_dau_tien == "Nhìn qua khe cửa") and do_nghe contains "Kính lúp") %}
quan_sat_bang_kinh* = SelectBox(
  | question = Dùng kính lúp, bạn phát hiện một vết máu nhỏ trên nắm đấm cửa. Bạn:
  | choices = Lấy mẫu máu, Bỏ qua, đi tiếp
)
{% endif %}
:::

::: [{$ bi_tan_cong hanh_dong_dau_tien quan_sat_bang_kinh rut_sung $}]
{% if ((bi_tan_cong == "Đỡ đòn") or (rut_sung == "Có") or (quan_sat_bang_kinh == "Lấy mẫu máu") or (hanh_dong_dau_tien == "Gõ cửa cẩn thận")) %}
ket_luan* = SelectBox(
  | question = Tóm lại, bạn nghĩ thủ phạm là ai?
  | choices = Người hầu, Quản gia, Một con thú hoang, Chưa đủ bằng chứng
)
{% endif %}
:::
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
