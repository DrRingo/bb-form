(ns com.drbinhthanh.bb-form.expert
  (:require [odoyle.rules :as o]
            [com.drbinhthanh.bb-form.core :as core]
            [com.drbinhthanh.bb-form.engines.gum :as gum]
            [com.drbinhthanh.bb-form.engines.tui :as tui]
            [clojure.set :as set]
            [clojure.string :as str]))

(def ^:dynamic *rule-priorities* nil)

(defn fire-rules-with-priorities [session]
  (binding [*rule-priorities* (atom {})]
    (o/fire-rules session)))

;; ─────────────────────────────────────────────────────────────────
;; 1. PHÂN TÍCH VÀ CHUYỂN ĐỔI BIỂU THỨC (EDN Logic Utilities)
;; ─────────────────────────────────────────────────────────────────

(defn extract-dependencies [expr field-ids]
  (let [field-ids-set (set field-ids)]
    (letfn [(walk [x]
              (cond
                (keyword? x) (if (contains? field-ids-set x) #{x} #{})
                (coll? x) (apply set/union (map walk x))
                :else #{}))]
      (walk expr))))

(defn normalize-expr [expr field-ids]
  (let [field-ids-set (set field-ids)]
    (letfn [(walk [x]
              (cond
                (and (vector? x) (= (first x) :var)) x
                (and (vector? x) (= (first x) :exists)) [:exists (second x)]
                (keyword? x) (if (contains? field-ids-set x) [:var x] x)
                (vector? x) (mapv walk x)
                (list? x) (apply list (map walk x))
                (map? x) (into {} (map (fn [[k v]] [k (walk v)]) x))
                :else x))]
      (walk expr))))

(defn extract-active-deps [expr answers all-vars]
  (letfn [(walk [x]
            (cond
              (and (vector? x) (= (first x) :var))
              (let [var-key (second x)]
                (if (= (get answers var-key :not-answered) :not-answered)
                  #{var-key}
                  #{}))
              
              (and (vector? x) (= (first x) :exists))
              (let [var-key (second x)]
                (if (= (get answers var-key :not-answered) :not-answered)
                  #{var-key}
                  #{}))
              
              (and (vector? x) (keyword? (first x)))
              (let [sub-val (core/eval-expr x {:selectedByUser answers :solver true})]
                (if (not= sub-val :not-answered)
                  #{}
                  (apply set/union (map walk (rest x)))))
              
              (coll? x)
              (apply set/union (map walk x))
              
              :else
              #{}))]
    (walk (normalize-expr expr all-vars))))

;; ─────────────────────────────────────────────────────────────────
;; 2. BỘ BIÊN DỊCH LUẬT O'DOYLE RULES (O'Doyle Rules Compiler)
;; ─────────────────────────────────────────────────────────────────

(defn get-session-answers [session]
  (let [facts (o/query-all session ::get-facts)]
    (->> facts
         (map (fn [{:keys [attr val]}]
                [(keyword (name attr)) val]))
         (into {}))))

(defn get-rule-required-vars [rule all-vars]
  (if (contains? rule :require)
    (set (:require rule))
    (let [if-expr (:if rule)
          then-map (:then rule)
          if-deps (if if-expr (extract-dependencies if-expr all-vars) #{})
          then-deps (apply set/union (map #(extract-dependencies % all-vars) (vals then-map)))]
      (set/union if-deps then-deps))))

(defn compile-rules [rules all-vars]
  (let [generic-query (o/->rule ::get-facts {:what '[[?session attr val]]})
        ;; Sắp xếp rules theo :priority tăng dần trước khi add vào session
        ;; (Rule ưu tiên cao hơn được add sau → khi cùng fire sẽ ghi đè rule ưu tiên thấp)
        ;; Đây là cơ chế Conflict Resolution theo §9 của spec.
        sorted-rules (sort-by #(or (:priority %) 0) rules)]
    (reduce
      (fn [session-acc rule]
        (let [rule-id (keyword "com.drbinhthanh.bb-form.rules" (name (:id rule)))
              if-expr (:if rule)
              then-map (:then rule)
              ;; all-deps are all variables referenced (for O'Doyle bindings)
              if-deps (if if-expr (extract-dependencies if-expr all-vars) #{})
              then-deps (apply set/union (map #(extract-dependencies % all-vars) (vals then-map)))
              all-deps (set/union if-deps then-deps)
              
              ;; required-vars must be resolved (not :not-answered) for the rule to fire
              required-vars (get-rule-required-vars rule all-vars)
              
              norm-if (when if-expr (normalize-expr if-expr all-vars))
              what-block (into [['?session :session/state 'state]]
                               (map (fn [dep]
                                      ['?session (keyword "session" (name dep)) (symbol (name dep))])
                                    (set/union all-deps required-vars)))
              when-fn (fn [session match]
                        (and
                          ;; 1. TẤT CẢ các required-vars phải khác :not-answered
                          (every? (fn [rvar]
                                    (not= (get match (keyword (name rvar))) :not-answered))
                                  required-vars)
                          ;; 2. Điều kiện :if (nếu có) phải đúng
                          (if norm-if
                            (let [context {:selectedByUser (into {} (map (fn [dep]
                                                                           [dep (get match (keyword (name dep)))])
                                                                         (set/union all-deps required-vars)))}
                                  res (core/eval-expr norm-if context)]
                              (and (some? res) (not= res false) (not= res :not-answered)))
                            true)))
              then-fn (fn [session match]
                        (let [context {:selectedByUser (into {} (map (fn [dep]
                                                                       [dep (get match (keyword (name dep)))])
                                                                     (set/union all-deps required-vars)))}
                              rule-priority (or (:priority rule) 0)]
                          (doseq [[out-var out-expr] then-map]
                            (let [val (core/eval-expr (normalize-expr out-expr all-vars) context)
                                  current-prio (if *rule-priorities*
                                                 (get @*rule-priorities* out-var -1)
                                                 -1)]
                              (when (>= rule-priority current-prio)
                                (when *rule-priorities*
                                  (swap! *rule-priorities* assoc out-var rule-priority))
                                (o/insert! (:?session match) (keyword "session" (name out-var)) val))))))]
          (o/add-rule session-acc (o/->rule rule-id {:what what-block
                                                     :when when-fn
                                                     :then then-fn}))))
      (o/add-rule (o/->session) generic-query)
      sorted-rules)))

;; ─────────────────────────────────────────────────────────────────
;; 3. GIẢI THUẬT DUYỆT NGƯỢC (Backward-Chaining Solver)
;; ─────────────────────────────────────────────────────────────────

(defn resolve-var [form answers rules var-id visited]
  (let [field-ids (map :id (:fields form))
        all-outputs (mapcat (fn [r] (keys (:then r))) rules)
        all-vars (set/union (set field-ids) (set all-outputs))]
    (letfn [(resolve-rec [var-id visited]
              (if (contains? visited var-id)
                #{}
                (let [val (get answers var-id :not-answered)
                      visited (conj visited var-id)]
                  (if (not= val :not-answered)
                    #{}
                    (if (some #(= (:id %) var-id) (:fields form))
                      (let [field (some #(when (= (:id %) var-id) %) (:fields form))
                            default-expr (:default field)
                            default-deps (if (vector? default-expr) (extract-active-deps default-expr answers all-vars) #{})]
                        (if (empty? default-deps)
                          #{var-id}
                          (let [unresolved-default-deps (mapcat #(resolve-rec % visited) default-deps)]
                            (if (empty? unresolved-default-deps)
                              #{var-id}
                              (into #{} unresolved-default-deps)))))
                      (let [matching-rules (filter #(contains? (:then %) var-id) rules)]
                        (if (empty? matching-rules)
                          #{}
                          (into #{}
                                (mapcat (fn [rule]
                                          (let [cond-val (if (:if rule)
                                                           (core/eval-expr (normalize-expr (:if rule) all-vars)
                                                                           {:selectedByUser answers :solver true})
                                                           true)]
                                            (if (= cond-val false)
                                              #{}
                                               (let [unanswered-reqs (if (contains? rule :require)
                                                                       (filter #(= (get answers % :not-answered) :not-answered) (:require rule))
                                                                       [])
                                                     rule-deps (if (seq unanswered-reqs)
                                                                 unanswered-reqs
                                                                 (let [if-active (if (:if rule)
                                                                                   (extract-active-deps (:if rule) answers all-vars)
                                                                                   #{})
                                                                       then-active (extract-active-deps (get (:then rule) var-id) answers all-vars)]
                                                                   (set/union if-active then-active)))]
                                                (mapcat #(resolve-rec % visited) rule-deps)))))
                                        matching-rules)))))))))]
      (resolve-rec var-id visited))))

(defn sort-candidates [form candidates]
  (let [fields-order (into {} (map-indexed (fn [idx f] [(:id f) idx]) (:fields form)))
        fields-priority (into {} (map (fn [f] [(:id f) (or (:priority f) 0)]) (:fields form)))]
    (sort-by (fn [cid]
               [(- (get fields-priority cid 0))
                (get fields-order cid 9999)])
             candidates)))

;; ─────────────────────────────────────────────────────────────────
;; 4. RÀNG BUỘC PHƯƠNG THỨC NHẬP LIỆU & ENGINE (Inputs & Execution)
;; ─────────────────────────────────────────────────────────────────

(defn get-marathon-value [field current-answers all-vars]
  ;; Bug fix §8.1-A: Trước đây dùng field-ids chỉ gồm 1 phần tử (chính field đó).
  ;; Cần dùng all-vars (tập toàn bộ biến của form + output của rules) để normalize-expr
  ;; có thể nhận diện đúng các biến tham chiếu trong biểu thức :default động.
  (let [explicit-default (:default field)
        field-type (keyword (:type field))]
    (cond
      ;; Phương án A: `:default` là biểu thức EDN động (vector)
      ;; Đánh giá biểu thức với toàn bộ all-vars để normalize đúng
      (and (some? explicit-default) (vector? explicit-default))
      (core/eval-expr (normalize-expr explicit-default all-vars) {:selectedByUser current-answers})

      ;; Phương án A: `:default` là giá trị tĩnh
      (some? explicit-default)
      explicit-default

      ;; Trường :info: chỉ cần đánh dấu đã xử lý, không lưu giá trị nhập liệu.
      ;; Nhãn của trường :info sẽ được render bởi engine khi ask-field được gọi.
      ;; Trong marathon, ta trả về nil (sẽ không insert fact vào session).
      (= field-type :info)
      nil

      (#{:select :radio} field-type)
      (first (:options field))

      (= :multiselect field-type)
      [(first (:options field))]

      (= :number field-type)
      0

      (#{:date :datetime} field-type)
      (core/today)

      :else
      nil)))

(defn ask-field-via-engine [engine-kw field form current-answers theme]
  (let [answers-atom (atom {:selectedByUser current-answers
                            :HiddenVar {}})]
    (case engine-kw
      :gum
      (let [default-theme-var (requiring-resolve 'com.drbinhthanh.bb-form.themes.gum-theme/default-theme)
            default-theme @default-theme-var
            loaded-theme (core/load-theme default-theme theme)]
        (gum/clear-screen)
        (gum/render-header form "" loaded-theme)
        (gum/ask-field field form answers-atom loaded-theme))

      :tui
      (let [default-theme-var (requiring-resolve 'com.drbinhthanh.bb-form.themes.tui-theme/default-theme)
            default-theme @default-theme-var
            loaded-theme (core/load-theme default-theme theme)]
        (tui/ask-field field form answers-atom loaded-theme))

      ;; Fallback to gum
      (let [default-theme-var (requiring-resolve 'com.drbinhthanh.bb-form.themes.gum-theme/default-theme)
            default-theme @default-theme-var
            loaded-theme (core/load-theme default-theme theme)]
        (gum/clear-screen)
        (gum/render-header form "" loaded-theme)
        (gum/ask-field field form answers-atom loaded-theme)))
    (get-in @answers-atom [:selectedByUser (keyword (:id field))])))

;; ─────────────────────────────────────────────────────────────────
;; 5. VÒNG LẶP CHÍNH CỦA HỆ CHUYÊN GIA (Main Expert Loop)
;; ─────────────────────────────────────────────────────────────────

(defn clean-answers [answers]
  (into {} (remove (fn [[k v]] (= v :not-answered)) answers)))

(defn run-expert-loop [form prefilled options]
  (let [rules (:rules form)
        field-ids (map :id (:fields form))
        all-outputs (into #{} (mapcat (fn [r] (keys (:then r))) rules))
        all-vars (set/union (set field-ids) all-outputs)
        session-atom (atom (compile-rules rules all-vars))
        engine (or (:engine options) :gum)
        theme (:theme options)
        marathon? (:marathon options)
        ;; Bản đồ tra cứu field theo id để tránh tìm kiếm O(n) trong vòng lặp
        fields-by-id (into {} (map (fn [f] [(:id f) f]) (:fields form)))]
    
    ;; 1. Khởi tạo Session với Facts ban đầu
    (swap! session-atom
           (fn [s]
             (reduce
               (fn [session-acc var-id]
                 (let [val (get prefilled var-id :not-answered)]
                   (o/insert session-acc :global (keyword "session" (name var-id)) val)))
               (o/insert s :global :session/state :active)
               all-vars)))
    
    ;; Kích hoạt các luật lần đầu
    (swap! session-atom fire-rules-with-priorities)

    ;; 2. Vòng lặp suy diễn & giải quyết câu hỏi ứng viên
    (loop []
      (let [current-answers (get-session-answers @session-atom)
            goals (:goals form)
            goals-resolved? (every? (fn [g]
                                      (let [v (get current-answers g :not-answered)]
                                        (and (not= v :not-answered) (some? v))))
                                    goals)]
        (if goals-resolved?
          ;; Tất cả các goals đã được giải quyết thành công
          (clean-answers current-answers)
          (let [candidates (into #{}
                                 (mapcat (fn [goal-id]
                                           (resolve-var form current-answers rules goal-id #{}))
                                         goals))]
            (if (empty? candidates)
              ;; Không thể suy diễn thêm và không còn câu hỏi ứng viên nào
              (clean-answers current-answers)
              (let [sorted (sort-candidates form candidates)
                    next-q-id (first sorted)
                    next-field (some #(when (= (:id %) next-q-id) %) (:fields form))]
                (if-not next-field
                  (clean-answers current-answers)
                  (let [answer-val (if marathon?
                                     (get-marathon-value next-field current-answers all-vars)
                                     (ask-field-via-engine engine next-field form current-answers theme))]
                    ;; Thieu 1: :info field hoac optional (required: false) co the tra ve nil.
                    ;; - :info: danh dau "displayed" de solver khong hoi lai.
                    ;; - Optional nil: insert nil (nil != :not-answered, :exists -> false).
                    (if (and (nil? answer-val)
                             (= :info (keyword (:type next-field))))
                      (swap! session-atom (fn [s]
                                            (-> s
                                                (o/insert :global (keyword "session" (name next-q-id)) "displayed")
                                                fire-rules-with-priorities)))
                      (swap! session-atom (fn [s]
                                            (-> s
                                                (o/insert :global (keyword "session" (name next-q-id)) answer-val)
                                                fire-rules-with-priorities))))
                    (recur)))))))))))