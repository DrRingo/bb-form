#! id = job_application

-> start -> Bắt đầu
# Hệ thống Phân Tích Kỹ Năng Ứng Viên (Matrix Score)
Form sử dụng thư viện tính toán ma trận bằng Clojure (.clj) để phân loại ứng viên.
---

:::
{% set analysis_result = {} %}
{% set recommended_role = "Đang phân tích" %}
{% set best_score = 0 %}
:::

ho_ten* = TextInput(
  | question = Họ và tên ứng viên
)

ui_score* = NumberInput(
  | question = 1. Tự đánh giá kỹ năng UI/UX (0-10)
)

sys_score* = NumberInput(
  | question = 2. Tự đánh giá kỹ năng System Design (0-10)
)

algo_score* = NumberInput(
  | question = 3. Tự đánh giá kỹ năng Thuật Toán (0-10)
)

devops_score* = NumberInput(
  | question = 4. Tự đánh giá kỹ năng DevOps/Deployment (0-10)
)
::: [{$ devops_score $}]
{% set analysis_result = score.analyze_candidate(ui_score, sys_score, algo_score, devops_score) %}
{% set recommended_role = score.get_recommended_role(analysis_result) %}
{% set best_score = score.get_best_score(analysis_result) %}
:::

::: [{$ best_score $}]
{% if best_score < 50 %}
⚠️ Rất tiếc, điểm cao nhất của bạn quá thấp để phù hợp với bất kỳ vị trí nào.
{% endif %}
:::

::: [{$ best_score $}]
{% if best_score >= 50 %}
🎉 CHÚC MỪNG! Dựa trên ma trận năng lực, vị trí phù hợp nhất với bạn là {$ recommended_role $} với số điểm {$ best_score $}/100.
{% endif %}
:::
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
