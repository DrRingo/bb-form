(ns formulas.bayesian-hiring
  (:require [clojure.java.shell :as shell]
            [cheshire.core :as json]))

;; ─────────────────────────────────────────────────────────────────────────────
;; run-stan-model
;;
;; Gọi thực sự mô hình Stan (hiring_model.stan) thông qua Python bridge
;; (scripts/run_hiring_model.py → cmdstanpy → CmdStan MCMC).
;;
;; Yêu cầu cài đặt một lần:
;;   bash scripts/setup_stan.sh
;;
;; Tham số:
;;   experience  — số năm kinh nghiệm (số thực)
;;   test-score  — điểm bài test kỹ năng, thang 1-10 (số thực)
;;
;; Trả về map:
;;   {:prob        — xác suất được tuyển (%) - posterior mean
;;    :variance    — phương sai posterior
;;    :lower_bound — cận dưới khoảng tin cậy 95%
;;    :upper_bound — cận trên khoảng tin cậy 95%
;;    :confidence  — "95%"
;;    :method      — "MCMC-Stan (cmdstanpy)"
;;    :chains      — số Markov chains (4)
;;    :samples     — tổng số samples posterior
;;    :rhat        — R-hat (hội tụ MCMC, lý tưởng < 1.01)}
;; ─────────────────────────────────────────────────────────────────────────────

(defn- find-project-root []
  ;; Tìm thư mục gốc dự án từ vị trí file script hiện tại
  (let [cwd (System/getProperty "user.dir")]
    cwd))

(defn- python-bridge-path []
  (str (find-project-root) "/scripts/run_hiring_model.py"))

(defn- venv-python []
  ;; Dùng Python từ virtual environment để cmdstanpy có sẵn
  ;; Nếu venv không tồn tại, fallback sang python3 hệ thống
  (let [venv-py (str (find-project-root) "/scripts/.venv/bin/python3")]
    (if (.exists (java.io.File. venv-py))
      venv-py
      "python3")))

(defn- parse-stan-output [raw-json]
  (let [parsed (json/parse-string raw-json true)]
    (if (:error parsed)
      (throw (ex-info (str "Stan model lỗi: " (:error parsed))
                      {:raw raw-json}))
      {:prob        (long (:prob parsed))
       :variance    (double (:variance parsed))
       :lower_bound (long (:lower_bound parsed))
       :upper_bound (long (:upper_bound parsed))
       :confidence  (:confidence parsed)
       :method      (:method parsed)
       :chains      (long (:chains parsed))
       :samples     (long (:samples parsed))
       :rhat        (double (:rhat parsed))})))

(defn run-stan-model
  "Chạy thực sự mô hình Bayesian Logistic Regression (Stan MCMC) để
   dự đoán xác suất tuyển dụng ứng viên.

   Yêu cầu: bash scripts/setup_stan.sh (chạy một lần để cài CmdStan)"
  [experience test-score]
  (println "🔬 [CmdStan] Đang chạy MCMC sampling (4 chains × 1000 samples)...")
  (println "   experience =" experience ", test_score =" test-score)

  (let [input-json (json/generate-string {:experience experience
                                           :test_score  test-score})
        bridge     (python-bridge-path)
        python     (venv-python)
        result     (shell/sh python bridge input-json)]

    (when (not= 0 (:exit result))
      (throw (ex-info "Python bridge thất bại"
                      {:stderr (:err result)
                       :exit   (:exit result)})))

    (let [output (parse-stan-output (clojure.string/trim (:out result)))]
      (println "✅ [CmdStan] MCMC hoàn tất."
               "| prob_hire =" (:prob output) "%"
               "| 95% CI [" (:lower_bound output) "%," (:upper_bound output) "%]"
               "| R-hat =" (:rhat output))
      output)))

;; ─────────────────────────────────────────────────────────────────────────────
;; Hàm kiểm tra Stan đã được cài hay chưa
;; ─────────────────────────────────────────────────────────────────────────────
(defn stan-available? []
  (let [result (shell/sh (venv-python) "-c" "import cmdstanpy; cmdstanpy.cmdstan_path()")]
    (= 0 (:exit result))))

(defn check-stan-setup []
  (if (stan-available?)
    (println "✅ CmdStan sẵn sàng.")
    (do
      (println "❌ CmdStan chưa được cài đặt.")
      (println "   Chạy lệnh sau để cài:")
      (println "   bash scripts/setup_stan.sh"))))
