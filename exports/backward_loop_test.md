#! id = backward_loop_test

-> start -> Bắt đầu
# Backward Loop Test
Kiểm thử cơ chế Restarting Loop với câu hỏi phụ thuộc ngược (Backward Dependency)
---

::: [{$ tham_gia $}]
{% if tham_gia == "Không" %}
ly_do_tu_choi* = TextInput(
  | question = Vì sao bạn từ chối tham gia?
)
{% endif %}
:::

ho_ten* = TextInput(
  | question = Họ và tên
)

tham_gia* = SelectBox(
  | question = Bạn có muốn tham gia sự kiện không?
  | options = Có, Không
)
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
