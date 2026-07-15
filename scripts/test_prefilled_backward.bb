;; Script kiểm thử tình huống: Câu hỏi backward ở đầu form được hiển thị ngay lập tức
;; do người dùng truyền sẵn --values thoả mãn điều kiện hiển thị của nó.
;; Chạy: bb scripts/test_prefilled_backward.bb

(require '[clojure.edn :as edn]
         '[clojure.string :as str])
(load-file "src/com/drbinhthanh/bb_form.clj")

(def form (edn/read-string (slurp "forms/hr_survey.edn")))
(def ask-order (atom []))

;; Giả lập mock-inputs cho những câu sẽ được hỏi (không nằm trong prefilled)
(def mock-inputs
  {:cap_macbook_pro "Có, nhận Macbook Pro"
   :ho_ten "DrRingo"
   :email_cong_ty "test@example.com"
   :ma_nhan_vien "NV001"
   :phong_ban "Kỹ thuật"
   :nam_kinh_nghiem_kt 5
   :trinh_do_hoc_van "Đại học"
   :ngay_vao_lam "01-01-2024"
   :muc_do_hai_long "Hài lòng"})

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

;; Giả lập người dùng nhập tham số qua CLI: --values '{:ngon_ngu_chinh "Clojure"}'
(reset! com.drbinhthanh.bb-form.core/answers {:selectedByUser {:ngon_ngu_chinh "Clojure"} :HiddenVar (get form :variables {})})

(println "Đang chạy form Khảo Sát Nhân Sự với prefilled {:ngon_ngu_chinh \"Clojure\"}...")
(com.drbinhthanh.bb-form.core/run-terminal-form form com.drbinhthanh.bb-form.core/answers mock-ui-adapter)

(println "\nThứ tự các câu hỏi được hiển thị thực tế:" @ask-order)

;; Kỳ vọng câu hỏi cap_macbook_pro xuất hiện ngay ở lượt đầu tiên (sau ho_ten)
;; chứ không phải chờ đến vòng lặp thứ 2, vì điều kiện [:ngon_ngu_chinh "Clojure"] đã có sẵn.
(if (= (take 2 @ask-order) '(:ho_ten :cap_macbook_pro))
  (do
    (println "✅ KIỂM THỬ THÀNH CÔNG: Câu hỏi backward (:cap_macbook_pro) đã hiện ra ngay từ vòng quét đầu tiên đúng như mong đợi nhờ giá trị từ --values!")
    (System/exit 0))
  (do
    (println "❌ THẤT BẠI: Câu hỏi backward không xuất hiện ở đúng vị trí đầu tiên.")
    (System/exit 1)))
