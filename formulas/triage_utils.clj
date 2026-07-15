(ns triage-utils)

;; Tính chỉ số NEWS2 (National Early Warning Score)
;; Trả về tổng điểm nguy cơ từ 0-20
(defn tinh-news2 [nhip-tho nhip-tim huyet-ap-tam-thu do-bao-hoa-oxy nhiet-do y-thuc]
  (let [pts-nhip-tho  (cond (<= nhip-tho 8) 3
                            (<= nhip-tho 11) 1
                            (<= nhip-tho 20) 0
                            (<= nhip-tho 24) 2
                            :else 3)
        pts-nhip-tim  (cond (<= nhip-tim 40) 3
                            (<= nhip-tim 50) 1
                            (<= nhip-tim 90) 0
                            (<= nhip-tim 110) 1
                            (<= nhip-tim 130) 2
                            :else 3)
        pts-ha-tthu   (cond (<= huyet-ap-tam-thu 90) 3
                            (<= huyet-ap-tam-thu 100) 2
                            (<= huyet-ap-tam-thu 110) 1
                            (<= huyet-ap-tam-thu 219) 0
                            :else 3)
        pts-spo2      (cond (<= do-bao-hoa-oxy 91) 3
                            (<= do-bao-hoa-oxy 93) 2
                            (<= do-bao-hoa-oxy 95) 1
                            :else 0)
        pts-nhiet-do  (cond (<= nhiet-do 35.0) 3
                            (<= nhiet-do 36.0) 1
                            (<= nhiet-do 38.0) 0
                            (<= nhiet-do 39.0) 1
                            :else 2)
        pts-y-thuc    (if (= y-thuc "Tỉnh táo") 0 3)]
    (+ pts-nhip-tho pts-nhip-tim pts-ha-tthu pts-spo2 pts-nhiet-do pts-y-thuc)))

;; Phân loại mức độ cấp cứu từ điểm NEWS2
(defn phan-loai-cap-cuu [score]
  (cond
    (>= score 7) "ĐỎ"      ; Cấp cứu hồi sức ngay lập tức
    (>= score 5) "CAM"     ; Can thiệp khẩn cấp trong 15 phút
    (>= score 3) "VÀNG"    ; Theo dõi mỗi giờ
    :else        "XANH"))  ; Theo dõi thường quy

;; Tạo kết luận lâm sàng đầy đủ
(defn ket-luan-cap-cuu [score ten-bn tuoi phan-loai chan-doan-so-bo]
  (str "[" phan-loai "] Bệnh nhân " ten-bn " (" tuoi " tuổi) — NEWS2: " score "/20\n"
       "Chan đoán sơ bộ: " chan-doan-so-bo "\n"
       (cond
         (= phan-loai "ĐỎ")   "→ Kích hoạt đội hồi sức. Nhập ICU ngay. Báo trưởng ca."
         (= phan-loai "CAM")  "→ Đánh giá lại sau 15 phút. Xét nghiệm cấp cứu toàn phần."
         (= phan-loai "VÀNG") "→ Theo dõi sinh hiệu mỗi giờ. Bác sĩ đánh giá lại sau 4h."
         :else                 "→ Nhập phòng thường. Theo dõi theo phác đồ thường quy.")))
