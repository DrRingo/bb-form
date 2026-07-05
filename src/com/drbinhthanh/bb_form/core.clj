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

(defn parse-value [v type]
  (case type
    ("number" :number) (try (Integer/parseInt (str v)) (catch Exception _ v))
    ("text" :text)     (str v)
    ("date" :date)     (let [s (str v)]
                         (if (re-matches #"^\d{2}-\d{2}-\d{4}$" s)
                           (try
                             (let [dt (java.time.LocalDate/parse s (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy"))]
                               (.format dt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd")))
                             (catch Exception _ s))
                           s))
    v))

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
      (re-matches #"^\d{2}$" trimmed)
      (let [dd (Integer/parseInt trimmed)]
        (format "%02d-%02d-%d" dd month year))
      (re-matches #"^\d{4}$" trimmed)
      (let [dd (Integer/parseInt (subs trimmed 0 2))
            mm (Integer/parseInt (subs trimmed 2 4))]
        (format "%02d-%02d-%d" dd mm year))
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
                                  (do
                                    ((:ask-field ui-adapter) field form answers-atom)
                                    (when (:actions field)
                                      (eval-actions (:actions field) answers-atom ui-adapter))
                                    (swap! executed-actions conj id)
                                    (inc count))
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
