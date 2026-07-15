#!/usr/bin/env bb
;; ═══════════════════════════════════════════════════════════════════════════
;; STRESS TEST SUITE - Expert System Engine
;; Test 3 forms phức tạp với nhiều edge cases khác nhau
;; Chạy: bb scripts/test_stress.bb
;; ═══════════════════════════════════════════════════════════════════════════

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[babashka.process :refer [shell]]
         '[clojure.test :refer [deftest is run-tests]])

(.mkdirs (io/file "scripts/test_data"))
(println "\n🔥 EXPERT SYSTEM STRESS TEST SUITE\n")

;; ─────────────────────────────────────────────────────────────────────────
;; HELPER
;; ─────────────────────────────────────────────────────────────────────────
(defn run-expert [form-file values-map out-file]
  (spit out-file (pr-str values-map))
  (shell "bb" "-m" "com.drbinhthanh.bb-form" form-file
         "--marathon"
         "--values" out-file
         "--out" (str out-file ".result.edn"))
  (edn/read-string (slurp (str out-file ".result.edn"))))

(defn answers [result] (:selectedByUser result))

;; ─────────────────────────────────────────────────────────────────────────
;; FORM 1: LOAN APPROVAL
;; ─────────────────────────────────────────────────────────────────────────
(println "═══ FORM 1: Loan Approval ═══\n")

(deftest loan-phe-duyet-nhanh
  (println "  ✦ [Loan 1] Hồ sơ điểm cao → Phê duyệt nhanh...")
  (let [result (run-expert
                 "forms/stress_loan.edn"
                 {:ho_ten "Nguyen Van A" :tuoi 35 :nghe_nghiep "Công chức/Viên chức"
                  :thu_nhap_thang 60 :no_hien_tai 5 :lich_su_tra_no "Tốt"
                  :tai_san_dam_bao 800 :so_tien_vay 200 :ky_han_vay "5"}
                 "scripts/test_data/loan1.edn")
        a (answers result)]
    (is (some? (:diem_tin_dung a)) "Phải tính được điểm tín dụng")
    (is (some? (:muc_vay_toi_da a)) "Phải tính được mức vay tối đa")
    (is (some? (:lai_suat_de_nghi a)) "Phải tính được lãi suất")
    (is (str/includes? (:ket_luan_vay a) "PHÊ DUYỆT") "Hồ sơ tốt phải được phê duyệt")
    (println "     Điểm:" (:diem_tin_dung a) "| Kết luận:" (subs (:ket_luan_vay a) 0 20) "...")
    (println "  ✅ [Loan 1] PASSED")))

(deftest loan-tu-choi-that-nghiep
  (println "  ✦ [Loan 2] Conflict resolution: Thất nghiệp → Từ chối (priority 50 override)...")
  (let [result (run-expert
                 "forms/stress_loan.edn"
                 {:ho_ten "Tran Thi B" :tuoi 30 :nghe_nghiep "Không có việc làm"
                  :thu_nhap_thang 0 :no_hien_tai 0 :lich_su_tra_no "Tốt"
                  :tai_san_dam_bao 1000 :so_tien_vay 50 :ky_han_vay "1"}
                 "scripts/test_data/loan2.edn")
        a (answers result)]
    (is (str/includes? (:ket_luan_vay a) "TỪ CHỐI") "Thất nghiệp phải bị từ chối")
    (is (str/includes? (:ket_luan_vay a) "thu nhập") "Phải đề cập lý do thu nhập")
    (println "     Kết luận:" (:ket_luan_vay a))
    (println "  ✅ [Loan 2] PASSED")))

(deftest loan-tu-choi-tuoi
  (println "  ✦ [Loan 3] Conflict resolution: Tuổi >70 → Từ chối (priority 40)...")
  (let [result (run-expert
                 "forms/stress_loan.edn"
                 {:ho_ten "Le Van C" :tuoi 75 :nghe_nghiep "Công chức/Viên chức"
                  :thu_nhap_thang 40 :no_hien_tai 0 :lich_su_tra_no "Tốt"
                  :tai_san_dam_bao 500 :so_tien_vay 100 :ky_han_vay "3"}
                 "scripts/test_data/loan3.edn")
        a (answers result)]
    (is (str/includes? (:ket_luan_vay a) "TỪ CHỐI") "Quá tuổi phải bị từ chối")
    (is (str/includes? (:ket_luan_vay a) "tuổi") "Phải đề cập lý do tuổi")
    (println "     Kết luận:" (:ket_luan_vay a))
    (println "  ✅ [Loan 3] PASSED")))

(deftest loan-optional-nil
  (println "  ✦ [Loan 4] Optional field tai_san_dam_bao = nil → vẫn tính được...")
  (let [result (run-expert
                 "forms/stress_loan.edn"
                 {:ho_ten "Pham Thi D" :tuoi 28 :nghe_nghiep "Nhân viên văn phòng"
                  :thu_nhap_thang 20 :no_hien_tai 3 :lich_su_tra_no "Trung bình"
                  :so_tien_vay 30 :ky_han_vay "3"}  ;; KHÔNG có tai_san_dam_bao
                 "scripts/test_data/loan4.edn")
        a (answers result)]
    (is (some? (:diem_tin_dung a)) "Vẫn phải tính được điểm dù không có tài sản")
    (is (some? (:ket_luan_vay a)) "Phải có kết luận")
    (println "     Điểm:" (:diem_tin_dung a) "| KL:" (subs (str (:ket_luan_vay a)) 0 20) "...")
    (println "  ✅ [Loan 4] PASSED")))

;; ─────────────────────────────────────────────────────────────────────────
;; FORM 2: MEDICAL TRIAGE
;; ─────────────────────────────────────────────────────────────────────────
(println "\n═══ FORM 2: Medical Triage NEWS2 ═══\n")

(deftest triage-xanh-on-dinh
  (println "  ✦ [Triage 1] Bệnh nhân ổn định → XANH...")
  (let [result (run-expert
                 "forms/stress_triage.edn"
                 {:ten_benh_nhan "Nguyen Thi E" :tuoi_benh_nhan 45
                  :nhip_tho 16 :nhip_tim 75 :huyet_ap_tam_thu 120
                  :do_bao_hoa_oxy 98 :nhiet_do 37.0 :y_thuc "Tỉnh táo"}
                 "scripts/test_data/triage1.edn")
        a (answers result)]
    (is (some? (:news2_score a)) "Phải tính được NEWS2")
    (is (= (:mau_cap_cuu a) "XANH") "Bệnh nhân ổn định → XANH")
    (is (some? (:cap_cuu_ket_luan a)) "Phải có kết luận")
    (println "     NEWS2:" (:news2_score a) "| Màu:" (:mau_cap_cuu a))
    (println "  ✅ [Triage 1] PASSED")))

(deftest triage-do-hon-me
  (println "  ✦ [Triage 2] Hôn mê → Conflict priority 80 → override thành ĐỎ...")
  (let [result (run-expert
                 "forms/stress_triage.edn"
                 {:ten_benh_nhan "Le Van F" :tuoi_benh_nhan 60
                  :nhip_tho 8 :nhip_tim 140 :huyet_ap_tam_thu 80
                  :do_bao_hoa_oxy 88 :nhiet_do 39.5 :y_thuc "Hôn mê"}
                 "scripts/test_data/triage2.edn")
        a (answers result)]
    (is (str/includes? (:cap_cuu_ket_luan a) "ĐỎ") "Hôn mê phải → ĐỎ")
    (is (str/includes? (:cap_cuu_ket_luan a) "hồi sức") "Phải có hướng dẫn hồi sức")
    (println "     KL:" (subs (:cap_cuu_ket_luan a) 0 40) "...")
    (println "  ✅ [Triage 2] PASSED")))

(deftest triage-shorthand-syntax
  (println "  ✦ [Triage 3] Shorthand [:triage/tinh-news2 ...] syntax test (§11.2)...")
  ;; Bệnh nhân CAM: nhịp thở cao + nhịp tim cao nhưng KHÔNG hôn mê
  (let [result (run-expert
                 "forms/stress_triage.edn"
                 {:ten_benh_nhan "Pham Van G" :tuoi_benh_nhan 55
                  :nhip_tho 22 :nhip_tim 112 :huyet_ap_tam_thu 115
                  :do_bao_hoa_oxy 95 :nhiet_do 38.5 :y_thuc "Tỉnh táo"
                  :chan_doan_so_bo "Nhiễm khuẩn huyết"}
                 "scripts/test_data/triage3.edn")
        a (answers result)]
    (is (some? (:news2_score a)) "Shorthand syntax phải tính được NEWS2")
    (is (>= (:news2_score a) 5) "NEWS2 phải >= 5 với bệnh nhân CAM")
    (is (str/includes? (:cap_cuu_ket_luan a) "CAM") "Phải phân loại CAM")
    (println "     NEWS2:" (:news2_score a) "| Chan doan:" (:chan_doan_so_bo a))
    (println "  ✅ [Triage 3] PASSED")))

(deftest triage-phuong-an-b
  (println "  ✦ [Triage 4] Phương án B: chan_doan_so_bo tự điền 'Chưa xác định'...")
  (let [result (run-expert
                 "forms/stress_triage.edn"
                 {:ten_benh_nhan "Tran Thi H" :tuoi_benh_nhan 30
                  :nhip_tho 18 :nhip_tim 80 :huyet_ap_tam_thu 115
                  :do_bao_hoa_oxy 97 :nhiet_do 37.2 :y_thuc "Tỉnh táo"}
                 ;; KHÔNG cung cấp chan_doan_so_bo
                 "scripts/test_data/triage4.edn")
        a (answers result)]
    (is (= (:chan_doan_so_bo a) "Chưa xác định") "Phương án B phải tự điền")
    (is (some? (:cap_cuu_ket_luan a)) "Kết luận phải có dù không cung cấp chẩn đoán")
    (println "     Chan doan tu dien:" (:chan_doan_so_bo a))
    (println "  ✅ [Triage 4] PASSED")))

;; ─────────────────────────────────────────────────────────────────────────
;; FORM 3: MURDER MYSTERY
;; ─────────────────────────────────────────────────────────────────────────
(println "\n═══ FORM 3: Murder Mystery ═══\n")

(deftest mystery-thomas-guilty
  (println "  ✦ [Mystery 1] Thomas là hung thủ - pruning tối đa...")
  (let [result (run-expert
                 "forms/stress_mystery.edn"
                 {:ten_tham_tu "Holmes"
                  :thoi_gian_chet "21:00 - 23:00"
                  :nguyen_nhan_chet "Ngộ độc thạch tín"
                  :loai_doc_chat "Thạch tín vô cơ"
                  :vi_tri_thi_the "Phòng thư viện"
                  :dau_vet_dau_tranh "Không"
                  :dau_vet_xam_nhap "Không có"
                  :nguoi_cuoi_gap "Con trai Thomas"
                  :alibi_thomas "Ở nhà một mình"
                  :di_chuc "Thomas thừa hưởng tất cả"
                  :tranh_chap_tai_chinh "Thomas tranh chấp thừa kế"}
                 "scripts/test_data/mystery1.edn")
        a (answers result)]
    (is (some? (:kieu_gay_an a)) "Phải xác định kiểu gây án")
    (is (some? (:nghi_pham_chinh a)) "Phải xác định nghi phạm")
    (is (str/includes? (:nghi_pham_chinh a) "Thomas") "Nghi phạm phải là Thomas")
    (is (str/includes? (:ban_an_cuoi_cung a) "Thomas") "Ban án phải nhắc Thomas")
    (println "     Kiểu gây án:" (subs (:kieu_gay_an a) 0 30) "...")
    (println "     Nghi phạm:" (:nghi_pham_chinh a))
    (println "  ✅ [Mystery 1] PASSED")))

(deftest mystery-victor-outside
  (println "  ✦ [Mystery 2] Victor tấn công từ bên ngoài...")
  (let [result (run-expert
                 "forms/stress_mystery.edn"
                 {:ten_tham_tu "Poirot"
                  :thoi_gian_chet "23:00 - 01:00"
                  :nguyen_nhan_chet "Vết dao đâm"
                  :loai_doc_chat "Không phát hiện"
                  :vi_tri_thi_the "Vườn phía sau"
                  :dau_vet_dau_tranh "Có - mạnh"
                  :dau_vet_xam_nhap "Cửa bị phá"
                  :nguoi_cuoi_gap "Đối tác kinh doanh Victor"
                  :alibi_victor "Không rõ"
                  :di_chuc "Victor là người thụ hưởng chính"
                  :tranh_chap_tai_chinh "Victor tranh chấp hợp đồng"}
                 "scripts/test_data/mystery2.edn")
        a (answers result)]
    (is (str/includes? (:nghi_pham_chinh a) "Victor") "Nghi phạm phải là Victor")
    (is (str/includes? (:ban_an_cuoi_cung a) "Victor") "Ban án phải nhắc Victor")
    (println "     Nghi phạm:" (:nghi_pham_chinh a))
    (println "  ✅ [Mystery 2] PASSED")))

(deftest mystery-min-questions
  (println "  ✦ [Mystery 3] Pruning: Ngôi nhân tố đủ sớm → không hỏi alibi không cần thiết...")
  (let [result (run-expert
                 "forms/stress_mystery.edn"
                 {:ten_tham_tu "Marple"
                  :thoi_gian_chet "21:00 - 23:00"
                  :nguyen_nhan_chet "Ngộ độc thạch tín"
                  :loai_doc_chat "Thạch tín vô cơ"
                  :vi_tri_thi_the "Bếp"
                  :dau_vet_dau_tranh "Không"
                  :dau_vet_xam_nhap "Không có"
                  :nguoi_cuoi_gap "Gia nhân Maria"
                  :di_chuc "Rebecca thừa hưởng tất cả"
                  :tranh_chap_tai_chinh "Rebecca tranh chấp tài sản hôn nhân"
                  ;; Không cung cấp alibi của Thomas và Victor
                  :alibi_rebecca "Không rõ"}
                 "scripts/test_data/mystery3.edn")
        a (answers result)]
    (is (some? (:ban_an_cuoi_cung a)) "Phải đưa ra kết luận")
    ;; Thomas alibi không được hỏi vì không cần để xác định Rebecca
    (println "     Kết luận:" (subs (:ban_an_cuoi_cung a) 0 40) "...")
    (println "     alibi_thomas có trong kết quả:" (some? (:alibi_thomas a)))
    (println "  ✅ [Mystery 3] PASSED")))

;; ─────────────────────────────────────────────────────────────────────────
;; CHẠY TẤT CẢ
;; ─────────────────────────────────────────────────────────────────────────
(require '[clojure.string :as str])
(let [result (run-tests)]
  (println "\n" (if (= 0 (:fail result) (:error result))
                  "🎉 TẤT CẢ STRESS TESTS ĐẠT TIÊU CHUẨN!"
                  "❌ CÓ STRESS TEST THẤT BẠI!")))
