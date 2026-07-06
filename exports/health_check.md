#! id = health_check

-> start -> Bắt đầu
# Kiểm tra rủi ro tim mạch
Form mẫu minh hoạ tính năng Import Formula.
---

:::
{% set risk_score = 0.0 %}{{ setVal("risk_score", risk_score) }}
{% set risk_category = 'unknown' %}{{ setVal("risk_category", risk_category) }}
:::

ho_ten* = TextInput(
  | question = Họ và tên
)

tuoi* = NumberInput(
  | question = Tuổi
)

gioi_tinh* = SelectBox(
  | question = Giới tính
  | options = Nam, Nữ
)

bmi* = NumberInput(
  | question = Chỉ số BMI
)

hut_thuoc* = SelectBox(
  | question = Bạn có hút thuốc không?
  | options = Có, Không
)

hdl* = NumberInput(
  | question = Chỉ số HDL (cholesterol tốt)
)
::: [{$ hdl $}]
{% set risk_score = cardio_risk.calc_risk(tuoi, gioi_tinh, bmi, (true if hut_thuoc == "Có" else false), hdl) %}{{ setVal("risk_score", risk_score) }}
{% set risk_category = cardio_risk.risk_category(risk_score) %}{{ setVal("risk_category", risk_category) }}
:::

::: [{$ risk_category $}]
{% if (risk_category == 'high' or risk_category == 'very-high') %}
thong_bao_nguy_hiem = TextInput(
  | question = ⚠️ CẢNH BÁO: Rủi ro tim mạch của bạn ở mức CAO. Hãy đến bác sĩ!
)
{% endif %}
:::

::: [{$ risk_category $}]
{% if risk_category == 'low' %}
thong_bao_an_toan = TextInput(
  | question = ✅ Tốt lắm: Rủi ro tim mạch của bạn ở mức THẤP.
)
{% endif %}
:::
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
