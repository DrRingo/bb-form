(ns com.drbinhthanh.bb-form.core
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.set :as set]))

;; Atoms for managing form state
(def answers (atom {:selectedByUser {} :HiddenVar {}}))
(def status-line (atom ""))
(def past-hidden-vars (atom #{}))
(def current-stage-fields (atom []))
(def executed-actions (atom #{}))

;; Formula and namespace registries
(def formula-registry (atom {}))
(def ns-aliases (atom {}))

(defn set-status! [msg]
  (reset! status-line msg))

(defn clear-status! []
  (reset! status-line ""))

(defn load-theme [default-theme file-path]
  (if (and file-path (.exists (io/file file-path)))
    (try
      (let [custom (edn/read-string (slurp file-path))]
        (merge-with merge default-theme custom))
      (catch Exception e
        (println "⚠️ Lỗi khi nạp theme tùy chỉnh từ file:" file-path)
        (println "Chi tiết:" (.getMessage e))
        default-theme))
    default-theme))

;; Load formulas from file imports
(defn load-formulas! [import-paths cwd]
  (doseq [import-decl import-paths]
    (try
      (let [[path _ alias-key] (if (vector? import-decl) import-decl [import-decl nil nil])
            file (io/file cwd path)
            actual-ns (if (str/ends-with? path ".clj")
                        (do
                          (println "📦 Đang nạp thư viện Clojure:" path)
                          (load-file (str file))
                          (second (re-find #"\(ns\s+([\w\.\-]+)" (slurp file))))
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
        (when (and alias-key actual-ns)
          (swap! ns-aliases assoc (name alias-key) actual-ns)))
      (catch Exception e
        (println "⚠️ Lỗi khi nạp công thức từ cấu trúc:" import-decl)
        (println "Chi tiết lỗi:" (.getMessage e))))))

;; Normalize values for string comparisons / dropdown display
(defn normalize-str [v]
  (cond
    (keyword? v) (name v)
    (symbol? v)  (name v)
    :else (str v)))

(defn normalize-branch-key [v]
  (-> v normalize-str str/trim str/lower-case))

(declare eval-expr)

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

(defn should-skip? [id]
  (some? (get-in @answers [:selectedByUser (keyword id)])))

(defn get-prefilled [id]
  (get-in @answers [:selectedByUser (keyword id)]))

(defn today []
  (let [now (java.time.LocalDate/now)]
    (.format now (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy"))))

(defn current-month-year []
  (let [now (java.time.LocalDate/now)]
    {:month (.getMonthValue now)
     :year (.getYear now)}))

(defn expand-date-shortcut [input]
  (let [trimmed (str/trim input)
        {:keys [month year]} (current-month-year)]
    (cond
      ;; 8 digits: DDMMYYYY
      (re-matches #"^\d{8}$" trimmed)
      (let [dd (subs trimmed 0 2)
            mm (subs trimmed 2 4)
            yyyy (subs trimmed 4 8)]
        (format "%s-%s-%s" dd mm yyyy))

      ;; DDMM[+-]N (e.g. 2304-1)
      (re-matches #"^(\d{2})(\d{2})([+-])(\d+)$" trimmed)
      (let [[_ dd-str mm-str sign-str n-str] (re-matches #"^(\d{2})(\d{2})([+-])(\d+)$" trimmed)
            dd (Integer/parseInt dd-str)
            mm (Integer/parseInt mm-str)
            n (Integer/parseInt n-str)
            resolved-year (if (= sign-str "+") (+ year n) (- year n))]
        (format "%02d-%02d-%d" dd mm resolved-year))

      ;; DD[+-]N (e.g. 23+10)
      (re-matches #"^(\d{2})([+-])(\d+)$" trimmed)
      (let [[_ dd-str sign-str n-str] (re-matches #"^(\d{2})([+-])(\d+)$" trimmed)
            dd (Integer/parseInt dd-str)
            n (Integer/parseInt n-str)
            max-len (-> (java.time.YearMonth/of year month) .lengthOfMonth)
            clamped-dd (max 1 (min dd max-len))
            base-date (java.time.LocalDate/of year month clamped-dd)
            resolved-date (if (= sign-str "+")
                            (.plusDays base-date n)
                            (.minusDays base-date n))
            resolved-day (.getDayOfMonth resolved-date)
            resolved-month (.getMonthValue resolved-date)
            resolved-year (.getYear resolved-date)]
        (format "%02d-%02d-%d" resolved-day resolved-month resolved-year))

      ;; DDMM (e.g. 2304)
      (re-matches #"^\d{4}$" trimmed)
      (let [dd (Integer/parseInt (subs trimmed 0 2))
            mm (Integer/parseInt (subs trimmed 2 4))]
        (format "%02d-%02d-%d" dd mm year))

      ;; DD (e.g. 23)
      (re-matches #"^\d{2}$" trimmed)
      (let [dd (Integer/parseInt trimmed)]
        (format "%02d-%02d-%d" dd month year))

      ;; DD-MM-YYYY or DD/MM/YYYY
      (re-matches #"^\d{2}[-/]\d{2}[-/]\d{4}$" trimmed)
      (str/replace trimmed #"[/]" "-")

      :else trimmed)))

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

(defn valid-time-str? [time-str]
  (if-let [m (re-matches #"^(?i)(?:h)?(\d{1,2})(?:[h:])?(\d{2})(?:[+-]\d+)?$" time-str)]
    (let [h (Integer/parseInt (nth m 1))
          min (Integer/parseInt (nth m 2))]
      (and (<= 0 h 23) (<= 0 min 59)))
    false))

(defn valid-datetime-input? [input type]
  (let [trimmed (str/trim (or input ""))
        parts (if (str/blank? trimmed) [] (str/split trimmed #"\s+"))]
    (cond
      (str/blank? trimmed) true
      
      (= (count parts) 2)
      (let [d-part (first parts)
            t-part (second parts)]
        (and (valid-date? (expand-date-shortcut d-part))
             (valid-time-str? t-part)))
             
      (= (count parts) 1)
      (let [p (first parts)]
        (if (or (str/includes? p ":")
                (str/includes? p "h")
                (str/includes? p "H"))
          (and (#{:datetime "datetime"} (keyword type))
               (valid-time-str? p))
          (and (#{:date :datetime "date" "datetime"} (keyword type))
               (valid-date? (expand-date-shortcut p)))))
               
      :else false)))

(defn normalize-datetime [input type]
  (let [trimmed (str/trim (or input ""))
        parts (if (str/blank? trimmed) [] (str/split trimmed #"\s+"))
        {:keys [date-str time-str]}
        (cond
          (= (count parts) 2)
          {:date-str (first parts) :time-str (second parts)}
          
          (= (count parts) 1)
          (let [p (first parts)]
            (if (or (str/includes? p ":")
                    (str/includes? p "h")
                    (str/includes? p "H"))
              {:date-str nil :time-str p}
              {:date-str p :time-str nil}))
              
          :else
          {:date-str nil :time-str nil})
          
        time-pattern #"^(?i)(?:h)?(\d{1,2})(?:[h:])?(\d{2})(?:([+-])(\d+))?$"
        time-match (when time-str (re-matches time-pattern time-str))
        
        [hour min sign offset-days]
        (if time-match
          (let [[_ h-str m-str sgn-str off-str] time-match]
            [(Integer/parseInt h-str)
             (Integer/parseInt m-str)
             sgn-str
             (if off-str (Integer/parseInt off-str) 0)])
          (if (and (= (keyword type) :date) (not time-str))
            [0 0 nil 0]
            (let [now (java.time.LocalTime/now)]
              [(.getHour now) (.getMinute now) nil 0])))
              
        resolved-date-str (if date-str
                            (expand-date-shortcut date-str)
                            (today))
        
        base-date (try
                    (if (re-matches #"^\d{4}-\d{2}-\d{2}$" resolved-date-str)
                      (java.time.LocalDate/parse resolved-date-str (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd"))
                      (java.time.LocalDate/parse resolved-date-str (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy")))
                    (catch Exception _
                      (java.time.LocalDate/now)))
                      
        final-date (if (and sign (> offset-days 0))
                     (if (= sign "-")
                       (.minusDays base-date offset-days)
                       (.plusDays base-date offset-days))
                     base-date)
                     
        formatted-date (.format final-date (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd"))
        formatted-time (format "%02d:%02d" hour min)]
    (str formatted-date " " formatted-time)))

(defn parse-value [v type]
  (case type
    ("number" :number)     (try (Integer/parseInt (str v)) (catch Exception _ v))
    ("text" :text)         (str v)
    ("date" :date)         (normalize-datetime v :date)
    ("datetime" :datetime) (normalize-datetime v :datetime)
    v))

;; Returns the auto-fill value for a field in marathon mode
(defn get-marathon-default [field]
  (let [type             (keyword (:type field))
        explicit-default (:default field)]
    (cond
      ;; info/hidden: bỏ qua - xử lý riêng trong run-terminal-form
      (#{:info :hidden} type) nil

      ;; nếu có :default tường minh ⇒ parse và trả về
      (some? explicit-default) (parse-value explicit-default (name type))

      ;; select / radio: lấy option đầu tiên
      (#{:select :radio} type)
      (first (mapv normalize-str (:options field)))

      ;; multiselect: vector chứa option đầu tiên
      (= type :multiselect)
      (when-let [first-opt (first (mapv normalize-str (:options field)))]
        [first-opt])

      (= type :text)     ""
      (= type :number)   0
      (= type :date)     (today)
      (= type :datetime) (let [now (java.time.LocalDateTime/now)]
                           (.format now (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy HH:mm")))

      :else nil)))
(defn ->pattern [regex]
  (cond
    (instance? java.util.regex.Pattern regex) regex
    (string? regex) (re-pattern regex)
    :else nil))

;; EDN logic evaluation engine
(defn eval-expr [expr context]
  (if-not (vector? expr)
    expr
    (let [[op & args] expr]
      (case op
        :var (let [var-key (keyword (first args))]
               (if (contains? @past-hidden-vars var-key)
                 nil
                 (if-let [[_ val] (find (:HiddenVar context) var-key)]
                   val
                   (get-in context [:selectedByUser var-key]))))
        :get (let [obj (eval-expr (first args) context)
                   prop (eval-expr (second args) context)
                   prop-key (if (or (string? prop) (keyword? prop)) (keyword prop) prop)]
               (if (map? obj)
                 (get obj prop-key)
                 nil))
        :and (every? #(eval-expr % context) args)
        :or  (boolean (some #(eval-expr % context) args))
        :not (not (eval-expr (first args) context))
        :=   (apply = (map #(eval-expr % context) args))
        :!=  (apply not= (map #(eval-expr % context) args))
        :>   (apply > (map #(or (eval-expr % context) 0) args))
        :<   (apply < (map #(or (eval-expr % context) 0) args))
        :>=  (apply >= (map #(or (eval-expr % context) 0) args))
        :<=  (apply <= (map #(or (eval-expr % context) 0) args))
        :if  (if (eval-expr (first args) context)
               (eval-expr (second args) context)
               (eval-expr (nth args 2) context))
        :+   (apply + (map #(or (eval-expr % context) 0) args))
        :-   (apply - (map #(or (eval-expr % context) 0) args))
        :*   (apply * (map #(or (eval-expr % context) 0) args))
        :/   (apply / (map #(or (eval-expr % context) 0) args))
        :mod (mod (or (eval-expr (first args) context) 0)
                  (or (eval-expr (second args) context) 1))
        :str/includes?  (str/includes? (str (eval-expr (first args) context)) (str (eval-expr (second args) context)))
        :str/lower-case (str/lower-case (str (eval-expr (first args) context)))
        :str/upper-case (str/upper-case (str (eval-expr (first args) context)))
        :count  (count (eval-expr (first args) context))
        :first  (first (eval-expr (first args) context))
        :concat (apply concat (map #(eval-expr % context) args))
        :array  (vec (map #(eval-expr % context) args))
        :contains? (let [coll (eval-expr (first args) context)
                          item (eval-expr (second args) context)]
                     (if (set? coll)
                       (contains? coll item)
                       (some? (some #{item} coll))))
        :call (let [[fn-path & fn-args] args
                    [raw-ns fn-name] (if (keyword? fn-path)
                                       [(namespace fn-path) (name fn-path)]
                                       [(namespace (str fn-path)) (name (str fn-path))])
                    ns-name (get @ns-aliases raw-ns raw-ns)
                    evaluated-args (map #(eval-expr % context) fn-args)]
                (if-let [f-var (and ns-name fn-name (resolve (symbol ns-name fn-name)))]
                  (apply @f-var evaluated-args)
                  (if-let [f (get-in @formula-registry [(keyword ns-name) (keyword fn-name)])]
                    (apply f evaluated-args)
                    (throw (ex-info (str "Hàm không tồn tại trong namespace hoặc registry: " fn-path)
                                    {:ns ns-name :fn fn-name :raw-ns raw-ns})))))
        :default true
        false))))

;; Actions engine
(defn eval-action [action context-atom ui-adapter]
  (let [[op & args] action]
    (case op
      :set   (let [var-name (first args)
                   expr (second args)
                   val (eval-expr expr @context-atom)]
               (swap! context-atom assoc-in [:HiddenVar (keyword var-name)] val))
      :print (let [msg (eval-expr (first args) @context-atom)]
               ((:pause ui-adapter) msg))
      nil)))

(defn eval-actions [actions context-atom ui-adapter]
  (doseq [act actions]
    (eval-action act context-atom ui-adapter)))

;; Shared Terminal Form Runner Loop
(defn run-terminal-form [form answers-atom ui-adapter]
  ((:clear-screen ui-adapter))
  ((:render-header ui-adapter) form @status-line)
  (reset! past-hidden-vars #{})
  (reset! executed-actions #{})
  
  (let [stages (or (:stages form) [{:fields (:fields form)}])]
    (doseq [stage stages]
      (when (:on-begin stage)
        (eval-actions (:on-begin stage) answers-atom ui-adapter))
      
      (reset! current-stage-fields (:fields stage))
      
      (loop []
        (let [answered-in-pass
              (reduce (fn [count field]
                        (let [id (:id field)
                              type (keyword (:type field))
                              condition (:show-if field)]
                          (if (or (nil? condition)
                                  (eval-expr condition @answers-atom))
                            (if (= type :hidden)
                              (let [id-k (keyword id)
                                    new-val (eval-expr (:value field) @answers-atom)
                                    old-val (get-in @answers-atom [:selectedByUser id-k])]
                                (if (not= new-val old-val)
                                  (do
                                    (swap! answers-atom assoc-in [:selectedByUser id-k] new-val)
                                    (when (:actions field)
                                      (eval-actions (:actions field) answers-atom ui-adapter))
                                    (inc count))
                                  count))
                              (let [answered? (should-skip? id)]
                                (if-not answered?
                                  (if (:marathon? ui-adapter)
                                    ;; Marathon mode: tự điền giá trị mặc định, không tương tác
                                    (let [id-k     (keyword id)
                                          auto-val (if (= type :info)
                                                     (resolve-label (:label field) @answers-atom)
                                                     (get-marathon-default field))]
                                      (when (some? auto-val)
                                        (swap! answers-atom assoc-in [:selectedByUser id-k] auto-val))
                                      (when (:actions field)
                                        (eval-actions (:actions field) answers-atom ui-adapter))
                                      (swap! executed-actions conj id)
                                      (inc count))
                                    ;; Normal mode: hỏi người dùng
                                    (do
                                      ((:ask-field ui-adapter) field form answers-atom)
                                      (when (:actions field)
                                        (eval-actions (:actions field) answers-atom ui-adapter))
                                      (swap! executed-actions conj id)
                                      (inc count)))
                                  (do
                                    (let [id-k (keyword id)
                                          val (get-in @answers-atom [:selectedByUser id-k])]
                                      (when (some? val)
                                        (swap! answers-atom assoc-in [:selectedByUser id-k] (parse-value val type))))
                                    (when (= type :info)
                                      (swap! answers-atom assoc-in [:selectedByUser (keyword id)] (resolve-label (:label field) @answers-atom)))
                                    (when (and (:actions field) (not (contains? @executed-actions id)))
                                      (eval-actions (:actions field) answers-atom ui-adapter)
                                      (swap! executed-actions conj id))
                                    count))))
                            count)))
                      0
                      (:fields stage))]
          (when (> answered-in-pass 0)
            (recur))))
      
      (when (:on-complete stage)
        (eval-actions (:on-complete stage) answers-atom ui-adapter))
      
      (let [hidden-ids (->> (:fields stage)
                            (filter #(= (keyword (:type %)) :hidden))
                            (map :id)
                            (map keyword)
                            set)]
        (swap! past-hidden-vars clojure.set/union hidden-ids)))))
