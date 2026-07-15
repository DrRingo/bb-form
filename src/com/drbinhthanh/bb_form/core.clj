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
(declare eval-expr)
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
        (binding [*out* *err*]
          (println "⚠️ Lỗi khi nạp theme tùy chỉnh từ file:" file-path)
          (println "Chi tiết:" (.getMessage e)))
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
                          (binding [*out* *err*]
                            (println "📦 Đang nạp thư viện Clojure:" path))
                          (load-file (str file))
                          (second (re-find #"\(ns\s+([\w\.\-]+)" (slurp file))))
                        (let [data (edn/read-string (slurp file))
                              ns-name (:ns data)
                              consts (:consts data)
                              fns (:fns data)]
                          (when (and ns-name fns)
                            (binding [*out* *err*]
                              (println "📦 Đang nạp thư viện EDN:" path))
                            (let [compiled-fns (into {}
                                                     (for [[k v] fns]
                                                       [k (eval `(let [~'consts '~consts] ~v))]))]
                              (swap! formula-registry assoc ns-name compiled-fns)))
                          (name ns-name)))]
        (when (and alias-key actual-ns)
          (swap! ns-aliases assoc (name alias-key) actual-ns)))
      (catch Exception e
        (binding [*out* *err*]
          (println "⚠️ Lỗi khi nạp công thức từ cấu trúc:" import-decl)
          (println "Chi tiết lỗi:" (.getMessage e)))))))

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
  (contains? (:selectedByUser @answers) (keyword id)))

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
        (if-let [m (re-matches #"^(\d{4})([hH]\d{4}(?:[+-]\d+)?)$" p)]
          (let [d-part (nth m 1)
                t-part (nth m 2)]
            (and (#{:datetime "datetime"} (keyword type))
                 (valid-date? (expand-date-shortcut d-part))
                 (valid-time-str? t-part)))
          (if (or (str/includes? p ":")
                  (str/includes? p "h")
                  (str/includes? p "H"))
            (and (#{:datetime "datetime"} (keyword type))
                 (valid-time-str? p))
            (and (#{:date :datetime "date" "datetime"} (keyword type))
                 (valid-date? (expand-date-shortcut p))))))
               
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
            (if-let [m (re-matches #"^(\d{4})([hH]\d{4}(?:[+-]\d+)?)$" p)]
              {:date-str (nth m 1) :time-str (nth m 2)}
              (if (or (str/includes? p ":")
                      (str/includes? p "h")
                      (str/includes? p "H"))
                {:date-str nil :time-str p}
                {:date-str p :time-str nil})))
              
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
(defn get-marathon-default
  ([field] (get-marathon-default field nil))
  ([field context]
   (let [type             (keyword (:type field))
         explicit-default (:default field)
         resolved-default (if (and (vector? explicit-default) context)
                            (eval-expr explicit-default context)
                            explicit-default)]
     (cond
       ;; info/hidden: bỏ qua - xử lý riêng trong run-terminal-form
       (#{:info :hidden} type) nil

       ;; nếu có :default tường minh ⇒ parse và trả về
       (some? resolved-default) (parse-value resolved-default (name type))

       ;; nếu trường không bắt buộc và không có giá trị mặc định ⇒ trả về nil
       (false? (:required field)) nil

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

       :else nil))))
(defn ->pattern [regex]
  (cond
    (instance? java.util.regex.Pattern regex) regex
    (string? regex) (re-pattern regex)
    :else nil))

(defn coerce-num [v]
  (cond
    (number? v) v
    (string? v) (try (Long/parseLong v)
                     (catch Exception _
                       (try (Double/parseDouble v)
                            (catch Exception _ 0))))
    :else 0))

(defn eval-expr [expr context]
  (cond
    (not (vector? expr)) expr
    (empty? expr) expr
    ;; §11.2 Cú pháp rút gọn gọi hàm ngoài: [:ns/fn arg1 arg2 ...]
    ;; Nếu phần tử đầu là keyword có namespace (ví dụ :sanh/tinh-gio), tự động
    ;; chuyển thành [:call :ns/fn arg1 arg2 ...] để tái sử dụng logic :call.
    ;; Loại trừ các namespace nội bộ của bb-form (:str) vì chúng được xử lý trực tiếp trong case.
    (and (keyword? (first expr))
         (namespace (first expr))
         (not= (namespace (first expr)) "str"))
    (eval-expr (into [:call] expr) context)
    (not (keyword? (first expr))) (vec (map #(eval-expr % context) expr))
    :else
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
        :and (let [evaluated (mapv #(eval-expr % context) args)]
               (cond
                 (some #(= % false) evaluated) false
                 (some #(= % :not-answered) evaluated) :not-answered
                 :else true))
        :or  (let [evaluated (mapv #(eval-expr % context) args)]
               (cond
                 (some (fn [v] (and (some? v) (not= v false) (not= v :not-answered))) evaluated) true
                 (some #(= % :not-answered) evaluated) :not-answered
                 :else false))
        :not (let [v (eval-expr (first args) context)]
               (if (= v :not-answered)
                 :not-answered
                 (not v)))
        :=   (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply = evaluated)))
        :!=  (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply not= evaluated)))
        :>   (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply > (map coerce-num evaluated))))
        :<   (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply < (map coerce-num evaluated))))
        :>=  (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply >= (map coerce-num evaluated))))
        :<=  (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply <= (map coerce-num evaluated))))
        :if  (let [cond-val (eval-expr (first args) context)]
               (if (= cond-val :not-answered)
                 :not-answered
                 (if cond-val
                   (eval-expr (second args) context)
                   (eval-expr (nth args 2) context))))
        :+   (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply + (map coerce-num evaluated))))
        :-   (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply - (map coerce-num evaluated))))
        :*   (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply * (map coerce-num evaluated))))
        :/   (let [evaluated (mapv #(eval-expr % context) args)]
               (if (some #(= % :not-answered) evaluated)
                 :not-answered
                 (apply / (map coerce-num evaluated))))
        :mod (let [v1 (eval-expr (first args) context)
                   v2 (eval-expr (second args) context)]
               (if (or (= v1 :not-answered) (= v2 :not-answered))
                 :not-answered
                 (mod (or v1 0) (or v2 1))))
        :str/includes?  (let [s1 (eval-expr (first args) context)
                              s2 (eval-expr (second args) context)]
                          (if (or (= s1 :not-answered) (= s2 :not-answered))
                            :not-answered
                            (str/includes? (str s1) (str s2))))
        :str/lower-case (let [s (eval-expr (first args) context)]
                          (if (= s :not-answered)
                            :not-answered
                            (str/lower-case (str s))))
        :str/upper-case (let [s (eval-expr (first args) context)]
                          (if (= s :not-answered)
                            :not-answered
                            (str/upper-case (str s))))
        :count  (let [coll (eval-expr (first args) context)]
                  (if (= coll :not-answered)
                    :not-answered
                    (count coll)))
        :first  (let [coll (eval-expr (first args) context)]
                  (if (= coll :not-answered)
                    :not-answered
                    (first coll)))
        :concat (let [evaluated (mapv #(eval-expr % context) args)]
                  (if (some #(= % :not-answered) evaluated)
                    :not-answered
                    (apply concat evaluated)))
        :array  (let [evaluated (mapv #(eval-expr % context) args)]
                  (if (some #(= % :not-answered) evaluated)
                    :not-answered
                    (vec evaluated)))
        :exists (let [raw-key (eval-expr (first args) context)
                      var-key (if (or (string? raw-key) (keyword? raw-key)) (keyword raw-key) raw-key)
                      val (or (get-in context [:selectedByUser var-key])
                              (get-in context [:HiddenVar var-key]))]
                  (if (:solver context)
                    (if (or (nil? val) (= val :not-answered))
                      :not-answered
                      true)
                    (boolean (and (some? val) (not= val :not-answered)))))
        :contains? (let [coll (eval-expr (first args) context)
                          item (eval-expr (second args) context)]
                      (cond
                        (or (= coll :not-answered) (= item :not-answered)) :not-answered
                        (set? coll) (contains? coll item)
                        :else (some? (some #{item} coll))))

        :call (let [[fn-path & fn-args] args
                    [raw-ns fn-name] (if (keyword? fn-path)
                                       [(namespace fn-path) (name fn-path)]
                                       [(namespace (str fn-path)) (name (str fn-path))])
                    ns-name (get @ns-aliases raw-ns raw-ns)
                    evaluated-args (map #(eval-expr % context) fn-args)]
                (if (some #(= % :not-answered) evaluated-args)
                  :not-answered
                  (if-let [f-var (and ns-name fn-name (resolve (symbol ns-name fn-name)))]
                    (apply @f-var evaluated-args)
                    (if-let [f (get-in @formula-registry [(keyword ns-name) (keyword fn-name)])]
                      (apply f evaluated-args)
                      (throw (ex-info (str "Hàm không tồn tại trong namespace hoặc registry: " fn-path)
                                      {:ns ns-name :fn fn-name :raw-ns raw-ns}))))))
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

(defn eval-action-pure [action context]
  (let [[op & args] action]
    (case op
      :set (let [var-name (first args)
                 expr (second args)
                 val (eval-expr expr context)]
             (assoc-in context [:HiddenVar (keyword var-name)] val))
      context)))

(defn eval-actions-pure [actions context]
  (reduce (fn [ctx act] (eval-action-pure act ctx)) context actions))

(defn solve-stages [stages stage-idx field-idx context active-P]
  (if (>= stage-idx (count stages))
    (if (every? (fn [[k v]] (= (get-in context [:selectedByUser k]) v)) active-P)
      context
      nil)
    (let [stage (nth stages stage-idx)
          fields (:fields stage)]
      (if (>= field-idx (count fields))
        ;; End of stage: run on-complete, then move to next stage
        (let [completed-context (if (:on-complete stage)
                                  (eval-actions-pure (:on-complete stage) context)
                                  context)]
          (solve-stages stages (inc stage-idx) 0 completed-context active-P))
        ;; In stage: run on-begin if field-idx is 0
        (let [context (if (and (zero? field-idx) (:on-begin stage))
                        (eval-actions-pure (:on-begin stage) context)
                        context)
              field (nth fields field-idx)
              id (keyword (:id field))
              type (keyword (:type field))
              show-if (:show-if field)]
          (if (or (nil? show-if) (eval-expr show-if context))
            ;; Visible field
            (cond
              (= type :hidden)
              (let [calc-val (eval-expr (:value field) context)
                    next-context (assoc-in context [:selectedByUser id] calc-val)
                    next-context (if (:actions field)
                                   (eval-actions-pure (:actions field) next-context)
                                   next-context)]
                (solve-stages stages stage-idx (inc field-idx) next-context active-P))

              (= type :info)
              (let [label-val (resolve-label (:label field) context)
                    next-context (assoc-in context [:selectedByUser id] label-val)
                    next-context (if (:actions field)
                                   (eval-actions-pure (:actions field) next-context)
                                   next-context)]
                (solve-stages stages stage-idx (inc field-idx) next-context active-P))

              :else
              (if (contains? active-P id)
                ;; Case 1: Prefilled
                (let [val (get active-P id)
                      options (:options field)]
                  (if (seq options)
                    (if-let [matching-opt (first (filter #(= (normalize-str %) (normalize-str val)) options))]
                      (let [next-context (assoc-in context [:selectedByUser id] matching-opt)
                            next-context (if (:actions field)
                                           (eval-actions-pure (:actions field) next-context)
                                           next-context)]
                        (solve-stages stages stage-idx (inc field-idx) next-context active-P))
                      nil) ;; Invalid prefilled option
                    (let [next-context (assoc-in context [:selectedByUser id] val)
                          next-context (if (:actions field)
                                         (eval-actions-pure (:actions field) next-context)
                                         next-context)]
                      (solve-stages stages stage-idx (inc field-idx) next-context active-P))))
                ;; Case 2: Not prefilled
                (let [options (:options field)]
                  (if (seq options)
                    (let [user-val (get-in context [:prefilled id])
                          default-val (let [d (:default field)]
                                        (if (vector? d)
                                          (eval-expr d context)
                                          d))
                          user-opt (when user-val (first (filter #(= (normalize-str %) (normalize-str user-val)) options)))
                          default-opt (when default-val (first (filter #(= (normalize-str %) (normalize-str default-val)) options)))
                          ordered-opts (cond-> []
                                         user-opt (conj user-opt)
                                         default-opt (conj default-opt)
                                         :always (into options)
                                         :always distinct)]
                      (some (fn [opt]
                              (let [next-context (assoc-in context [:selectedByUser id] opt)
                                    next-context (if (:actions field)
                                                   (eval-actions-pure (:actions field) next-context)
                                                   next-context)]
                                (solve-stages stages stage-idx (inc field-idx) next-context active-P)))
                            ordered-opts))
                    (let [user-val (get-in context [:prefilled id])
                          def-val (if (some? user-val) user-val (get-marathon-default field context))
                          next-context (assoc-in context [:selectedByUser id] def-val)
                          next-context (if (:actions field)
                                         (eval-actions-pure (:actions field) next-context)
                                         next-context)]
                      (solve-stages stages stage-idx (inc field-idx) next-context active-P))))))
            ;; Hidden field
            (let [next-context (update context :selectedByUser dissoc id)]
              (solve-stages stages stage-idx (inc field-idx) next-context active-P))))))))

(defn find-path
  ([form prefilled-map] (find-path form prefilled-map true))
  ([form prefilled-map marathon?]
   (let [stages (or (:stages form) [{:fields (:fields form)}])
         all-fields (mapcat :fields stages)
         field-priorities (into {} (map (fn [f] [(keyword (:id f)) (get f :priority 0)]) all-fields))
         field-priority (fn [field-id] (get field-priorities (keyword field-id) 0))
         field-types (into {} (map (fn [f] [(keyword (:id f)) (keyword (:type f))]) all-fields))
         clean-prefilled (into {} (filter (fn [[_ v]] (some? v)) prefilled-map))
         parsed-prefilled (into {} (for [[k v] clean-prefilled]
                                     [k (parse-value v (get field-types k :text))]))
         all-keys (keys parsed-prefilled)
         field-order (into {} (map-indexed (fn [idx field] [(keyword (:id field)) idx]) all-fields))
         ;; Sort keys from highest priority (largest priority then largest index) to lowest priority
         prioritized-keys (sort-by (fn [k]
                                     [(- (field-priority k))
                                      (- (get field-order (keyword k) -1))])
                                   all-keys)]
     (let [final-accepted
           (reduce (fn [accepted k]
                     (let [test-accepted (assoc accepted k (get parsed-prefilled k))
                           initial-context {:selectedByUser {}
                                            :HiddenVar (get form :variables {})
                                            :prefilled parsed-prefilled}
                           result-context (solve-stages stages 0 0 initial-context test-accepted)
                           result-answers (:selectedByUser result-context)]
                       (if (and result-context
                                (every? (fn [[tk tv]] (= (get result-answers tk) tv)) test-accepted))
                         test-accepted
                         accepted)))
                   {}
                   prioritized-keys)]
       (if marathon?
         (let [final-context (solve-stages stages 0 0
                                           {:selectedByUser {}
                                            :HiddenVar (get form :variables {})
                                            :prefilled parsed-prefilled}
                                           final-accepted)]
           (or (:selectedByUser final-context) {}))
         final-accepted)))))

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
                                                     (get-marathon-default field @answers-atom))]
                                      (swap! answers-atom assoc-in [:selectedByUser id-k] auto-val)
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
