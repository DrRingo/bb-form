#! id = test_datetime

-> start -> Bắt đầu
# Khảo Sát Thời Gian (Test Datetime & Time)
Biểu mẫu dùng để kiểm thử tính năng nhập ngày, giờ, ngày giờ và các ký hiệu viết tắt.
---

ngay_sinh* = DateInput(
  | question = 1. Nhập ngày sinh của bạn (date)
)

thoi_gian_ruong* = DatetimeInput(
  | question = 2. Thời gian bạn đi ngủ tối qua (datetime)
)

gio_trua* = TimeInput(
  | question = 3. Giờ nghỉ trưa mong muốn (time)
)
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
