#! id = test_flow

-> start -> Bắt đầu
# Biểu mẫu kiểm thử tính năng nâng cao formsmd
Minh họa rẽ nhánh cấp độ slide (Stage show-if) và cảnh báo gọi hàm ngoài
---

:::
{% set tuoi = 0 %}
{% set gioi_tinh = "Nam" %}
{% set diem_sk = 0 %}
:::

gioi_tinh* = SelectBox(
  | question = Chọn giới tính của bạn
  | options = Nam, Nữ
)

tuoi* = NumberInput(
  | question = Nhập tuổi của bạn
)
---

-> (gioi_tinh == "Nữ" and tuoi >= 18)

thong_tin_phu_nu* = TextInput(
  | question = Thông tin bổ sung dành cho Nữ trưởng thành
)
---

::: [{$ gioi_tinh tuoi $}]
{% set diem_sk_hidden = cardio_risk.calc_risk(tuoi, 22, gioi_tinh) %}
:::
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
