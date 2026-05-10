(ns com.drbinhthanh.bb-form
  (:require [babashka.process :refer [shell]]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

;; Atom để lưu trữ tất cả câu trả lời của form
(def answers (atom {:selectedByUser {} :HiddenVar {}}))
;; Atom cho dòng trạng thái/thông báo lỗi
(def status-line (atom ""))
;; Atom để track các hidden vars của các stage đã qua (Scope giới hạn)
(def past-hidden-vars (atom #{}))
(def current-stage-fields (atom []))
(def executed-actions (atom #{}))

;; Registry lưu trữ các hàm được compile từ file formula (.edn)
(def formula-registry (atom {}))

;; Registry lưu trữ alias của các namespace (dùng cho cú pháp :as)
(def ns-aliases (atom {}))

;; Hàm nạp công thức từ danh sách file import
(defn load-formulas! [import-paths cwd]
  (doseq [import-decl import-paths]
    (try
      (let [[path _ alias-key] (if (vector? import-decl) import-decl [import-decl nil nil])
            file (io/file cwd path)
            actual-ns (if (str/ends-with? path ".clj")
                        ;; Nếu là file Clojure chuẩn
                        (do
                          (println "📦 Đang nạp thư viện Clojure:" path)
                          (load-file (str file))
                          ;; Trích xuất namespace từ file clj bằng regex
                          (second (re-find #"\(ns\s+([\w\.\-]+)" (slurp file))))
                        ;; Nếu là file EDN formula
                        (let [data (edn/read-string (slurp file))
                              ns-name (:ns data)
                              consts (:consts data)
                              fns (:fns data)]
                          (when (and ns-name fns)
                            (println "📦 Đang nạp thư viện EDN:" path)
                            (let [compiled-fns (into {}
                                                     (for [[k v] fns]
                                                       [k (eval `(let [~'consts '~consts] ~v))]))]
                              (swap! formula-registry assoc ns-name compiled-fns)))
                          (name ns-name)))]
        ;; Lưu alias nếu có
        (when (and alias-key actual-ns)
          (swap! ns-aliases assoc (name alias-key) actual-ns)))
      (catch Exception e
        (println "⚠️ Lỗi khi nạp công thức từ cấu trúc:" import-decl)
        (println "Chi tiết lỗi:" (.getMessage e))))))


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

;; Declaration of eval-expr to use in interpolate-string
(declare eval-expr)

;; Hàm nội suy chuỗi: thay thế {{var_name}} hoặc {{[:var :name]}} bằng giá trị thực
(defn interpolate-string [s context]
  (if (string? s)
    (str/replace s #"\{\{(.+?)\}\}"
                 (fn [[_ content]]
                   (let [trimmed (str/trim content)
                         expr (try (edn/read-string trimmed)
                                   (catch Exception _ trimmed))]
                     (if (vector? expr)
                       (str (eval-expr expr context))
                       (str (eval-expr [:var (keyword trimmed)] context))))))
    s))

;; Hàm giải quyết nhãn động (Dynamic Label)
(defn resolve-label [label-def context]
  (let [raw-text (cond
                   (string? label-def) label-def
                   (vector? label-def) (->> label-def
                                            (filter #(or (nil? (:show-if %))
                                                         (eval-expr (:show-if %) context)))
                                            first
                                            :text)
                   :else (str label-def))]
    (interpolate-string raw-text context)))

;; Kiểm tra xem field đã có giá trị chưa (do được truyền qua CLI hoặc file JSON)
(defn should-skip? [id]
  (some? (get-in @answers [:selectedByUser (keyword id)])))

;; Lấy giá trị prefilled của field
(defn get-prefilled [id]
  (get-in @answers [:selectedByUser (keyword id)]))

;; Hàm parse giá trị theo kiểu dữ liệu
(defn parse-value [v type]
  (case type
    "number" (try (Integer/parseInt (str v)) (catch Exception _ v))
    :number  (try (Integer/parseInt (str v)) (catch Exception _ v))
    "text"   (str v)
    :text    (str v)
    "date"   (let [s (str v)]
                 (if (re-matches #"^\d{2}-\d{2}-\d{4}$" s)
                   (try
                     (let [dt (java.time.LocalDate/parse s (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy"))]
                       (.format dt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd")))
                     (catch Exception _ s))
                   s))
    :date    (let [s (str v)]
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



;; -------------------------------
;; Regex helper — hỗ trợ cả EDN Pattern và String
;; -------------------------------

;; Chuyển regex từ EDN Pattern hoặc String sang java.util.regex.Pattern
(defn ->pattern [regex]
  (cond
    (instance? java.util.regex.Pattern regex) regex
    (string? regex) (re-pattern regex)
    :else nil))

;; -------------------------------
;; EDN Logic Engine
;; -------------------------------

(defn eval-expr [expr context]
  (if-not (vector? expr)
    expr
    (let [[op & args] expr]
      (case op
        ;; Truy xuất biến trạng thái
        :var (let [var-key (keyword (first args))]
               ;; 1. Block truy xuất nếu đây là local hidden var của stage trước
               (if (contains? @past-hidden-vars var-key)
                 nil
                 ;; 2. Ưu tiên biến toàn cục (HiddenVar)
                 (if-let [[_ val] (find (:HiddenVar context) var-key)]
                   val
                   ;; 3. Fallback lấy biến người dùng (selectedByUser)
                   (get-in context [:selectedByUser var-key]))))
        
        ;; Truy xuất thuộc tính của object (Map)
        :get (let [obj (eval-expr (first args) context)
                   prop (eval-expr (second args) context)
                   prop-key (if (or (string? prop) (keyword? prop)) (keyword prop) prop)]
               (if (map? obj)
                 (get obj prop-key)
                 nil))
        
        ;; Toán tử logic
        :and (every? #(eval-expr % context) args)
        :or  (boolean (some #(eval-expr % context) args))
        :not (not (eval-expr (first args) context))
        
        ;; Toán tử so sánh (ép kiểu nil thành 0 cho các phép so sánh số)
        :=   (apply = (map #(eval-expr % context) args))
        :!=  (apply not= (map #(eval-expr % context) args))
        :>   (apply > (map #(or (eval-expr % context) 0) args))
        :<   (apply < (map #(or (eval-expr % context) 0) args))
        :>=  (apply >= (map #(or (eval-expr % context) 0) args))
        :<=  (apply <= (map #(or (eval-expr % context) 0) args))
        
        ;; Điều kiện rẽ nhánh
        :if  (if (eval-expr (first args) context)
               (eval-expr (second args) context)
               (eval-expr (nth args 2) context))
        
        ;; Toán học (ép kiểu nil thành 0)
        :+   (apply + (map #(or (eval-expr % context) 0) args))
        :-   (apply - (map #(or (eval-expr % context) 0) args))
        :*   (apply * (map #(or (eval-expr % context) 0) args))
        :/   (apply / (map #(or (eval-expr % context) 0) args))
        :mod (mod (or (eval-expr (first args) context) 0) 
                  (or (eval-expr (second args) context) 1)) ;; tránh chia cho 0
        
        ;; Chuỗi
        :str/includes?  (str/includes? (str (eval-expr (first args) context)) (str (eval-expr (second args) context)))
        :str/lower-case (str/lower-case (str (eval-expr (first args) context)))
        :str/upper-case (str/upper-case (str (eval-expr (first args) context)))
        
        ;; Tập hợp
        :count  (count (eval-expr (first args) context))
        :first  (first (eval-expr (first args) context))
        :concat (apply concat (map #(eval-expr % context) args))
        :array  (vec (map #(eval-expr % context) args))
        
        :contains? (let [coll (eval-expr (first args) context)
                         item (eval-expr (second args) context)]
                     (if (set? coll)
                       (contains? coll item)
                       (some? (some #{item} coll))))
        
        ;; Function Call from Registry or Clojure Environment
        :call (let [[fn-path & fn-args] args
                    [raw-ns fn-name] (if (keyword? fn-path)
                                       [(namespace fn-path) (name fn-path)]
                                       [(namespace (str fn-path)) (name (str fn-path))])
                    ;; Tra cứu alias (nếu có), nếu không dùng nguyên gốc
                    ns-name (get @ns-aliases raw-ns raw-ns)
                    evaluated-args (map #(eval-expr % context) fn-args)]
                ;; Ưu tiên resolve hàm Clojure chuẩn trước
                (if-let [f-var (and ns-name fn-name (resolve (symbol ns-name fn-name)))]
                  (apply @f-var evaluated-args)
                  ;; Fallback tìm trong EDN formula registry
                  (if-let [f (get-in @formula-registry [(keyword ns-name) (keyword fn-name)])]
                    (apply f evaluated-args)
                    (throw (ex-info (str "Hàm không tồn tại trong namespace hoặc registry: " fn-path)
                                    {:ns ns-name :fn fn-name :raw-ns raw-ns})))))
        
        ;; Mặc định
        :default true
        
        ;; Nếu không hiểu toán tử thì trả về false
        false))))

;; -------------------------------
;; Actions Engine
;; -------------------------------

(defn eval-action [action context-atom]
  (let [[op & args] action]
    (case op
      :set   (let [var-name (first args)
                   expr (second args)
                   val (eval-expr expr @context-atom)]
               ;; [:set] luôn tác động vào Global Variables (:HiddenVar)
               (swap! context-atom assoc-in [:HiddenVar (keyword var-name)] val))
      :print (let [msg (eval-expr (first args) @context-atom)]
               (println (str "\nℹ️ " msg))
               (shell {:out :inherit} "gum" "input" "--placeholder" "Nhấn Enter để tiếp tục..."))
      nil)))

(defn eval-actions [actions context-atom]
  (doseq [act actions]
    (eval-action act context-atom)))

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
(defmulti ask-field (fn [field form] (keyword (:type field))))

;; Method xử lý field :hidden (không UI, tự tính toán)
(defmethod ask-field :hidden [{:keys [id value]} form]
  ;; Xử lý hidden được bọc trực tiếp trong run-form để phát hiện thay đổi
  nil)

;; Method xử lý field :info (Chỉ hiển thị thông tin, không yêu cầu nhập)
(defmethod ask-field :info [{:keys [id label]} form]
  (let [id-k (keyword id)
        resolved-label (resolve-label label @answers)]
    (if (should-skip? id)
      ;; Nếu được skip (CLI --values), tự động lưu kết quả text vào kết quả
      (swap! answers assoc-in [:selectedByUser id-k] resolved-label)
      (do
        (println "\n" resolved-label)
        (shell {:out :inherit} "gum" "input" "--placeholder" "Nhấn Enter để tiếp tục...")
        (swap! answers assoc-in [:selectedByUser id-k] resolved-label)))))

;; Method xử lý field text với regex validation
(defmethod ask-field :text [{:keys [id label required regex regexError]} form]
  (let [id-k    (keyword id)
        ;; Hỗ trợ cả EDN #"..." Pattern lẫn JSON string regex
        pattern (->pattern regex)
        resolved-label (resolve-label label @answers)
        value   (if (should-skip? id)
                  (get-prefilled id)
                  (loop []
                    (let [v (gum-input resolved-label)]
                      ;; Kiểm tra regex nếu có
                      (if (and pattern (not (re-matches pattern v)))
                        (do (set-status! (or regexError (str "Giá trị không khớp với regex: " regex))) (clear-screen) (render-header form) (recur))
                        (do (clear-status!) (clear-screen) (render-header form) v)))))]
    ;; Chỉ lưu giá trị nếu field không bắt buộc hoặc có giá trị
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers assoc-in [:selectedByUser id-k] (parse-value value "text")))))

;; Method xử lý field number với validation số nguyên
(defmethod ask-field :number [{:keys [id label required]} form]
  (let [id-k (keyword id)
        resolved-label (resolve-label label @answers)
        value (if (should-skip? id)
                (get-prefilled id)
                (loop []
                  (let [v (gum-input resolved-label)]
                    ;; Kiểm tra xem có phải số nguyên không
                    (if (or (not required)
                            (try (Integer/parseInt v) true (catch Exception _ false)))
                      (do (clear-status!) (clear-screen) (render-header form) v)
                      (do (set-status! "⚠️ Vui lòng nhập số nguyên!") (clear-screen) (render-header form) (recur))))))]
    ;; Chỉ lưu giá trị nếu field không bắt buộc hoặc có giá trị
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers assoc-in [:selectedByUser id-k] (parse-value value "number")))))

;; Method xử lý field date với validation và gõ tắt
(defmethod ask-field :date [{:keys [id label required]} form]
  (let [id-k (keyword id)
        resolved-label (resolve-label label @answers)
        value (if (should-skip? id)
                (get-prefilled id)
                (loop []
                  (let [v (gum-input (str resolved-label " (DD-MM-YYYY hoặc gõ tắt: 04, 1204)"))]
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
      (swap! answers assoc-in [:selectedByUser id-k] (parse-value value "date")))))

;; Method xử lý field select (dropdown chọn một)
(defmethod ask-field :select [{:keys [id label options]} form]
  (let [id-k  (keyword id)
        resolved-label (resolve-label label @answers)
        ;; Chuẩn hóa options: hỗ trợ cả EDN keyword/string lẫn JSON string
        opts  (mapv normalize-str options)
        value (if (should-skip? id)
                (get-prefilled id)
                (gum-select resolved-label opts))]
    ;; Lưu giá trị được chọn
    (swap! answers assoc-in [:selectedByUser id-k] value)))

;; Method xử lý field radio — alias sang :select
(defmethod ask-field :radio [field form]
  (ask-field (assoc field :type :select) form))

;; Method xử lý field multiselect (chọn nhiều)
(defmethod ask-field :multiselect [{:keys [id label options]} form]
  (let [id-k    (keyword id)
        resolved-label (resolve-label label @answers)
        opts    (mapv normalize-str options)
        raw     (if (should-skip? id)
                  (get-prefilled id)
                  (gum-multiselect resolved-label opts))
        ;; Chuẩn hóa kết quả multiselect
        choices (cond
                  (string? raw) [raw]
                  (sequential? raw) raw
                  :else [])]
    ;; Lưu danh sách các lựa chọn
    (swap! answers assoc-in [:selectedByUser id-k] choices)))

;; -------------------------------
;; Entry point
;; -------------------------------

;; Hàm chính để chạy form
(defn run-form [form]
  (clear-screen)
  (render-header form)
  (reset! past-hidden-vars #{})
  (reset! executed-actions #{})
  
  (let [stages (or (:stages form) [{:fields (:fields form)}])]
    (doseq [stage stages]
      ;; 1. Thực thi hooks on-begin của stage
      (when (:on-begin stage)
        (eval-actions (:on-begin stage) answers))
      
      (reset! current-stage-fields (:fields stage))
      
      ;; 2. Chạy vòng lặp Restarting Loop cho nội bộ stage
      (loop []
        (let [answered-in-pass
              (reduce (fn [count field]
                        (let [id (:id field)
                              type (keyword (:type field))
                              condition (:show-if field)]
                          
                          ;; Chỉ xử lý nếu thoả mãn show-if
                          (if (or (nil? condition)
                                  (eval-expr condition @answers))
                            
                            (if (= type :hidden)
                              ;; --- XỬ LÝ BIẾN ẨN ĐỊA PHƯƠNG ---
                              (let [id-k (keyword id)
                                    new-val (eval-expr (:value field) @answers)
                                    old-val (get-in @answers [:selectedByUser id-k])]
                                (if (not= new-val old-val)
                                  (do
                                    (swap! answers assoc-in [:selectedByUser id-k] new-val)
                                    ;; Trigger actions nếu có
                                    (when (:actions field)
                                      (eval-actions (:actions field) answers))
                                    (inc count))
                                  count))
                              
                              ;; --- XỬ LÝ CÂU HỎI THƯỜNG ---
                              (let [answered? (should-skip? id)]
                                (if (not answered?)
                                  (do
                                    (ask-field field form)
                                    (when (:actions field)
                                      (eval-actions (:actions field) answers))
                                    (swap! executed-actions conj id)
                                    (inc count))
                                  (do
                                    (when (= type :info)
                                      (swap! answers assoc-in [:selectedByUser (keyword id)] (resolve-label (:label field) @answers)))
                                    (when (and (:actions field) (not (contains? @executed-actions id)))
                                      (eval-actions (:actions field) answers)
                                      (swap! executed-actions conj id))
                                    count))))
                            
                            ;; Không thoả mãn show-if
                            count)))
                      0
                      (:fields stage))]
          
          ;; Nếu có field mới được hỏi/tính toán, lặp lại
          (when (> answered-in-pass 0)
            (recur))))
      
      ;; 3. Thực thi hooks on-complete của stage
      (when (:on-complete stage)
        (eval-actions (:on-complete stage) answers))
      
      ;; 4. Khoá các biến địa phương (local hidden vars) của stage này
      (let [hidden-ids (->> (:fields stage)
                            (filter #(= (keyword (:type %)) :hidden))
                            (map :id)
                            (map keyword)
                            set)]
        (swap! past-hidden-vars clojure.set/union hidden-ids)))))

;; -------------------------------
;; Form loader — hỗ trợ EDN (ưu tiên) và JSON (tương thích ngược)
;; -------------------------------

(defn load-form [file-path]
  (let [content (slurp (io/file file-path))]
    (cond
      (str/ends-with? file-path ".edn") (edn/read-string content)
      (str/ends-with? file-path ".json") (json/parse-string content true)
      :else (throw (ex-info (str "Định dạng file không được hỗ trợ: " file-path
                                 "\nHỗ trợ: .edn (khuyến nghị), .json (tương thích ngược)")
                            {:file file-path})))))

;; -------------------------------
;; Output serializer — EDN (mặc định) hoặc JSON (tương thích ngược)
;; -------------------------------

;; Phát hiện format từ đuôi file hoặc flag tường minh
(defn detect-format
  "Trả về :edn hoặc :json dựa trên:
   1. force-format được truyền vào (chuỗi \"json\" hoặc \"edn\") → format đó
   2. output-path có đuôi .json → :json
   3. Mặc định → :edn"
  [output-path force-format]
  (cond
    (= force-format "edn")               :edn
    (= force-format "json")              :json
    (str/ends-with? output-path ".json") :json
    :else                                :edn))

;; Ghi kết quả ra file theo format phát hiện được
(defn write-output! [data output-path format]
  (case format
    :edn  (spit output-path (with-out-str (pprint/pprint data)))
    :json (spit output-path (json/generate-string data {:pretty true}))))

;; -------------------------------
;; CLI entry point
;; -------------------------------

;; Hàm parse các tham số key:value từ command line
(defn parse-kv-args [args]
  (->>  args
        (filter #(str/includes? % ":"))
        (map #(str/split % #":" 2))
        (map (fn [[k v]] [(keyword k) v]))
        (into {})))

;; Hàm parse các options từ command line (--values, --out, --format)
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

          ;; Parse option --format (json | edn)
          (= k "--format")
          (if (empty? rest)
            (do (println "❌ Thiếu giá trị sau --format (json hoặc edn)") (System/exit 1))
            (let [fmt (str/lower-case (first rest))]
              (when-not (#{ "json" "edn"} fmt)
                (println (str "❌ --format chỉ nhận \"json\" hoặc \"edn\", nhận được: " fmt))
                (System/exit 1))
              (recur (drop 1 rest)
                     (assoc opts :format fmt))))

          ;; Báo lỗi option không hỗ trợ
          (str/starts-with? k "--")
          (do (println (str "❌ Option không được hỗ trợ: " k)) (System/exit 1))

          :else
          (recur rest
                 (update opts :kv-args (fnil conj []) k)))))))

;; Hàm main - entry point của CLI
(defn -main [& args]
  (let [;; Tìm file form: ưu tiên .edn, fallback .json
        form-file   (first (filter #(and (not (str/starts-with? % "--"))
                                         (not (str/includes? % ":"))
                                         (or (str/ends-with? % ".edn")
                                             (str/ends-with? % ".json"))) args))
        ;; Parse options từ tất cả arguments
        {:keys [values-file kv-args output-file format]} (parse-options args)
        kv-values   (parse-kv-args kv-args)
        ;; Load giá trị mặc định từ values file (EDN)
        edn-values  (if values-file
                      (edn/read-string (slurp values-file))
                      {})
        ;; Merge các giá trị từ command line và file
        prefilled   (merge edn-values kv-values)
        ;; Mặc định xuất EDN; tự phát hiện JSON từ đuôi .json hoặc --format json
        output-path (or output-file "result.edn")
        out-format  (detect-format output-path format)]

    ;; Kiểm tra xem có file form không
    (if-not form-file
      (do (println "❌ Vui lòng nhập đường dẫn tới file form")
          (println (str "Cách sử dụng: bb-form <form.edn|form.json>"
                        " [--values <values.edn>]"
                        " [--out <output.edn|output.json>]"
                        " [--format json|edn]"))
          (System/exit 1))
      (let [form (load-form form-file)]
        ;; Nạp formulas nếu form có cấu trúc :import
        (when-let [imports (:import form)]
          (let [form-dir (if (.getParent (io/file form-file))
                           (.getParent (io/file form-file))
                           ".")]
            (load-formulas! imports form-dir)))

        ;; Khởi tạo atom với cả 2 block: selectedByUser và HiddenVar (load từ default :variables)
        (reset! answers {:selectedByUser {}
                         :HiddenVar (get form :variables {})})
        (swap! answers update :selectedByUser merge prefilled)
        ;; Chạy form
        (run-form form)
        ;; Tạo thư mục output nếu chưa tồn tại
        (let [out-file   (io/file output-path)
              parent-dir (.getParentFile out-file)]
          (when parent-dir (.mkdirs parent-dir)))
        ;; Ghi kết quả — EDN mặc định, JSON nếu yêu cầu tường minh
        (write-output! @answers output-path out-format)
        (println (str "\n💾 Đã lưu kết quả " (name out-format) " vào " output-path))))))

;; Gọi hàm main với command line arguments
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))