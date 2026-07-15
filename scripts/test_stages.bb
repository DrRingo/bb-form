;; Script kiểm thử Giai đoạn 4: Stages, Global/Local Vars, Actions
(require '[clojure.edn :as edn]
         '[clojure.string :as str])
(load-file "src/com/drbinhthanh/bb_form.clj")

(def core-ns (find-ns 'com.drbinhthanh.bb-form.core))
(def form (edn/read-string (slurp "forms/stage_demo.edn")))
(def ask-order (atom []))
(def prints (atom []))

;; Giả lập kịch bản Đậu vòng 2
(def mock-inputs
  {:ho_ten "DrRingo"
   :kinh_nghiem 6 ; -> diem_kinh_nghiem = 60 -> hỏi câu phụ
   :cau_hoi_phu_chuyen_gia "Hệ thống AI Form"
   :cau_hoi_thu_thach "Clojure" ; -> diem_tong += 50 -> tổng = 110
   })

;; Tạo mock-ui-adapter
(def mock-ui-adapter
  {:clear-screen  (fn [])
   :render-header (fn [_ _])
   :show-error    (fn [_])
   :ask-field     (fn [field form answers-atom]
                    (if (= (:type field) :hidden)
                      nil
                      (let [id (:id field)]
                        (swap! ask-order conj (keyword id))
                        (swap! answers-atom assoc-in [:selectedByUser (keyword id)] (get mock-inputs (keyword id) "N/A")))))
   :pause         (fn [_])})

(reset! com.drbinhthanh.bb-form.core/answers {:selectedByUser {} :HiddenVar (get form :variables {})})
(com.drbinhthanh.bb-form.core/run-terminal-form form com.drbinhthanh.bb-form.core/answers mock-ui-adapter)

;; ----------------------------------------
;; Xác minh kết quả
;; ----------------------------------------

(println "\n--- KẾT QUẢ TEST ---")
(println "Thứ tự hỏi:" @ask-order)

(def res @com.drbinhthanh.bb-form.core/answers)
(println "Trạng thái HiddenVar cuối cùng:" (:HiddenVar res))
(println "Biến Local của Vòng 1:" (:diem_kinh_nghiem (:selectedByUser res)))

(let [pass? (and (= @ask-order [:ho_ten :kinh_nghiem :cau_hoi_phu_chuyen_gia :cau_hoi_thu_thach])
                 (= (:HiddenVar res) {:diem_tong 110 :trang_thai "Đã Đậu"})
                 (= (:diem_kinh_nghiem (:selectedByUser res)) 60))]
  (if pass?
    (do
      (println "\n✅ KIỂM THỬ THÀNH CÔNG TẤT CẢ CÁC TÍNH NĂNG GIAI ĐOẠN 4!")
      (System/exit 0))
    (do
      (println "\n❌ KIỂM THỬ THẤT BẠI!")
      (System/exit 1))))
