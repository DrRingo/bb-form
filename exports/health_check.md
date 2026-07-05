#! id = health_check

-> start -> Bắt đầu
# Kiểm tra rủi ro tim mạch
Form mẫu minh hoạ tính năng Import Formula.
---

:::
{% set risk_score = 0.0 %}
{% set risk_category = 'unknown' %}
:::

ho_ten* = TextInput(
  | question = Họ và tên
)

tuoi* = NumberInput(
  | question = Tuổi
)

gioi_tinh* = SelectBox(
  | question = Giới tính
  | choices = Nam, Nữ
)

bmi* = NumberInput(
  | question = Chỉ số BMI
)

hut_thuoc* = SelectBox(
  | question = Bạn có hút thuốc không?
  | choices = Có, Không
)

hdl* = NumberInput(
  | question = Chỉ số HDL (cholesterol tốt)
)
::: [{$ hdl $}]
{% set risk_score = cardio_risk.calc_risk(tuoi, gioi_tinh, bmi, ((hut_thuoc == "Có") ? true : false), hdl) %}
{% set risk_category = cardio_risk.risk_category(risk_score) %}
:::

::: [{$ risk_category $}]
{% if ((risk_category == 'high') or (risk_category == 'very-high')) %}
thong_bao_nguy_hiem = TextInput(
  | question = ⚠️ CẢNH BÁO: Rủi ro tim mạch của bạn ở mức CAO. Hãy đến bác sĩ!
)
{% endif %}
:::

::: [{$ risk_category $}]
{% if (risk_category == 'low') %}
thong_bao_an_toan = TextInput(
  | question = ✅ Tốt lắm: Rủi ro tim mạch của bạn ở mức THẤP.
)
{% endif %}
:::
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
