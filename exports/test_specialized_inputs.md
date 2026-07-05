#! id = test_specialized_inputs

-> start -> Bắt đầu
# Kiểm thử Specialized Inputs
Minh họa trường Email, Tel, URL, Rating trong formsmd sử dụng tham số :form
---

test_email* = EmailInput(
  | question = Địa chỉ Email của bạn
)

test_tel* = TelInput(
  | question = Số điện thoại di động
)

test_url = URLInput(
  | question = Link Github cá nhân
)

test_rating* = RatingInput(
  | question = Đánh giá mức độ hài lòng (1-5 sao)
  | min = 1
  | max = 5
)
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
