#! id = loan_approval

-> start
# Hệ Thống Thẩm Định Vay Vốn Đa Tầng
Ứng dụng chấm điểm tín dụng động với nhiều giai đoạn thẩm định, sử dụng biến ẩn và vòng lặp tự động.

---
{% set global_credit_score = 100 %}
{% set global_total_assets = 0 %}
{% set loan_status = "Chờ thẩm định" %}

:::
<p class="text-muted">📢 ▶ GIAI ĐOẠN 1: Thu thập thông tin Khách hàng & Đánh giá Rủi ro sơ bộ</p>
:::

:::[{$ local_risk_points $}]
{% if (local_risk_points >= 50) %}
giai_trinh_rui_ro* = TextInput(
  | question = ⚠️ CẢNH BÁO RỦI RO CAO: Điểm rủi ro của bạn đã vượt quá giới hạn. Vui lòng giải trình ngắn gọn lý do có nợ xấu trước đây:
)
{% endif %}
:::


ho_ten* = TextInput(
  | question = 1. Họ và tên khách hàng
)


ngay_sinh* = DateInput(
  | question = 2. Ngày sinh
)


thu_nhap_thang* = NumberInput(
  | question = 3. Thu nhập trung bình hàng tháng (VNĐ)
)


lich_su_no_xau* = SelectBox(
  | question = 4. Bạn có từng bị nợ xấu (CIC nhóm 3 trở lên) trong 5 năm qua không?
  | choices = Không, tôi luôn trả đúng hạn, Có, tôi từng bị nợ xấu
)
:::[{$ lich_su_no_xau $}]
{% set global_credit_score = ((lich_su_no_xau == "Có, tôi từng bị nợ xấu") ? (global_credit_score - 50) : global_credit_score) %}
:::


:::[{$ lich_su_no_xau $}]
{% set local_risk_points = ((lich_su_no_xau == "Có, tôi từng bị nợ xấu") ? 50 : 0) %}
:::


loai_hinh_cong_viec* = SelectBox(
  | question = 5. Bạn đang làm công việc gì?
  | choices = Nhân viên văn phòng, Tự doanh/Kinh doanh tự do, Khác
)
:::[{$ loai_hinh_cong_viec $}]
{% set global_credit_score = ((loai_hinh_cong_viec == "Nhân viên văn phòng") ? (global_credit_score + 20) : global_credit_score) %}
:::


:::
<p class="text-muted">📢 Hoàn tất Giai đoạn 1. Chuyển dữ liệu sang bộ phận Thẩm định Tài sản...</p>
:::

---
:::
<p class="text-muted">📢 ▶ GIAI ĐOẠN 2: Đánh giá Năng lực Tài chính</p>
:::

:::[{$ danh_sach_tai_san $}]
{% if danh_sach_tai_san.includes("Bất động sản") %}
thong_tin_nguoi_dong_so_huu* = TextInput(
  | question = 📝 YÊU CẦU BỔ SUNG: Bạn có khai báo Bất động sản. Vui lòng nhập tên người Đồng sở hữu (nếu có, hoặc nhập 'Không'):
)
{% endif %}
:::


so_tien_muon_vay* = NumberInput(
  | question = 1. Số tiền bạn muốn vay (VNĐ)
)


danh_sach_tai_san* = ChoiceInput(
  | question = 2. Chọn các loại tài sản bạn đang sở hữu (có thể chọn nhiều)
  | choices = Bất động sản, Ô tô, Sổ tiết kiệm, Không có tài sản đảm bảo
  | multiple = true
)


:::[{$ danh_sach_tai_san $}]
{% set local_asset_value = ((danh_sach_tai_san.includes("Bất động sản") ? 1000000000 : 0) + (danh_sach_tai_san.includes("Ô tô") ? 500000000 : 0) + (danh_sach_tai_san.includes("Sổ tiết kiệm") ? 200000000 : 0)) %}
:::


:::
{% set global_total_assets = local_asset_value %}
<p class="text-muted">📢 Đã ghi nhận khối lượng tài sản.</p>
:::

---
:::
<p class="text-muted">📢 ▶ GIAI ĐOẠN 3: Ra Quyết định Phê duyệt</p>
:::

:::[{$ global_credit_score global_total_assets so_tien_muon_vay $}]
{% set local_du_dieu_kien = ((global_credit_score >= 100) && (global_total_assets >= so_tien_muon_vay)) %}
:::


:::[{$ local_du_dieu_kien $}]
{% if local_du_dieu_kien %}
thong_bao_chap_thuan* = TextInput(
  | question = 🎉 CHÚC MỪNG: Hồ sơ của bạn đã đủ điều kiện phê duyệt! (Nhập 'OK' để tiếp tục)
)
{% endif %}
:::
:::[{$ thong_bao_chap_thuan $}]
{% set loan_status = "Đã Phê Duyệt" %}
:::


:::[{$ local_du_dieu_kien $}]
{% if !local_du_dieu_kien %}
thong_bao_tu_choi* = TextInput(
  | question = ❌ RẤT TIẾC: Điểm tín dụng hoặc Tài sản đảm bảo của bạn không đủ đáp ứng. (Nhập 'OK' để đóng)
)
{% endif %}
:::
:::[{$ thong_bao_tu_choi $}]
{% set loan_status = "Bị Từ Chối" %}
:::


:::
<p class="text-muted">📢 Cảm ơn bạn đã sử dụng Hệ thống Thẩm định tự động!</p>
:::

---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
