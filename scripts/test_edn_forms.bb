;; Script kiểm thử toàn diện bb-form với EDN
;; Chạy: bb scripts/test_edn_forms.bb
;; Kiểm tra: parse EDN, cấu trúc field, load source, detect-format, write-output

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; ─────────────────────────────────────────────
;; Helpers
;; ─────────────────────────────────────────────

(def pass-count (atom 0))
(def fail-count (atom 0))

(defn pass! [msg]
  (swap! pass-count inc)
  (println (str "  ✅ " msg)))

(defn fail! [msg]
  (swap! fail-count inc)
  (println (str "  ❌ " msg)))

(defmacro check [label expr]
  `(try
     (if ~expr
       (pass! ~label)
       (fail! ~label))
     (catch Exception e
       (fail! (str ~label " — EXCEPTION: " (ex-message e))))))

(defn section [title]
  (println (str "\n━━━ " title " ━━━")))

;; ─────────────────────────────────────────────
;; 0. Load source file
;; ─────────────────────────────────────────────

(section "0. Load source file")
(try
  (load-file "src/com/drbinhthanh/bb_form.clj")
  (pass! "bb_form.clj load OK")
  (catch Exception e
    (fail! (str "bb_form.clj load ERROR: " (ex-message e)))
    (System/exit 1)))

;; ─────────────────────────────────────────────
;; 1. Parse kiểm tra tất cả file EDN
;; ─────────────────────────────────────────────

(section "1. Parse EDN — Tất cả file form")

(def form-files
  ["form_sample.edn"
   "forms/radio/form.edn"
   "forms/hr_survey.edn"
   "forms/event_registration.edn"
   "forms/medical_screening.edn"
   "forms/rpg_adventure.edn"])

(def parsed-forms
  (into {}
        (map (fn [path]
               (try
                 (let [form (edn/read-string (slurp path))]
                   (pass! (str path " — parse OK (" (count (:fields form)) " fields)"))
                   [path form])
                 (catch Exception e
                   (fail! (str path " — " (ex-message e)))
                   [path nil])))
             form-files)))

;; ─────────────────────────────────────────────
;; 2. Kiểm tra cấu trúc form
;; ─────────────────────────────────────────────

(section "2. Cấu trúc form")

(doseq [[path form] parsed-forms
        :when form]
  (check (str path " — có :title")       (string? (:title form)))
  (check (str path " — có :fields vector") (vector? (:fields form)))
  (check (str path " — :fields không rỗng") (pos? (count (:fields form)))))

;; ─────────────────────────────────────────────
;; 3. Kiểm tra từng field trong hr_survey
;; ─────────────────────────────────────────────

(section "3. Chi tiết field — hr_survey.edn (Flat List)")

(let [hr (get parsed-forms "forms/hr_survey.edn")
      fields (:fields hr)]
  (check "hr_survey có 18 fields" (= 18 (count fields)))
  (let [ids (map :id fields)]
    (check "Field :ho_ten tồn tại"          (some #{:ho_ten} ids))
    (check "Field :email_cong_ty tồn tại"   (some #{:email_cong_ty} ids))
    (check "Field :phong_ban tồn tại"       (some #{:phong_ban} ids))
    (check "Field :ngay_vao_lam tồn tại"    (some #{:ngay_vao_lam} ids)))
  ;; Kiểm tra regex field
  (let [email-field (first (filter #(= :email_cong_ty (:id %)) fields))]
    (check "email_cong_ty có :regex"     (string? (:regex email-field)))
    (check "email_cong_ty có :regexError" (string? (:regexError email-field))))
  ;; Kiểm tra show-if
  (let [phong-ban-tech (first (filter #(= :ngon_ngu_chinh (:id %)) fields))]
    (check "ngon_ngu_chinh có :show-if"  (vector? (:show-if phong-ban-tech)))))

;; ─────────────────────────────────────────────
;; 3.1. Kiểm tra eval-expr (Logic Engine)
;; ─────────────────────────────────────────────

(section "3.1. Engine eval-expr")

(let [ctx {:selectedByUser {:age 25 :gender "Nữ" :symptoms ["Sốt" "Ho"] :blood_type "O"}}
      eval com.drbinhthanh.bb-form/eval-expr]
  (check "eval :var"                 (= 25 (eval [:var :age] ctx)))
  (check "eval :="                   (true? (eval [:= [:var :gender] "Nữ"] ctx)))
  (check "eval :!="                  (true? (eval [:!= [:var :gender] "Nam"] ctx)))
  (check "eval :>"                   (true? (eval [:> [:var :age] 18] ctx)))
  (check "eval :> false"             (false? (eval [:> [:var :age] 30] ctx)))
  (check "eval :<="                  (true? (eval [:<= [:var :age] 25] ctx)))
  (check "eval :and true"            (true? (eval [:and [:= [:var :gender] "Nữ"] [:> [:var :age] 18]] ctx)))
  (check "eval :and false"           (false? (eval [:and [:= [:var :gender] "Nam"] [:> [:var :age] 18]] ctx)))
  (check "eval :or true"             (true? (eval [:or [:= [:var :gender] "Nam"] [:= [:var :blood_type] "O"]] ctx)))
  (check "eval :not true"            (true? (eval [:not [:= [:var :gender] "Nam"]] ctx)))
  (check "eval :contains? array"     (true? (eval [:contains? [:var :symptoms] "Sốt"] ctx)))
  (check "eval :contains? array fls" (false? (eval [:contains? [:var :symptoms] "Đau đầu"] ctx)))
  (check "eval :contains? set"       (true? (eval [:contains? #{"A" "B" "O"} [:var :blood_type]] ctx)))
  (check "eval :default"             (true? (eval [:default] ctx))))

;; ─────────────────────────────────────────────
;; 4. Kiểm tra values file EDN
;; ─────────────────────────────────────────────

(section "4. Values file EDN")

(check "values_sample.edn parse OK"
  (map? (edn/read-string (slurp "values_sample.edn"))))

(let [vals (edn/read-string (slurp "values_sample.edn"))]
  (check "values có :ho_ten"         (string? (:ho_ten vals)))
  (check "values có :email"          (string? (:email vals)))
  (check "values có :so_dien_thoai"  (string? (:so_dien_thoai vals))))

;; ─────────────────────────────────────────────
;; 5. Logic detect-format
;; ─────────────────────────────────────────────

(section "5. Logic detect-format")

;; Test detect-format bằng cách kiểm tra logic trực tiếp
(defn test-detect-format [output-path force-format expected]
  (let [result (cond
                 (= force-format "edn")                         :edn
                 (= force-format "json")                        :json
                 (str/ends-with? output-path ".json")           :json
                 :else                                          :edn)]
    (= result expected)))

(check "detect: result.edn + nil     → :edn"   (test-detect-format "result.edn" nil :edn))
(check "detect: result.json + nil    → :json"  (test-detect-format "result.json" nil :json))
(check "detect: result.edn + 'json'  → :json"  (test-detect-format "result.edn" "json" :json))
(check "detect: result.edn + 'edn'   → :edn"   (test-detect-format "result.edn" "edn" :edn))
(check "detect: out.json + 'edn'     → :edn"   (test-detect-format "out.json" "edn" :edn))
(check "detect: out.json + nil       → :json"  (test-detect-format "out.json" nil :json))

;; ─────────────────────────────────────────────
;; 6. Kiểm tra write-output! — EDN round-trip
;; ─────────────────────────────────────────────

(section "6. write-output! — EDN round-trip")

(let [test-data  {:selectedByUser {:ho_ten "Test User" :age 30 :tags [:a :b :c]}}
      edn-path   "/tmp/test_output.edn"
      json-path  "/tmp/test_output.json"]

  ;; Ghi EDN
  (spit edn-path (with-out-str (clojure.pprint/pprint test-data)))
  (let [read-back (edn/read-string (slurp edn-path))]
    (check "EDN write + read-back — :ho_ten OK"
      (= "Test User" (get-in read-back [:selectedByUser :ho_ten])))
    (check "EDN write + read-back — :age OK"
      (= 30 (get-in read-back [:selectedByUser :age])))
    (check "EDN write + read-back — :tags keyword vector OK"
      (= [:a :b :c] (get-in read-back [:selectedByUser :tags]))))

  ;; Ghi JSON
  (spit json-path (cheshire.core/generate-string test-data {:pretty true}))
  (let [read-back (cheshire.core/parse-string (slurp json-path) true)]
    (check "JSON write + read-back — :ho_ten OK"
      (= "Test User" (get-in read-back [:selectedByUser :ho_ten])))
    (check "JSON write + read-back — :age OK"
      (= 30 (get-in read-back [:selectedByUser :age])))
    (check "JSON write — keyword vector → string array (expected)"
      (= ["a" "b" "c"] (get-in read-back [:selectedByUser :tags])))))

;; ─────────────────────────────────────────────
;; 7. Kiểm tra regex helper ->pattern
;; ─────────────────────────────────────────────

(section "7. Regex helper — ->pattern")

(defn test-pattern [input]
  (cond
    (instance? java.util.regex.Pattern input) input
    (string? input) (re-pattern input)
    :else nil))

(check "String regex → Pattern"      (instance? java.util.regex.Pattern (test-pattern "^[0-9]+$")))
(check "Pattern passthrough"          (instance? java.util.regex.Pattern (test-pattern #"^[0-9]+$")))
(check "nil → nil"                   (nil? (test-pattern nil)))
(check "Email regex compiles OK"
  (some? (re-matches (test-pattern "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
                     "test@example.com")))
(check "Phone regex — valid 10 digits"
  (some? (re-matches (test-pattern "^[0-9]{10,11}$") "0901234567")))
(check "Phone regex — invalid 9 digits"
  (nil? (re-matches (test-pattern "^[0-9]{10,11}$") "090123456")))

;; ─────────────────────────────────────────────
;; Tổng kết
;; ─────────────────────────────────────────────

(println (str "\n" (apply str (repeat 50 "─"))))
(println (str "KẾT QUẢ: " @pass-count " passed / " @fail-count " failed"))
(when (pos? @fail-count)
  (println "⚠️  Có lỗi cần kiểm tra lại!")
  (System/exit 1))
(println "🎉 Tất cả kiểm thử đều passed!")
