;; Script: scripts/test_scenarios.bb
;; Chạy: bb scripts/test_scenarios.bb
;; Kiểm thử các tình huống đa dạng (diverse scenarios) của form

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; Load module chính để sử dụng eval-expr
(load-file "src/com/drbinhthanh/bb_form.clj")
(def eval-expr com.drbinhthanh.bb-form/eval-expr)

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

;; Hàm lấy danh sách các trường (fields) được kích hoạt dựa trên input của người dùng
(defn get-active-field-ids [form answers-map]
  (let [context {:selectedByUser answers-map}]
    (->> (:fields form)
         (filter (fn [field]
                   (let [cond (:show-if field)]
                     (or (nil? cond)
                         (eval-expr cond context)))))
         (map :id)
         set)))

;; ─────────────────────────────────────────────
;; Load Forms
;; ─────────────────────────────────────────────

(def medical-form (edn/read-string (slurp "forms/medical_screening.edn")))
(def rpg-form (edn/read-string (slurp "forms/rpg_adventure.edn")))

;; ─────────────────────────────────────────────
;; 1. Medical Screening Form - Scenarios
;; ─────────────────────────────────────────────

(section "1. Medical Screening Form - Scenarios")

(let [answers {:gioi_tinh "Nam"}]
  (let [fields (get-active-field-ids medical-form answers)]
    (check "Nam giới KHÔNG có câu hỏi mang thai" (not (contains? fields :co_thai)))
    (check "Nam giới KHÔNG có tuần thai" (not (contains? fields :tuan_thai)))))

(let [answers {:gioi_tinh "Nữ" :co_thai "Có"}]
  (let [fields (get-active-field-ids medical-form answers)]
    (check "Nữ mang thai CÓ hỏi tuần thai" (contains? fields :tuan_thai))))

(let [answers {:gioi_tinh "Nữ" :co_thai "Không"}]
  (let [fields (get-active-field-ids medical-form answers)]
    (check "Nữ KHÔNG mang thai KHÔNG hỏi tuần thai" (not (contains? fields :tuan_thai)))))

(let [answers {:trieu_chung ["Sốt" "Ho"]}]
  (let [fields (get-active-field-ids medical-form answers)]
    (check "Có triệu chứng Sốt -> Hỏi nhiệt độ" (contains? fields :nhiet_do))
    (check "Có triệu chứng Sốt -> Hỏi số ngày sốt" (contains? fields :so_ngay_sot))
    (check "Không có Khó thở -> Không hỏi mức độ khó thở" (not (contains? fields :muc_do_kho_tho)))))

(let [answers {:trieu_chung ["Khó thở"]}]
  (let [fields (get-active-field-ids medical-form answers)]
    (check "Có Khó thở -> Hỏi mức độ khó thở" (contains? fields :muc_do_kho_tho))
    (check "Không Sốt -> Không hỏi nhiệt độ" (not (contains? fields :nhiet_do)))))

;; ─────────────────────────────────────────────
;; 2. RPG Adventure Form - Scenarios
;; ─────────────────────────────────────────────

(section "2. RPG Adventure Form - Scenarios")

(let [answers {:hanh_dong_dau_tien "Đạp cửa xông vào" :do_nghe ["Súng"]}]
  (let [fields (get-active-field-ids rpg-form answers)]
    (check "Đạp cửa + Có Súng -> Hỏi rút súng" (contains? fields :rut_sung))
    (check "Đạp cửa + Có Súng -> KHÔNG bị tấn công" (not (contains? fields :bi_tan_cong)))))

(let [answers {:hanh_dong_dau_tien "Đạp cửa xông vào" :do_nghe ["Đèn pin"]}]
  (let [fields (get-active-field-ids rpg-form answers)]
    (check "Đạp cửa + KHÔNG Súng -> KHÔNG hỏi rút súng" (not (contains? fields :rut_sung)))
    (check "Đạp cửa + KHÔNG Súng -> Bị tấn công" (contains? fields :bi_tan_cong))))

(let [answers {:hanh_dong_dau_tien "Nhìn qua khe cửa" :do_nghe ["Kính lúp"]}]
  (let [fields (get-active-field-ids rpg-form answers)]
    (check "Nhìn qua khe cửa + Kính lúp -> Quan sát bằng kính" (contains? fields :quan_sat_bang_kinh))))

(let [answers {:hanh_dong_dau_tien "Nhìn qua khe cửa" :do_nghe []}]
  (let [fields (get-active-field-ids rpg-form answers)]
    (check "Nhìn qua khe cửa + KHÔNG Kính lúp -> KHÔNG quan sát bằng kính" (not (contains? fields :quan_sat_bang_kinh)))))

;; Check final conclusion question logic for RPG
(let [answers {:hanh_dong_dau_tien "Gõ cửa cẩn thận"}]
  (check "Gõ cửa cẩn thận -> Có kết luận" (contains? (get-active-field-ids rpg-form answers) :ket_luan)))

(let [answers {:rut_sung "Có"}]
  (check "Rút súng: Có -> Có kết luận" (contains? (get-active-field-ids rpg-form answers) :ket_luan)))

(let [answers {:bi_tan_cong "Đỡ đòn"}]
  (check "Bị tấn công: Đỡ đòn -> Có kết luận" (contains? (get-active-field-ids rpg-form answers) :ket_luan)))

(let [answers {:quan_sat_bang_kinh "Lấy mẫu máu"}]
  (check "Quan sát kính: Lấy mẫu máu -> Có kết luận" (contains? (get-active-field-ids rpg-form answers) :ket_luan)))

;; ─────────────────────────────────────────────
;; 3. Engine eval-expr (Math, String, Array)
;; ─────────────────────────────────────────────

(section "3. Engine eval-expr (Math, String, Array)")

(let [ctx {:selectedByUser {:age 30 :name "DrRingo" :items ["Sword" "Shield"]}}]
  ;; Toán học
  (check "eval :+ "                 (= 45 (eval-expr [:+ [:var :age] 15] ctx)))
  (check "eval :- "                 (= 10 (eval-expr [:- [:var :age] 20] ctx)))
  (check "eval :* "                 (= 60 (eval-expr [:* [:var :age] 2] ctx)))
  (check "eval :/ "                 (= 15 (eval-expr [:/ [:var :age] 2] ctx)))
  (check "eval :mod "               (= 0  (eval-expr [:mod [:var :age] 5] ctx)))
  (check "eval :mod (dư) "          (= 2  (eval-expr [:mod [:var :age] 4] ctx)))
  
  ;; Chuỗi
  (check "eval :str/includes? true" (true? (eval-expr [:str/includes? [:var :name] "Ringo"] ctx)))
  (check "eval :str/includes? fls " (false? (eval-expr [:str/includes? [:var :name] "John"] ctx)))
  (check "eval :str/lower-case"     (= "drringo" (eval-expr [:str/lower-case [:var :name]] ctx)))
  (check "eval :str/upper-case"     (= "DRRINGO" (eval-expr [:str/upper-case [:var :name]] ctx)))
  
  ;; Mảng / Tập hợp
  (check "eval :count"              (= 2 (eval-expr [:count [:var :items]] ctx)))
  (check "eval :first"              (= "Sword" (eval-expr [:first [:var :items]] ctx)))
  (check "eval :concat"             (= ["Sword" "Shield" "Potion"] (eval-expr [:concat [:var :items] [:array "Potion"]] ctx))))

(println (str "\n" (apply str (repeat 50 "─"))))
(println (str "KẾT QUẢ SCENARIOS: " @pass-count " passed / " @fail-count " failed"))
(when (pos? @fail-count)
  (System/exit 1))
