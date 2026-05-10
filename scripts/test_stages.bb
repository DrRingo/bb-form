;; Script kiểm thử Giai đoạn 4: Stages, Global/Local Vars, Actions
(require '[clojure.edn :as edn]
         '[clojure.string :as str])
(load-file "src/com/drbinhthanh/bb_form.clj")

(def form (edn/read-string (slurp "forms/stage_demo.edn")))
(def ask-order (atom []))
(def prints (atom []))

;; Giả lập kịch bản Đậu vòng 2
(def mock-inputs
  {:ho_ten "DrRingo"
   :kinh_nghiem 6 ; -> diem_kinh_nghiem = 60 -> hỏi câu phụ
   :cau_hoi_phu_chuyen_gia "Hệ thống AI Form"
   :cau_hoi_thu_thach "Clojure" ; -> diem_tong += 50 -> tổng = 110
   ;; :thong_bao_rot sẽ KHÔNG hiển thị vì tổng điểm > 100
   })

(with-redefs [com.drbinhthanh.bb-form/clear-screen (fn [])
              com.drbinhthanh.bb-form/render-header (fn [_])
              babashka.process/shell (fn [& args] 
                                       ;; Giả lập ấn Enter cho lệnh gum
                                       {:out ""})
              clojure.core/println (fn [& args]
                                     (let [msg (apply str args)]
                                       (if (str/starts-with? msg "\nℹ️")
                                         (swap! prints conj msg)
                                         (do)))) ;; Bỏ qua các lệnh in khác để log sạch
              com.drbinhthanh.bb-form/ask-field (fn [field form]
                                                  (if (= (:type field) :hidden)
                                                    nil
                                                    (let [id (:id field)]
                                                      (swap! ask-order conj id)
                                                      (swap! com.drbinhthanh.bb-form/answers assoc-in [:selectedByUser (keyword id)] (get mock-inputs (keyword id) "N/A")))))]
  
  (com.drbinhthanh.bb-form/-main "forms/stage_demo.edn" "--out" "result_stage.edn"))

;; ----------------------------------------
;; Xác minh kết quả
;; ----------------------------------------

(clojure.core/println "\n--- KẾT QUẢ TEST ---")
(clojure.core/println "Thứ tự hỏi:" @ask-order)
(clojure.core/println "Các log in ra:" @prints)

(def res (edn/read-string (slurp "result_stage.edn")))
(clojure.core/println "Trạng thái HiddenVar cuối cùng:" (:HiddenVar res))
(clojure.core/println "Biến Local của Vòng 1:" (:diem_kinh_nghiem (:selectedByUser res)))

(let [pass? (and (= @ask-order [:ho_ten :kinh_nghiem :cau_hoi_phu_chuyen_gia :cau_hoi_thu_thach])
                 (= (:HiddenVar res) {:diem_tong 110 :trang_thai "Đã Đậu"})
                 (= (:diem_kinh_nghiem (:selectedByUser res)) 60))]
  (if pass?
    (do
      (clojure.core/println "\n✅ KIỂM THỬ THÀNH CÔNG TẤT CẢ CÁC TÍNH NĂNG GIAI ĐOẠN 4!")
      (System/exit 0))
    (do
      (clojure.core/println "\n❌ KIỂM THỬ THẤT BẠI!")
      (System/exit 1))))
