#! id = bayesian_recruitment

-> start
# Bayesian Recruitment Engine (Phase 6)
Sử dụng Backend Stan để dự đoán khả năng trúng tuyển của ứng viên kèm theo mức độ bất định (uncertainty).
---

{% set stan_result = {} %}

experience* = NumberInput(
  | question = Số năm kinh nghiệm làm việc?
)


test_score* = NumberInput(
  | question = Điểm bài test kỹ năng (1-10)
)
::: [{$ test_score $}]
{% set stan_result = stan.run_stan_model(experience, test_score) %}
:::


::: [{$ stan_result $}]
{% if (stan_result.'prob' < 50) %}
⚠️ CẢNH BÁO CAO: Khả năng ứng viên này phù hợp với công ty chỉ là {{ stan_result.'prob' }}%. (Mức độ tin cậy {{ stan_result.'confidence' }}: dao động từ {{ stan_result.'lower_bound' }}% đến {{ stan_result.'upper_bound' }}%)
{% else %}
✅ TIỀM NĂNG: Xác suất ứng viên này thành công là {{ stan_result.'prob' }}%. (Mức độ tin cậy {{ stan_result.'confidence' }}: dao động từ {{ stan_result.'lower_bound' }}% đến {{ stan_result.'upper_bound' }}%)
{% endif %}
:::


---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
