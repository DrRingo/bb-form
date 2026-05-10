(ns formulas.bayesian-hiring)

;; Hàm mô phỏng việc gọi CmdStan
;; Trong thực tế, bạn sẽ dùng clojure.java.shell để gọi cmdstan:
;; (shell/sh "cmdstan/bin/hiring_model" "sample" "data" ...)
(defn run-stan-model [experience test-score]
  (println "🤖 [CmdStan] Đang biên dịch và chạy mô hình phân phối MCMC cho dữ liệu ứng viên mới...")
  (Thread/sleep 1500) ;; Giả lập thời gian chạy MCMC 1.5s
  
  ;; Giả lập trích xuất kết quả từ file output.csv của Stan (Posterior summary)
  ;; Trả về MAP biểu diễn biến xác suất
  (let [base-prob (min 0.99 (+ (* experience 0.05) (* test-score 0.08) -0.1))
        variance 0.02
        lower-bound (max 0.0 (- base-prob (* 1.96 (Math/sqrt variance))))
        upper-bound (min 1.0 (+ base-prob (* 1.96 (Math/sqrt variance))))]
    {:prob        (Math/round (* base-prob 100.0))
     :variance    variance
     :lower_bound (Math/round (* lower-bound 100.0))
     :upper_bound (Math/round (* upper-bound 100.0))
     :confidence  "95%"}))
