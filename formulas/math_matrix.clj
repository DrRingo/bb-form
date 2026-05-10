(ns formulas.math-matrix)

;; Hàm tính tích vô hướng (Dot product) của 2 vector
(defn dot-product [v1 v2]
  (reduce + (map * v1 v2)))

;; Hàm nhân một ma trận (matrix) với một vector
(defn multiply-matrix-vector [matrix vec]
  (map #(dot-product % vec) matrix))

;; Tính tổng các phần tử trong vector
(defn sum [vec]
  (reduce + vec))
