#! id = loan_approval

-> start -> Bắt đầu
# Hệ Thống Thẩm Định Vay Vốn Đa Tầng
Ứng dụng chấm điểm tín dụng động với nhiều giai đoạn thẩm định, sử dụng biến ẩn và vòng lặp tự động.
---

:::
{% set global_credit_score = 100 %}{{ setVal("global_credit_score", global_credit_score) }}
{% set global_total_assets = 0 %}{{ setVal("global_total_assets", global_total_assets) }}
{% set loan_status = "Chờ thẩm định" %}{{ setVal("loan_status", loan_status) }}
:::

:::
<p class="text-muted">📢 ▶ GIAI ĐOẠN 1: Thu thập thông tin Khách hàng & Đánh giá Rủi ro sơ bộ</p>
:::
::: [{$ local_risk_points $}]
{% if local_risk_points >= 50 %}
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
  | options = Không, tôi luôn trả đúng hạn, Có, tôi từng bị nợ xấu
)
::: [{$ lich_su_no_xau $}]
{% set global_credit_score = ((global_credit_score - 50) if lich_su_no_xau == "Có, tôi từng bị nợ xấu" else global_credit_score) %}{{ setVal("global_credit_score", global_credit_score) }}
:::

::: [{$ lich_su_no_xau $}]
{% set local_risk_points = (50 if lich_su_no_xau == "Có, tôi từng bị nợ xấu" else 0) %}{{ setVal("local_risk_points", local_risk_points) }}
:::

loai_hinh_cong_viec* = SelectBox(
  | question = 5. Bạn đang làm công việc gì?
  | options = Nhân viên văn phòng, Tự doanh/Kinh doanh tự do, Khác
)
::: [{$ loai_hinh_cong_viec $}]
{% set global_credit_score = ((global_credit_score + 20) if loai_hinh_cong_viec == "Nhân viên văn phòng" else global_credit_score) %}{{ setVal("global_credit_score", global_credit_score) }}
:::
::: [{$ giai_trinh_rui_ro ho_ten lich_su_no_xau loai_hinh_cong_viec local_risk_points ngay_sinh thu_nhap_thang $}]
{% if (not (local_risk_points >= 50) or giai_trinh_rui_ro) and ho_ten and ngay_sinh and thu_nhap_thang and lich_su_no_xau and loai_hinh_cong_viec %}
<p class="text-muted">📢 Hoàn tất Giai đoạn 1. Chuyển dữ liệu sang bộ phận Thẩm định Tài sản...</p>
{% endif %}
:::
---

:::
<p class="text-muted">📢 ▶ GIAI ĐOẠN 2: Đánh giá Năng lực Tài chính</p>
:::
::: [{$ danh_sach_tai_san $}]
{% if (danh_sach_tai_san and ("Bất động sản" in danh_sach_tai_san)) %}
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

::: [{$ danh_sach_tai_san $}]
{% set local_asset_value = ((1000000000 if (danh_sach_tai_san and ("Bất động sản" in danh_sach_tai_san)) else 0) + (500000000 if (danh_sach_tai_san and ("Ô tô" in danh_sach_tai_san)) else 0) + (200000000 if (danh_sach_tai_san and ("Sổ tiết kiệm" in danh_sach_tai_san)) else 0)) %}{{ setVal("local_asset_value", local_asset_value) }}
:::
::: [{$ danh_sach_tai_san local_asset_value so_tien_muon_vay thong_tin_nguoi_dong_so_huu $}]
{% if (not ((danh_sach_tai_san and ("Bất động sản" in danh_sach_tai_san))) or thong_tin_nguoi_dong_so_huu) and so_tien_muon_vay and danh_sach_tai_san %}
{% set global_total_assets = local_asset_value %}{{ setVal("global_total_assets", global_total_assets) }}
<p class="text-muted">📢 Đã ghi nhận khối lượng tài sản.</p>
{% endif %}
:::
---

:::
<p class="text-muted">📢 ▶ GIAI ĐOẠN 3: Ra Quyết định Phê duyệt</p>
:::
::: [{$ global_credit_score global_total_assets so_tien_muon_vay $}]
{% set local_du_dieu_kien = (global_credit_score >= 100 and global_total_assets >= so_tien_muon_vay) %}{{ setVal("local_du_dieu_kien", local_du_dieu_kien) }}
:::

::: [{$ local_du_dieu_kien $}]
{% if local_du_dieu_kien %}
thong_bao_chap_thuan* = TextInput(
  | question = 🎉 CHÚC MỪNG: Hồ sơ của bạn đã đủ điều kiện phê duyệt! (Nhập 'OK' để tiếp tục)
)
{% endif %}
:::
::: [{$ thong_bao_chap_thuan $}]
{% set loan_status = "Đã Phê Duyệt" %}{{ setVal("loan_status", loan_status) }}
:::

::: [{$ local_du_dieu_kien $}]
{% if not (local_du_dieu_kien) %}
thong_bao_tu_choi* = TextInput(
  | question = ❌ RẤT TIẾC: Điểm tín dụng hoặc Tài sản đảm bảo của bạn không đủ đáp ứng. (Nhập 'OK' để đóng)
)
{% endif %}
:::
::: [{$ thong_bao_tu_choi $}]
{% set loan_status = "Bị Từ Chối" %}{{ setVal("loan_status", loan_status) }}
:::
::: [{$ local_du_dieu_kien thong_bao_chap_thuan thong_bao_tu_choi $}]
{% if (not (local_du_dieu_kien) or thong_bao_chap_thuan) and (not (not (local_du_dieu_kien)) or thong_bao_tu_choi) %}
<p class="text-muted">📢 Cảm ơn bạn đã sử dụng Hệ thống Thẩm định tự động!</p>
{% endif %}
:::
---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
