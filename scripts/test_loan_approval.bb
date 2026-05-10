;; Kịch bản kiểm thử Form Thẩm Định Vay Vốn Đa Tầng
;; Chạy: bb scripts/test_loan_approval.bb

(require '[clojure.edn :as edn]
         '[clojure.string :as str])
(load-file "src/com/drbinhthanh/bb_form.clj")

(def form (edn/read-string (slurp "forms/loan_approval.edn")))
(def ask-order (atom []))
(def prints (atom []))

;; Giả lập kịch bản:
;; - Khách hàng có nợ xấu (kích hoạt backward giải trình rủi ro)
;; - Khách hàng có BĐS (kích hoạt backward đồng sở hữu)
;; - Vay 2 tỷ, tài sản 1 tỷ (Thiếu tài sản đảm bảo -> Rớt)
(def mock-inputs
  {;; Stage 1
   :ho_ten "Nguyễn Văn A"
   :ngay_sinh "01-01-1990"
   :thu_nhap_thang 20000000
   :lich_su_no_xau "Có, tôi từng bị nợ xấu" ; -> risk 50, credit score 50. Loop lại hỏi giai_trinh
   :giai_trinh_rui_ro "Trả chậm thẻ tín dụng 10 ngày do đi công tác"
   :loai_hinh_cong_viec "Nhân viên văn phòng" ; -> credit score + 20 = 70
   
   ;; Stage 2
   :so_tien_muon_vay 2000000000
   :danh_sach_tai_san ["Bất động sản"] ; -> loop lại hỏi đồng sở hữu
   :thong_tin_nguoi_dong_so_huu "Trần Thị B (Vợ)"
   
   ;; Stage 3
   :thong_bao_tu_choi "OK"
   })

(with-redefs [com.drbinhthanh.bb-form/clear-screen (fn [])
              com.drbinhthanh.bb-form/render-header (fn [_])
              babashka.process/shell (fn [& args] {:out ""})
              clojure.core/println (fn [& args]
                                     (let [msg (apply str args)]
                                       (if (str/starts-with? msg "\nℹ️")
                                         (swap! prints conj msg)
                                         (do))))
              com.drbinhthanh.bb-form/ask-field (fn [field form]
                                                  (if (= (:type field) :hidden)
                                                    nil
                                                    (let [id (:id field)]
                                                      (swap! ask-order conj id)
                                                      (swap! com.drbinhthanh.bb-form/answers assoc-in [:selectedByUser (keyword id)] (get mock-inputs (keyword id) "N/A")))))]
  
  (com.drbinhthanh.bb-form/-main "forms/loan_approval.edn" "--out" "result_loan.edn"))

(clojure.core/println "\n--- THỨ TỰ CÂU HỎI ---")
(clojure.core/println @ask-order)

(def res (edn/read-string (slurp "result_loan.edn")))
(clojure.core/println "\n--- TRẠNG THÁI CUỐI CÙNG ---")
(clojure.core/println (:HiddenVar res))

;; Kiểm tra xem các câu backward có xuất hiện ĐÚNG SAU câu trigger hay không
(let [pass? (and (= @ask-order [:ho_ten :ngay_sinh :thu_nhap_thang :lich_su_no_xau 
                                :giai_trinh_rui_ro ; Xuất hiện ngay sau lich_su_no_xau (vòng 2 của stage 1)
                                :loai_hinh_cong_viec
                                :so_tien_muon_vay :danh_sach_tai_san
                                :thong_tin_nguoi_dong_so_huu ; Xuất hiện sau danh_sach_tai_san
                                :thong_bao_tu_choi])
                 (= (:loan_status (:HiddenVar res)) "Bị Từ Chối")
                 (= (:global_credit_score (:HiddenVar res)) 70)
                 (= (:global_total_assets (:HiddenVar res)) 1000000000))]
  (if pass?
    (do
      (clojure.core/println "\n✅ KIỂM THỬ THÀNH CÔNG: Vòng lặp Backward và Hidden Variables (Local/Global) hoạt động hoàn hảo!")
      (System/exit 0))
    (do
      (clojure.core/println "\n❌ KIỂM THỬ THẤT BẠI!")
      (System/exit 1))))
