#! id = stage_demo

-> start -> Bắt đầu
# Hệ thống Xét Duyệt Hồ Sơ (Đa Vòng)
Minh hoạ tính năng Stages, Hidden Variables và Actions
---

:::
{% set diem_tong = 0 %}
{% set trang_thai = "Đang xét duyệt" %}
:::

:::
<p class="text-muted">📢 Chào mừng bạn đến với VÒNG 1: Đánh giá Kinh Nghiệm!</p>
:::
ho_ten* = TextInput(
  | question = Họ và tên ứng viên
)

kinh_nghiem* = NumberInput(
  | question = Số năm kinh nghiệm làm việc
)

::: [{$ kinh_nghiem $}]
{% set diem_kinh_nghiem = (kinh_nghiem * 10) %}
:::

::: [{$ diem_kinh_nghiem $}]
{% if (diem_kinh_nghiem >= 50) %}
cau_hoi_phu_chuyen_gia* = TextInput(
  | question = Bạn có kinh nghiệm > 50 điểm. Hãy kể tên 1 dự án lớn nhất bạn từng làm?
)
{% endif %}
:::
:::
{% set diem_tong = (diem_tong + diem_kinh_nghiem) %}
<p class="text-muted">📢 Đã cộng điểm Vòng 1 vào Tổng điểm!</p>
:::
---

:::
<p class="text-muted">📢 Bạn đã bước sang VÒNG 2: Bài test Chuyên môn.</p>
:::
cau_hoi_thu_thach* = SelectBox(
  | question = Ngôn ngữ nào chạy trên JVM mà không phải Java?
  | choices = Python, Clojure, Go, Rust
)
::: [{$ cau_hoi_thu_thach $}]
{% set diem_tong = (diem_tong + ((cau_hoi_thu_thach == "Clojure") ? 50 : 0)) %}
:::

::: [{$ diem_tong $}]
{% if (diem_tong < 100) %}
thong_bao_rot* = SelectBox(
  | question = ⚠️ Rất tiếc, Tổng điểm của bạn quá thấp để đậu vòng này.
  | choices = Tôi đồng ý
)
{% endif %}
:::
:::
{% set trang_thai = ((diem_tong >= 100) ? "Đã Đậu" : "Đã Rớt") %}
<p class="text-muted">📢 Hoàn tất đánh giá Vòng 2!</p>
:::
---
-> end
# Cảm ơn bạn!
Tổng điểm của bạn: **{$ diem_tong $}**
Trạng thái: **{$ trang_thai $}**
Thông tin đăng ký của bạn đã được ghi nhận.
