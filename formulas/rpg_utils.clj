(ns rpg-utils)

(defn tinh-diem [dung-cu tiep-can kham-nghiem kiem-tra phong-van thu-pham]
  (let [dung-cu-set (set dung-cu)
        ;; 1. Điểm chuẩn bị dụng cụ (tối đa 40 điểm)
        pts-dung-cu (+ (if (contains? dung-cu-set "Kính lúp") 10 0)
                       (if (contains? dung-cu-set "Đèn pin") 10 0)
                       (if (contains? dung-cu-set "Bộ tăm bông lấy mẫu") 10 0)
                       (if (contains? dung-cu-set "Sổ tay thám tử") 10 0))
                       
        ;; 2. Điểm khám phá manh mối tại hiện trường (tối đa 30 điểm)
        pts-manh-moi (cond
                       ;; Hướng phòng ngủ
                       (and (= tiep-can "Khám nghiệm phòng ngủ trước")
                            (= kham-nghiem "Kiểm tra ly rượu uống dở cạnh giường")
                            (contains? dung-cu-set "Đèn pin")) 30
                       
                       (and (= tiep-can "Khám nghiệm phòng ngủ trước")
                            (= kham-nghiem "Lấy mẫu chất dịch trên môi nạn nhân")
                            (contains? dung-cu-set "Bộ tăm bông lấy mẫu")) 30
                            
                       ;; Hướng thư phòng
                       (and (= tiep-can "Khám nghiệm thư phòng trước")
                            (= kiem-tra "Kiểm tra lò sưởi có tàn tro giấy đốt")
                            (contains? dung-cu-set "Đèn pin")) 30
                            
                       ;; Hướng phỏng vấn
                       (and (= tiep-can "Phỏng vấn các nghi phạm ngay lập tức")
                            (= phong-van "Hỏi Isabella về bức thư nợ tiền")
                            (contains? dung-cu-set "Sổ tay thám tử")) 30
                            
                       :else 0)
                       
        ;; 3. Điểm kết luận thủ phạm (tối đa 40 điểm)
        pts-thu-pham (cond
                       (= thu-pham "Cả Quản gia Alfred và Isabella đồng phạm") 40
                       (or (= thu-pham "Quản gia Alfred") (= thu-pham "Isabella")) 20
                       :else 0)]
    (+ pts-dung-cu pts-manh-moi pts-thu-pham)))

(defn danh-gia [score]
  (cond
    (>= score 100) (str "Thám tử lừng danh (Hạng S) - Điểm số: " score "/110. Bạn có óc quan sát siêu phàm, lập luận sắc bén và đã vạch trần vụ án mạng hoàn hảo!")
    (>= score 70)  (str "Thám tử chuyên nghiệp (Hạng A) - Điểm số: " score "/110. Bạn phá án thành công nhưng bỏ sót một vài chi tiết nhỏ tại hiện trường.")
    (>= score 40)  (str "Thám tử nghiệp dư (Hạng B) - Điểm số: " score "/110. Manh mối thu thập chưa đủ thuyết phục dù bạn đã đoán đúng thủ phạm.")
    :else          (str "Người qua đường hiếu kỳ (Hạng F) - Điểm số: " score "/110. Bạn đã bị nghi phạm gài bẫy hoàn toàn và bắt sai người! Vụ án đi vào ngõ cụt.")))
