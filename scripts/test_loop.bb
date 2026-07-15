(require '[clojure.edn :as edn]
         '[com.drbinhthanh.bb-form.core :as core])

(def form (edn/read-string (slurp "forms/backward_loop_test.edn")))
(def ask-order (atom []))

;; Giả lập dữ liệu nhập của người dùng
(def mock-inputs
  {:ho_ten "DrRingo"
   :tham_gia "Không"
   :ly_do_tu_choi "Tôi bận việc riêng"})

;; Tạo mock-ui-adapter
(def mock-ui-adapter
  {:clear-screen  (fn [])
   :render-header (fn [_ _])
   :show-error    (fn [_])
   :ask-field     (fn [field form answers-atom]
                    (let [id (:id field)]
                      (swap! ask-order conj (keyword id))
                      (swap! answers-atom assoc-in [:selectedByUser (keyword id)] (get mock-inputs (keyword id)))))
   :pause         (fn [_])})

(reset! core/answers {:selectedByUser {} :HiddenVar (get form :variables {})})
(println "Đang chạy form với cơ chế Restarting Loop...")
(core/run-terminal-form form core/answers mock-ui-adapter)

(println "Thứ tự các câu hỏi được hỏi:" @ask-order)
(if (= @ask-order [:ho_ten :tham_gia :ly_do_tu_choi])
  (do
    (println "✅ KIỂM THỬ THÀNH CÔNG: Vòng lặp Restarting Loop hoạt động đúng!")
    (println "Giải thích:")
    (println "  - Lượt 1: Bỏ qua :ly_do_tu_choi (vì :tham_gia chưa có). Hỏi :ho_ten, hỏi :tham_gia (trả lời 'Không').")
    (println "  - Lượt 2: Phát hiện có câu hỏi mới thoả mãn (:ly_do_tu_choi). Hỏi :ly_do_tu_choi.")
    (println "  - Lượt 3: Không có câu nào mới. Kết thúc form."))
  (do
    (println "❌ THẤT BẠI: Vòng lặp Restarting Loop sai thứ tự.")
    (System/exit 1)))
