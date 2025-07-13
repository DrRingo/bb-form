;; File: core.clj
(ns form.core
  (:require [babashka.process :refer [shell]]
            [clojure.string :as str]
            [cheshire.core :as json]))

;; Atom để lưu trữ tất cả câu trả lời của form
(def answers (atom {}))
;; Atom cho dòng trạng thái/thông báo lỗi
(def status-line (atom ""))

;; Hàm cập nhật status-line
(defn set-status! [msg]
  (reset! status-line msg))

;; Hàm xóa status-line
(defn clear-status! []
  (reset! status-line ""))

;; Hàm clear màn hình
(defn clear-screen []
  (print "\033[2J")  ;; Clear toàn bộ màn hình
  (print "\033[H"))  ;; Di chuyển con trỏ về đầu

;; Hàm render header (title, description, status-line)
(defn render-header [form]
  (println "\n📝" (:title form))
  (println (:description form) "\n")
  (println (str ":::: " @status-line)))

;; Hàm hiển thị thông báo lỗi với GUM
(defn show-error [message]
  (shell {:out :string} "gum" "style" "--foreground" "#ff0000" "--border" "normal" "--border-foreground" "#ff0000" "--margin" "1" "--padding" "1" message))

;; Hàm in status-line (luôn in sau tiêu đề/mô tả)
(defn print-status []
  (when (not (str/blank? @status-line))
    (show-error @status-line)))

;; Hàm clear dòng status cũ
(defn clear-status-line []
  (print "\033[2K")  ;; Xóa dòng hiện tại
  (print "\033[1A")  ;; Di chuyển lên 1 dòng
  (print "\033[2K")) ;; Xóa dòng đó

;; Hàm in lại status-line (sau khi đã clear)
(defn reprint-status []
  (when (not (str/blank? @status-line))
    (show-error @status-line)))

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
    "date"   (let [s (str v)]
                 (if (re-matches #"^\d{2}-\d{2}-\d{4}$" s)
                   (try
                     (let [dt (java.time.LocalDate/parse s (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy"))]
                       (.format dt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd")))
                     (catch Exception _ s))
                   s))
    v))

;; Lấy ngày hôm nay theo định dạng DD-MM-YYYY
;; Trả về: chuỗi ngày hôm nay
(defn today []
  (let [now (java.time.LocalDate/now)]
    (.format now (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy"))))

;; Lấy tháng và năm hiện tại
;; Trả về: map với :month và :year
(defn current-month-year []
  (let [now (java.time.LocalDate/now)]
    {:month (.getMonthValue now)
     :year (.getYear now)}))

;; Xử lý gõ tắt cho ngày tháng
;; Tham số: input - chuỗi người dùng nhập
;; Trả về: chuỗi ngày tháng đầy đủ DD-MM-YYYY
(defn expand-date-shortcut [input]
  (let [trimmed (str/trim input)
        {:keys [month year]} (current-month-year)]
    (cond
      ;; Gõ 2 chữ số: dd (lấy tháng và năm hiện tại)
      (re-matches #"^\d{2}$" trimmed)
      (let [dd (Integer/parseInt trimmed)]
        (format "%02d-%02d-%d" dd month year))
      
      ;; Gõ 4 chữ số: ddmm (lấy năm hiện tại)
      (re-matches #"^\d{4}$" trimmed)
      (let [dd (Integer/parseInt (subs trimmed 0 2))
            mm (Integer/parseInt (subs trimmed 2 4))]
        (format "%02d-%02d-%d" dd mm year))
      
      ;; Gõ đầy đủ DD-MM-YYYY hoặc DD/MM/YYYY
      (re-matches #"^\d{2}[-/]\d{2}[-/]\d{4}$" trimmed)
      (str/replace trimmed #"[/]" "-")
      
      ;; Các trường hợp khác, giữ nguyên
      :else trimmed)))

;; Kiểm tra tính hợp lệ của ngày tháng
;; Tham số: date-str - chuỗi ngày tháng theo định dạng DD-MM-YYYY
;; Trả về: true nếu ngày tháng hợp lệ, false nếu không
(defn valid-date? [date-str]
  (if-let [[_ dd mm yyyy] (re-matches #"^(\d{2})-(\d{2})-(\d{4})$" date-str)]
    (let [d (Integer/parseInt dd)
          m (Integer/parseInt mm)
          y (Integer/parseInt yyyy)
          max-day (cond
                     (or (< m 1) (> m 12)) 0
                     (= m 2) (if (or (zero? (mod y 400)) (and (zero? (mod y 4)) (not (zero? (mod y 100))))) 29 28)
                     (#{4 6 9 11} m) 30
                     :else 31)]
      (and (<= 1 d max-day)))
    false))

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

;; Xóa dòng hiện tại và dòng trước đó
(defn clear-error-lines []
  (print "\033[2K")  ;; Xóa dòng hiện tại
  (print "\033[1A")  ;; Di chuyển lên 1 dòng
  (print "\033[2K")  ;; Xóa dòng đó
  (flush))

;; Hiển thị thông báo lỗi với GUM
(defn show-error [message]
  (shell {:out :string} "gum" "style" "--foreground" "#ff0000" "--border" "normal" "--border-foreground" "#ff0000" "--margin" "1" "--padding" "1" message))

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
(defn handle-branch [branch value path form]
  (let [raw-key (str/trim (str value))
        norm-key (normalize-branch-key value)
        norm-branch (into {} (map (fn [[k v]] [(normalize-branch-key k) v]) branch))]
    (when-let [subfields (get norm-branch norm-key)]
      (doseq [sub subfields]
        (let [field-id (last path)
              branch-key (keyword (str (name field-id) "_branch"))
              branch-path (conj (pop path) branch-key (keyword raw-key))]
          (ask-field sub branch-path form))))))

;; Sửa các hàm validation để chỉ cập nhật status-line
(defmethod ask-field :text [{:keys [id label required branch regex regexError]} path form]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (loop []
                  (let [v (gum-input label)]
                    (if (and regex (not (re-matches (re-pattern regex) v)))
                      (do (set-status! (or regexError (str "Giá trị không khớp với regex: " regex))) (clear-screen) (render-header form) (recur))
                      (do (clear-status!) (clear-screen) (render-header form) v)))))]
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers update-in path #(assoc (force-map %) id-k (parse-value value "text"))))
    (handle-branch branch value (conj path id-k) form)))

(defmethod ask-field :number [{:keys [id label required branch]} path form]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (loop []
                  (let [v (gum-input label)]
                    (if (or (not required)
                            (try (Integer/parseInt v) true (catch Exception _ false)))
                      (do (clear-status!) (clear-screen) (render-header form) v)
                      (do (set-status! "⚠️ Vui lòng nhập số nguyên!") (clear-screen) (render-header form) (recur))))))]
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers update-in path #(assoc (force-map %) id-k (parse-value value "number"))))
    (handle-branch branch value (conj path id-k) form)))

(defmethod ask-field :date [{:keys [id label required branch]} path form]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (loop []
                  (let [v (gum-input (str label " (DD-MM-YYYY hoặc gõ tắt: 04, 1204)"))]
                    (cond
                      (str/blank? v) (do (clear-status!) (clear-screen) (render-header form) (today))
                      :else
                      (let [expanded (expand-date-shortcut v)]
                        (if (not (valid-date? expanded))
                          (do (set-status! "⚠️ Ngày tháng không hợp lệ. Ví dụ: 31-12-2023") (clear-screen) (render-header form) (recur))
                          (do (clear-status!) (clear-screen) (render-header form) expanded)))))))]
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers update-in path #(assoc (force-map %) id-k (parse-value value "date"))))
    (handle-branch branch value (conj path id-k) form)))

(defmethod ask-field :select [{:keys [id label options branch]} path form]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (gum-select label options))]
    (swap! answers update-in path #(assoc (force-map %) id-k value))
    (handle-branch branch value (conj path id-k) form)))

(defmethod ask-field :multiselect [{:keys [id label options branch]} path form]
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
      (handle-branch branch choice (conj path id-k) form))))

;; -------------------------------
;; Entry point
;; -------------------------------

;; Hàm chính để chạy form
;; Tham số: form - cấu trúc form chứa title, description và fields
;; Trả về: không có (side effect - hiển thị form)
(defn run-form [form]
  (render-header form)
  (doseq [field (:fields form)]
    (ask-field field [:selectedByUser] form))
  ;; Việc lưu file được xử lý trong run-form.clj
  )


