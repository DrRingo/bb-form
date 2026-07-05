#! id = event_registration

-> start
# Đăng Ký Sự Kiện
Đăng ký tham dự hội thảo Clojure & Babashka 2026

---
ho_ten* = TextInput(
  | question = Họ và tên
)


email* = TextInput(
  | question = Email liên lạc
  | pattern = ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$
)


so_dien_thoai = TextInput(
  | question = Số điện thoại
  | pattern = ^[0-9]{10,11}$
)


loai_ve* = SelectBox(
  | question = Loại vé đăng ký
  | choices = Tham dự trực tiếp, Xem trực tuyến, Chỉ nhận tài liệu
)


:::[{$ loai_ve $}]
{% if (loai_ve == "Tham dự trực tiếp") %}
che_do_an* = SelectBox(
  | question = Chế độ ăn
  | choices = Bình thường, Chay, Không ăn
)
{% endif %}
:::


:::[{$ loai_ve $}]
{% if (loai_ve == "Tham dự trực tiếp") %}
can_xe_dua_don* = SelectBox(
  | question = Cần hỗ trợ xe đưa đón?
  | choices = Có, Không
)
{% endif %}
:::


:::[{$ can_xe_dua_don loai_ve $}]
{% if ((loai_ve == "Tham dự trực tiếp") && (can_xe_dua_don == "Có")) %}
dia_chi_don* = TextInput(
  | question = Địa chỉ cần đón
)
{% endif %}
:::


chu_de_quan_tam = ChoiceInput(
  | question = Chủ đề quan tâm (chọn nhiều)
  | choices = Clojure cơ bản, Babashka scripting, ClojureScript, EDN & Data formats, Functional Programming, Datomic
  | multiple = true
)


ngay_tham_du* = DateInput(
  | question = Ngày tham dự
)


cau_hoi_truoc = TextInput(
  | question = Câu hỏi muốn gửi tới diễn giả (tùy chọn)
)


---
-> end
# Cảm ơn bạn!
Thông tin đăng ký của bạn đã được ghi nhận.
