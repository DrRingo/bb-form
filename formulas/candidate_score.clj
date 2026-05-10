(ns formulas.candidate-score
  (:require [formulas.math-matrix :as matrix]))

;; Ma trận trọng số (Weight Matrix) phân tích kỹ năng
;; Cột: [UI/UX, System Design, Algorithms, DevOps]
;; Hàng 0: Trọng số vị trí Frontend
;; Hàng 1: Trọng số vị trí Backend
;; Hàng 2: Trọng số vị trí System Architect
(def weight-matrix
  [[0.5  0.1  0.3  0.1]
   [0.0  0.3  0.5  0.2]
   [0.1  0.5  0.2  0.2]])

(defn analyze-candidate [ui-score sys-score algo-score devops-score]
  (let [skill-vector [ui-score sys-score algo-score devops-score]
        ;; Nhân ma trận trọng số với vector kỹ năng
        role-scores (matrix/multiply-matrix-vector weight-matrix skill-vector)
        max-score (apply max role-scores)
        best-fit-index (.indexOf (vec role-scores) max-score)]
    
    {:scores (map #(Math/round (* % 10.0)) role-scores)
     :best-score (Math/round (* max-score 10.0))
     :recommended-role (case best-fit-index
                         0 "Frontend Developer"
                         1 "Backend Developer"
                         2 "System Architect"
                         "Unknown")}))

;; Hàm phụ để trích xuất role từ kết quả phân tích
(defn get-recommended-role [analysis-result]
  (:recommended-role analysis-result))

;; Hàm phụ để lấy điểm cao nhất
(defn get-best-score [analysis-result]
  (:best-score analysis-result))
