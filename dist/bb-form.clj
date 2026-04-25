(ns com.drbinhthanh.bb-form
  (:require [babashka.process :refer [shell]]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

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
  (println (str ":::: " @status-line))
  ;; Luôn thêm dòng trống sau status line để tạo khoảng cách cố định
  (println))

;; Hàm hiển thị thông báo lỗi với GUM
(defn show-error [message]
  (shell {:out :string} "gum" "style" "--foreground" "#ff0000" "--border" "normal" "--border-foreground" "#ff0000" "--margin" "1" "--padding" "1" message)
  ;; Thêm dòng trống sau thông báo lỗi để tạo khoảng cách
  (println))

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

;; Hàm chuẩn hóa string từ các kiểu dữ liệu khác nhau
(defn normalize-str [v]
  (cond
    (keyword? v) (name v)
    (symbol? v)  (name v)
    :else (str v)))

;; Hàm chuẩn hóa key cho branch logic (lowercase và trim)
(defn normalize-branch-key [v]
  (-> v normalize-str str/trim str/lower-case))

;; Hàm kiểm tra xem có nên bỏ qua field này không (đã có giá trị)
(defn should-skip? [id path]
  (let [field-path (conj path (keyword id))
        v (get-in @answers field-path)]
    (or (and (map? v) (contains? v :_value))
        (and (not (map? v)) (some? v)))))

;; Hàm lấy giá trị đã điền trước đó cho field
(defn get-prefilled [id path]
  (let [field-path (conj path (keyword id))
        v (get-in @answers field-path)]
    (if (map? v)
      (:_value v)
      v)))

;; Hàm parse giá trị theo kiểu dữ liệu
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

;; Hàm lấy ngày hôm nay theo định dạng DD-MM-YYYY
(defn today []
  (let [now (java.time.LocalDate/now)]
    (.format now (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy"))))

;; Hàm lấy tháng và năm hiện tại
(defn current-month-year []
  (let [now (java.time.LocalDate/now)]
    {:month (.getMonthValue now)
     :year (.getYear now)}))

;; Hàm mở rộng các cách gõ tắt cho ngày tháng
(defn expand-date-shortcut [input]
  (let [trimmed (str/trim input)
        {:keys [month year]} (current-month-year)]
    (cond
      ;; Gõ tắt: "04" -> "04-MM-YYYY" (ngày 04 tháng hiện tại năm hiện tại)
      (re-matches #"^\d{2}$" trimmed)
      (let [dd (Integer/parseInt trimmed)]
        (format "%02d-%02d-%d" dd month year))
      ;; Gõ tắt: "1204" -> "12-04-YYYY" (ngày 12 tháng 04 năm hiện tại)
      (re-matches #"^\d{4}$" trimmed)
      (let [dd (Integer/parseInt (subs trimmed 0 2))
            mm (Integer/parseInt (subs trimmed 2 4))]
        (format "%02d-%02d-%d" dd mm year))
      ;; Chuyển đổi dấu "/" thành "-" trong ngày tháng
      (re-matches #"^\d{2}[-/]\d{2}[-/]\d{4}$" trimmed)
      (str/replace trimmed #"[/]" "-")
      :else trimmed)))

;; Hàm kiểm tra tính hợp lệ của ngày tháng
(defn valid-date? [date-str]
  (if-let [[_ dd mm yyyy] (re-matches #"^(\d{2})-(\d{2})-(\d{4})$" date-str)]
    (let [d (Integer/parseInt dd)
          m (Integer/parseInt mm)
          y (Integer/parseInt yyyy)
          ;; Tính số ngày tối đa trong tháng
          max-day (cond
                     (or (< m 1) (> m 12)) 0  ;; Tháng không hợp lệ
                     (= m 2) (if (or (zero? (mod y 400)) (and (zero? (mod y 4)) (not (zero? (mod y 100))))) 29 28)  ;; Tháng 2 (năm nhuận)
                     (#{4 6 9 11} m) 30  ;; Tháng có 30 ngày
                     :else 31)]  ;; Tháng có 31 ngày
      (and (<= 1 d max-day)))
    false))

;; Hàm ép buộc chuyển đổi giá trị thành map
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

;; Hàm xóa các dòng lỗi cũ trên terminal
(defn clear-error-lines []
  (print "\033[2K")  ;; Xóa dòng hiện tại
  (print "\033[1A")  ;; Di chuyển lên 1 dòng
  (print "\033[2K")  ;; Xóa dòng đó
  (flush))

;; Hàm tạo input field với GUM
(defn gum-input [label]
  (-> (shell {:out :string} "gum" "input" "--placeholder" label)
      :out str/trim))

;; Hàm tạo select dropdown với GUM
(defn gum-select [label options]
  (-> (apply shell {:out :string}
             (concat ["gum" "choose" "--header" label] options))
      :out str/trim))

;; Hàm tạo multiselect với GUM
(defn gum-multiselect [label options]
  (-> (apply shell {:out :string}
             (concat ["gum" "choose" "--no-limit" "--header" label] options))
      :out str/split-lines))

;; -------------------------------
;; Field handling
;; -------------------------------

;; Multimethod để xử lý các loại field khác nhau
(defmulti ask-field (fn [field & _] (keyword (:type field))))

;; Hàm xử lý logic phân nhánh (branching) cho field
(defn handle-branch [branch value path form]
  (let [raw-key (str/trim (str value))
        norm-key (normalize-branch-key value)
        ;; Chuẩn hóa branch keys để so sánh
        norm-branch (into {} (map (fn [[k v]] [(normalize-branch-key k) v]) branch))]
    (when-let [subfields (get norm-branch norm-key)]
      (doseq [sub subfields]
        (let [field-id (last path)
              branch-key (keyword (str (name field-id) "_branch"))
              branch-path (conj (pop path) branch-key (keyword raw-key))]
          (ask-field sub branch-path form))))))

;; Method xử lý field text với regex validation
(defmethod ask-field :text [{:keys [id label required branch regex regexError]} path form]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (loop []
                  (let [v (gum-input label)]
                    ;; Kiểm tra regex nếu có
                    (if (and regex (not (re-matches (re-pattern regex) v)))
                      (do (set-status! (or regexError (str "Giá trị không khớp với regex: " regex))) (clear-screen) (render-header form) (recur))
                      (do (clear-status!) (clear-screen) (render-header form) v)))))]
    ;; Chỉ lưu giá trị nếu field không bắt buộc hoặc có giá trị
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers update-in path #(assoc (force-map %) id-k (parse-value value "text"))))
    ;; Xử lý branch logic nếu có
    (handle-branch branch value (conj path id-k) form)))

;; Method xử lý field number với validation số nguyên
(defmethod ask-field :number [{:keys [id label required branch]} path form]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (loop []
                  (let [v (gum-input label)]
                    ;; Kiểm tra xem có phải số nguyên không
                    (if (or (not required)
                            (try (Integer/parseInt v) true (catch Exception _ false)))
                      (do (clear-status!) (clear-screen) (render-header form) v)
                      (do (set-status! "⚠️ Vui lòng nhập số nguyên!") (clear-screen) (render-header form) (recur))))))]
    ;; Chỉ lưu giá trị nếu field không bắt buộc hoặc có giá trị
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers update-in path #(assoc (force-map %) id-k (parse-value value "number"))))
    ;; Xử lý branch logic nếu có
    (handle-branch branch value (conj path id-k) form)))

;; Method xử lý field date với validation và gõ tắt
(defmethod ask-field :date [{:keys [id label required branch]} path form]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (loop []
                  (let [v (gum-input (str label " (DD-MM-YYYY hoặc gõ tắt: 04, 1204)"))]
                    (cond
                      ;; Nếu để trống, lấy ngày hôm nay
                      (str/blank? v) (do (clear-status!) (clear-screen) (render-header form) (today))
                      :else
                      (let [expanded (expand-date-shortcut v)]
                        ;; Kiểm tra tính hợp lệ của ngày tháng
                        (if (not (valid-date? expanded))
                          (do (set-status! "⚠️ Ngày tháng không hợp lệ. Ví dụ: 31-12-2023") (clear-screen) (render-header form) (recur))
                          (do (clear-status!) (clear-screen) (render-header form) expanded)))))))]
    ;; Chỉ lưu giá trị nếu field không bắt buộc hoặc có giá trị
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers update-in path #(assoc (force-map %) id-k (parse-value value "date"))))
    ;; Xử lý branch logic nếu có
    (handle-branch branch value (conj path id-k) form)))

;; Method xử lý field select (dropdown chọn một)
(defmethod ask-field :select [{:keys [id label options branch]} path form]
  (let [id-k (keyword id)
        value (if (should-skip? id path)
                (get-prefilled id path)
                (gum-select label options))]
    ;; Lưu giá trị được chọn
    (swap! answers update-in path #(assoc (force-map %) id-k value))
    ;; Xử lý branch logic nếu có
    (handle-branch branch value (conj path id-k) form)))

;; Method xử lý field multiselect (chọn nhiều)
(defmethod ask-field :multiselect [{:keys [id label options branch]} path form]
  (let [id-k (keyword id)
        raw (if (should-skip? id path)
              (get-prefilled id path)
              (gum-multiselect label options))
        ;; Chuẩn hóa kết quả multiselect
        choices (cond
                  (string? raw) [raw]
                  (sequential? raw) raw
                  :else [])]
    ;; Lưu danh sách các lựa chọn
    (swap! answers update-in path #(assoc (force-map %) id-k choices))
    ;; Xử lý branch logic cho từng lựa chọn
    (doseq [choice choices]
      (handle-branch branch choice (conj path id-k) form))))

;; -------------------------------
;; Entry point
;; -------------------------------

;; Hàm chính để chạy form
(defn run-form [form]
  (clear-screen)
  (render-header form)
  ;; Duyệt qua tất cả các field trong form
  (doseq [field (:fields form)]
    (ask-field field [:selectedByUser] form)))

;; -------------------------------
;; CLI entry point (from run-form.clj)
;; -------------------------------

;; Hàm parse các tham số key:value từ command line
(defn parse-kv-args [args]
  (->> args
       (filter #(str/includes? % ":"))
       (map #(str/split % #":" 2))
       (map (fn [[k v]] [(keyword k) v]))
       (into {})))

;; Hàm parse các options từ command line (--values, --out)
(defn parse-options [args]
  (loop [args args
         opts {}]
    (if (empty? args)
      opts
      (let [[k & rest] args]
        (cond
          ;; Parse option --values
          (= k "--values")
          (if (empty? rest)
            (do (println "❌ Thiếu file values sau --values") (System/exit 1))
            (recur (drop 1 rest)
                   (assoc opts :values-file (first rest))))

          ;; Parse option --out
          (= k "--out")
          (if (empty? rest)
            (do (println "❌ Thiếu file output sau --out") (System/exit 1))
            (recur (drop 1 rest)
                   (assoc opts :output-file (first rest))))

          ;; Bỏ qua các argument không phải option
          (str/starts-with? k "--")
          (do (println (str "❌ Option không được hỗ trợ: " k)) (System/exit 1))

          :else
          (recur rest
                 (update opts :kv-args (fnil conj []) k)))))))

;; Hàm main - entry point của CLI
(defn -main [& args]
  (let [;; Tìm file form trong tất cả arguments (không phải option)
        form-file (first (filter #(and (not (str/starts-with? % "--")) 
                                   (not (str/includes? % ":"))
                                   (str/ends-with? % ".json")) args))
        ;; Parse options từ tất cả arguments
        {:keys [values-file kv-args output-file]} (parse-options args)
        kv-values (parse-kv-args kv-args)
        ;; Load giá trị mặc định từ file JSON nếu có
        json-values (if values-file
                      (json/parse-string (slurp values-file) true)
                      {})
        ;; Merge các giá trị từ command line và file JSON
        prefilled (merge json-values kv-values)
        output-path (or output-file "result.json")]
    
    ;; Kiểm tra xem có file form không
    (if-not form-file
      (do (println "❌ Vui lòng nhập đường dẫn tới form.json") 
          (println "Cách sử dụng: bb-form <form.json> [--values <values.json>] [--out <output.json>]")
          (System/exit 1))
      (let [form (json/parse-string (slurp (io/file form-file)) true)]
        ;; Khởi tạo atom với :selectedByUser là map rỗng, sau đó merge prefilled
        (reset! answers {:selectedByUser {}})
        (swap! answers update :selectedByUser merge prefilled)
        ;; Chạy form
        (run-form form)
        ;; Tạo thư mục nếu chưa tồn tại (chỉ khi có path)
        (let [output-file (io/file output-path)
              parent-dir (.getParentFile output-file)]
          (when parent-dir
            (.mkdirs parent-dir)))
        ;; Lưu kết quả ra file JSON
        (spit output-path (json/generate-string @answers {:pretty true}))
        (println (str "\n💾 Đã lưu kết quả vào " output-path))))))

;; Gọi hàm main với command line arguments
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*)) (ns user (:require [com.drbinhthanh.bb-form])) (apply com.drbinhthanh.bb-form/-main *command-line-args*)