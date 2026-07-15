(ns loan-utils)

;; Tính điểm tín dụng tổng hợp từ 4 chỉ số
(defn tinh-diem-tin-dung [thu-nhap no-hien-tai lich-su-tra-no tai-san]
  (let [pts-thu-nhap (cond (>= thu-nhap 50) 40
                           (>= thu-nhap 20) 25
                           (>= thu-nhap 10) 10
                           :else 0)
        ty-le-no     (if (pos? thu-nhap) (/ no-hien-tai thu-nhap) 10)
        pts-no       (cond (<= ty-le-no 0.3) 30
                           (<= ty-le-no 0.5) 15
                           :else 0)
        pts-lichsu   (cond (= lich-su-tra-no "Tốt") 20
                           (= lich-su-tra-no "Trung bình") 8
                           :else 0)
        pts-taisan   (cond (>= tai-san 500)  10
                           (>= tai-san 100)  5
                           :else 0)]
    (+ pts-thu-nhap pts-no pts-lichsu pts-taisan)))

;; Tính mức vay tối đa (triệu đồng)
(defn tinh-muc-vay-toi-da [diem-tin-dung thu-nhap]
  (let [he-so (cond (>= diem-tin-dung 80) 60
                    (>= diem-tin-dung 60) 36
                    (>= diem-tin-dung 40) 12
                    :else 0)]
    (* thu-nhap he-so)))

;; Tính lãi suất đề nghị (% năm)
(defn tinh-lai-suat [diem-tin-dung ky-han-nam]
  (let [base (cond (>= diem-tin-dung 80) 6.5
                   (>= diem-tin-dung 60) 8.0
                   (>= diem-tin-dung 40) 10.5
                   :else 14.0)
        phu-phi (if (> ky-han-nam 5) 0.5 0.0)]
    (+ base phu-phi)))

;; Xếp loại hồ sơ cuối cùng
(defn xep-loai-ho-so [diem muc-vay-toi-da so-tien-vay lai-suat]
  (cond
    (< diem 40)
    (str "❌ TỪ CHỐI - Điểm tín dụng quá thấp (" diem "/100). Vui lòng cải thiện lịch sử tín dụng.")
    
    (> so-tien-vay muc-vay-toi-da)
    (str "⚠️ ĐIỀU CHỈNH - Hạn mức tối đa: " muc-vay-toi-da " triệu. Số tiền yêu cầu vượt hạn mức.")
    
    (>= diem 80)
    (str "✅ PHÊ DUYỆT NHANH - Điểm: " diem "/100. Lãi suất ưu đãi " lai-suat "%. Hạn mức: " muc-vay-toi-da " triệu.")
    
    :else
    (str "✅ PHÊ DUYỆT THƯỜNG - Điểm: " diem "/100. Lãi suất " lai-suat "%. Hạn mức: " muc-vay-toi-da " triệu.")))
