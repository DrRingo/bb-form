;; Script kiểm thử cơ chế Restarting Loop
;; Chạy: bb scripts/test_loop.bb
(require '[clojure.edn :as edn])
(load-file "src/com/drbinhthanh/bb_form.clj")

(def form (edn/read-string (slurp "forms/backward_loop_test.edn")))
(def ask-order (atom []))

;; Giả lập dữ liệu nhập của người dùng
(def mock-inputs
  {:ho_ten "DrRingo"
   :tham_gia "Không"
   :ly_do_tu_choi "Tôi bận việc riêng"})

;; Override các hàm UI và ask-field để không gọi gum
(with-redefs [com.drbinhthanh.bb-form/clear-screen (fn [])
              com.drbinhthanh.bb-form/render-header (fn [_])
              com.drbinhthanh.bb-form/ask-field (fn [field form]
                                                  (let [id (:id field)]
                                                    (swap! ask-order conj id)
                                                    (swap! com.drbinhthanh.bb-form/answers assoc-in [:selectedByUser (keyword id)] (get mock-inputs (keyword id)))))]
  (reset! com.drbinhthanh.bb-form/answers {:selectedByUser {}})
  (println "Đang chạy form với cơ chế Restarting Loop...")
  (com.drbinhthanh.bb-form/run-form form))

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
