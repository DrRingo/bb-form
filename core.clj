;; File: core.clj
(ns form.core
  (:require [babashka.process :refer [shell]]
            [clojure.string :as str]
            [cheshire.core :as json]))

;; Atom để lưu trữ tất cả câu trả lời của form
(def answers (atom {}))

;; -------------------------------
;; Utility functions
;; -------------------------------

;; Chuyển đổi giá trị thành chuỗi
;; Tham số: v - giá trị cần chuyển đổi (có thể là keyword, symbol hoặc giá trị khác)
;; Trả về: chuỗi đã được chuyển đổi
(defn normalize-str [v]
  (cond
    (keyword? v) (name v)
    (symbol? v)  (name v)
    :else (str v)))

;; Chuẩn hóa key cho branching logic
;; Tham số: v - giá trị cần chuẩn hóa
;; Trả về: chuỗi đã được trim, chuyển thành lowercase
(defn normalize-branch-key [v]
  (-> v normalize-str str/trim str/lower-case))

;; Kiểm tra xem field có nên bỏ qua không (đã có giá trị prefilled)
;; Tham số: id - id của field cần kiểm tra, path - đường dẫn hiện tại
;; Trả về: true nếu field đã có giá trị, false nếu chưa
(defn should-skip? [id path]
  (let [field-path (conj path (keyword id))
        v (get-in @answers field-path)]
    ;; (println "DEBUG should-skip? id:" id "path:" path "field-path:" field-path "value:" v)
    (or (and (map? v) (contains? v :_value))
        (and (not (map? v)) (some? v)))))

;; Lấy giá trị đã được điền sẵn cho field
;; Tham số: id - id của field cần lấy giá trị, path - đường dẫn hiện tại
;; Trả về: giá trị đã được điền sẵn hoặc nil nếu không có
(defn get-prefilled [id path]
  (let [field-path (conj path (keyword id))
        v (get-in @answers field-path)]
    ;; (println "DEBUG get-prefilled id:" id "path:" path "field-path:" field-path "value:" v)
    (if (map? v)
      (:_value v)
      v)))

;; Parse giá trị theo kiểu dữ liệu
;; Tham số: v - giá trị cần parse, type - kiểu dữ liệu ("number", "text", "date")
;; Trả về: giá trị đã được parse theo đúng kiểu dữ liệu
(defn parse-value [v type]
  (case type
    "number" (try (Integer/parseInt (str v)) (catch Exception _ v))
    "text"   (str v)
    "date"   (str v)
    v))

;; Helper ép kiểu về map nếu không phải map
(defn force-map [v]
  (cond
    (map? v) v
    (string? v) {:_value v}
    (number? v) {:_value v}
    (nil? v) {}
    :else {}))

;; -------------------------------
;; GUM UI
;; -------------------------------

;; Hiển thị input text với GUM
;; Tham số: label - nhãn hiển thị cho input
;; Trả về: chuỗi người dùng nhập vào
(defn gum-input [label]
  (-> (shell {:out :string} "gum" "input" "--placeholder" label)
      :out str/trim))

;; Hiển thị dropdown select với GUM
;; Tham số: label - nhãn hiển thị, options - danh sách các lựa chọn
;; Trả về: lựa chọn được chọn (một chuỗi)
(defn gum-select [label options]
  (-> (apply shell {:out :string}
             (concat ["gum" "choose" "--header" label] options))
      :out str/trim))

;; Hiển thị multiselect với GUM
;; Tham số: label - nhãn hiển thị, options - danh sách các lựa chọn
;; Trả về: danh sách các lựa chọn được chọn (vector)
(defn gum-multiselect [label options]
  (-> (apply shell {:out :string}
             (concat ["gum" "choose" "--no-limit" "--header" label] options))
      :out str/split-lines))

;; -------------------------------
;; Field handling
;; -------------------------------

;; Multimethod để xử lý các loại field khác nhau
;; Tham số: field - thông tin field, path - đường dẫn trong cấu trúc dữ liệu
;; Trả về: không có (side effect - cập nhật atom answers)
(defmulti ask-field (fn [field & _] (keyword (:type field))))

;; Xử lý branching logic - hiển thị field con dựa trên lựa chọn
;; Tham số: branch - map chứa các nhánh, value - giá trị được chọn, path - đường dẫn hiện tại
;; Trả về: không có (side effect - gọi ask-field cho các field con)
(defn handle-branch [branch value path]
  (let [raw-key (str/trim (str value))
        norm-key (normalize-branch-key value)
        norm-branch (into {} (map (fn [[k v]] [(normalize-branch-key k) v]) branch))]
    (when-let [subfields (get norm-branch norm-key)]
      (doseq [sub subfields]
        (let [field-id (last path)
              branch-key (keyword (str (name field-id) "_branch"))
              branch-path (conj (pop path) branch-key (keyword raw-key))]
          (ask-field sub branch-path))))))

;; Xử lý field kiểu text
;; Tham số: field - thông tin field (id, label, required, branch), path - đường dẫn
;; Trả về: không có (side effect - cập nhật answers và xử lý branch)
(defmethod ask-field :text [{:keys [id label required branch]} path]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (gum-input label))]
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers update-in path #(assoc (force-map %) id-k (parse-value value "text"))))
    (handle-branch branch value (conj path id-k))))

;; Xử lý field kiểu number với validation
;; Tham số: field - thông tin field (id, label, required, branch), path - đường dẫn
;; Trả về: không có (side effect - cập nhật answers và xử lý branch)
(defmethod ask-field :number [{:keys [id label required branch]} path]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (loop []
                  (let [v (gum-input label)]
                    (if (or (not required)
                            (try (Integer/parseInt v) true (catch Exception _ false)))
                      v
                      (do (println "⚠️ Vui lòng nhập số nguyên!") (recur))))))]
    (swap! answers update-in path #(assoc (force-map %) id-k (parse-value value "number")))
    (handle-branch branch value (conj path id-k))))

;; Xử lý field kiểu date
;; Tham số: field - thông tin field (id, label, required, branch), path - đường dẫn
;; Trả về: không có (side effect - cập nhật answers và xử lý branch)
(defmethod ask-field :date [{:keys [id label required branch]} path]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (gum-input (str label " (YYYY-MM-DD)")))]
    (when (or (not required) (not (str/blank? value)))
      (swap! answers update-in path #(assoc (force-map %) id-k (parse-value value "date"))))
    (handle-branch branch value (conj path id-k))))

;; Xử lý field kiểu select (dropdown một lựa chọn)
;; Tham số: field - thông tin field (id, label, options, branch), path - đường dẫn
;; Trả về: không có (side effect - cập nhật answers và xử lý branch)
(defmethod ask-field :select [{:keys [id label options branch]} path]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (gum-select label options))]
    (swap! answers update-in path #(assoc (force-map %) id-k value))
    (handle-branch branch value (conj path id-k))))

;; Xử lý field kiểu multiselect (chọn nhiều lựa chọn)
;; Tham số: field - thông tin field (id, label, options, branch), path - đường dẫn
;; Trả về: không có (side effect - cập nhật answers và xử lý branch cho từng lựa chọn)
(defmethod ask-field :multiselect [{:keys [id label options branch]} path]
  (let [id-k (keyword id)
        raw (if (should-skip? id path)
              (get-prefilled id path)
              (gum-multiselect label options))
        choices (cond
                  (string? raw) [raw]
                  (sequential? raw) raw
                  :else [])]
    (swap! answers update-in path #(assoc (force-map %) id-k choices))
    (doseq [choice choices]
      (handle-branch branch choice (conj path id-k)))))

;; -------------------------------
;; Entry point
;; -------------------------------

;; Hàm chính để chạy form
;; Tham số: form - cấu trúc form chứa title, description và fields
;; Trả về: không có (side effect - hiển thị form)
(defn run-form [form]
  (println "\n📝" (:title form))
  (println (:description form) "\n")
  (doseq [field (:fields form)]
    (ask-field field [:selectedByUser]))
  ;; Việc lưu file được xử lý trong run-form.clj
  )


