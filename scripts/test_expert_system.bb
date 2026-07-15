#!/usr/bin/env bb

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[babashka.process :refer [shell]]
         '[clojure.test :refer [deftest is run-tests]])

(import '[java.time LocalDateTime]
        '[java.time.format DateTimeFormatter])

(println "🏥 Khởi tạo test suite cho Hệ chuyên gia (Expert System)...")

;; Tạo thư mục test_data nếu chưa có
(.mkdirs (io/file "scripts/test_data"))

(deftest test-ngoi-ngang
  (println "\n🧪 Chạy Testcase 1: Ngôi thai Ngang -> Quyết định mổ khẩn cấp lập tức...")
  (spit "scripts/test_data/ngoi_ngang.edn" (pr-str {:ngoi_thai "Ngang"}))
  
  (shell "bb" "-m" "com.drbinhthanh.bb-form" "forms/sanh-expert.edn"
         "--marathon"
         "--values" "scripts/test_data/ngoi_ngang.edn"
         "--out" "scripts/test_data/result_ngoi_ngang.edn")
  
  (let [result (edn/read-string (slurp "scripts/test_data/result_ngoi_ngang.edn"))
        answers (:selectedByUser result)]
    (is (= (:ngoi_thai answers) "Ngang"))
    (is (= (:ket_luan answers) "Chỉ định mổ lấy thai khẩn cấp (Ngôi ngang không thể sinh thường)"))
    ;; Đảm bảo solver tự động cắt nhánh (không hỏi các câu hỏi không liên quan)
    (is (nil? (:khung_chau answers)))
    (is (nil? (:nuoc_oi answers)))
    (println "✅ Testcase 1 ĐẠT!")))

(deftest test-khung-chau-hep
  (println "\n🧪 Chạy Testcase 2: Khung chậu Hẹp -> Chỉ định mổ lấy thai...")
  (spit "scripts/test_data/khung_chau_hep.edn" (pr-str {:ngoi_thai "Đầu" :khung_chau "Hẹp"}))
  
  (shell "bb" "-m" "com.drbinhthanh.bb-form" "forms/sanh-expert.edn"
         "--marathon"
         "--values" "scripts/test_data/khung_chau_hep.edn"
         "--out" "scripts/test_data/result_khung_chau_hep.edn")
  
  (let [result (edn/read-string (slurp "scripts/test_data/result_khung_chau_hep.edn"))
        answers (:selectedByUser result)]
    (is (= (:ngoi_thai answers) "Đầu"))
    (is (= (:khung_chau answers) "Hẹp"))
    (is (= (:ket_luan answers) "Chỉ định mổ lấy thai (Bất tương xứng đầu chậu do khung chậu hẹp)"))
    (println "✅ Testcase 2 ĐẠT!")))

(deftest test-oi-vo-lau-nhiem-trung
  (println "\n🧪 Chạy Testcase 3: Ối vỡ kéo dài (> 12 giờ) -> Nguy cơ nhiễm trùng và chỉ định mổ...")
  ;; Lấy thời gian hiện tại lùi lại 15 tiếng để kiểm thử
  (let [formatter (DateTimeFormatter/ofPattern "dd-MM-yyyy HH:mm")
        time-15h-ago (.minusHours (LocalDateTime/now) 15)
        formatted-time (.format time-15h-ago formatter)]
    (spit "scripts/test_data/oi_vo_lau.edn"
          (pr-str {:ngoi_thai "Đầu"
                   :nuoc_oi "Đã vỡ"
                   :gio_vo_oi formatted-time}))
    
    (shell "bb" "-m" "com.drbinhthanh.bb-form" "forms/sanh-expert.edn"
           "--marathon"
           "--values" "scripts/test_data/oi_vo_lau.edn"
           "--out" "scripts/test_data/result_oi_vo_lau.edn")
    
    (let [result (edn/read-string (slurp "scripts/test_data/result_oi_vo_lau.edn"))
          answers (:selectedByUser result)]
      (is (= (:ngoi_thai answers) "Đầu"))
      (is (= (:nuoc_oi answers) "Đã vỡ"))
      (is (= (:gio_vo_oi answers) formatted-time))
      ;; Kiểm tra xem số giờ vỡ ối có được tính toán đúng không (phải >= 15)
      (is (>= (Integer/parseInt (:so_gio_vo_oi answers)) 15))
      (is (= (:nguy_co_nhiem_trung answers) true))
      (is (= (:ket_luan answers) "Chỉ định mổ lấy thai kết hợp kháng sinh liều cao (Nguy cơ nhiễm trùng do ối vỡ lâu > 12 giờ)"))
      (println "✅ Testcase 3 ĐẠT!"))))

(deftest test-tiep-tuc-theo-doi
  (println "\n🧪 Chạy Testcase 4: Tiếp tục theo dõi chuyển dạ bình thường (Rule 2.7)...")
  (spit "scripts/test_data/tiep_tuc_theo_doi.edn"
        (pr-str {:ngoi_thai "Đầu"
                 :do_mo_ctc "Mở 2cm"
                 :do_xoa_ctc "60%"
                 :nuoc_oi "Còn"
                 :khung_chau "Bình thường"
                 :can_nang_thai 3000}))
  
  (shell "bb" "-m" "com.drbinhthanh.bb-form" "forms/sanh-expert.edn"
         "--marathon"
         "--values" "scripts/test_data/tiep_tuc_theo_doi.edn"
         "--out" "scripts/test_data/result_tiep_tuc_theo_doi.edn")
  
  (let [result (edn/read-string (slurp "scripts/test_data/result_tiep_tuc_theo_doi.edn"))
        answers (:selectedByUser result)]
    (is (= (:ngoi_thai answers) "Đầu"))
    (is (= (:do_mo_ctc answers) "Mở 2cm"))
    (is (= (:do_xoa_ctc answers) "60%"))
    (is (= (:nuoc_oi answers) "Còn"))
    (is (= (:khung_chau answers) "Bình thường"))
    (is (= (:can_nang_thai answers) 3000))
    (is (nil? (:nguy_co_nhiem_trung answers)))
    (is (= (:ket_luan answers) "Tiếp tục theo dõi sát tiến triển của cổ tử cung, độ xóa mở và tim thai"))
    (println "✅ Testcase 4 ĐẠT!")))

;; Chạy các testcase

(let [test-results (run-tests)]
  (if (zero? (+ (:fail test-results) (:error test-results)))
    (do
      (println "\n🎉 TẤT CẢ CÁC BÀI KIỂM THỬ ĐÃ ĐẠT TIÊU CHUẨN!")
      (System/exit 0))
    (do
      (println "\n❌ CÓ BÀI KIỂM THỬ THẤT BẠI!")
      (System/exit 1))))
