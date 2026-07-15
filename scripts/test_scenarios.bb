;; Script: scripts/test_scenarios.bb
;; Chạy: bb scripts/test_scenarios.bb
;; Kiểm thử các tình huống đa dạng (diverse scenarios) của form

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; Load module chính để sử dụng eval-expr
(load-file "src/com/drbinhthanh/bb_form.clj")
(def eval-expr com.drbinhthanh.bb-form.core/eval-expr)

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

;; Load formulas for RPG form
(com.drbinhthanh.bb-form.core/load-formulas! (:import rpg-form) "forms")

;; Helper to run expert loop in tests
(def run-expert-loop com.drbinhthanh.bb-form.expert/run-expert-loop)

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
;; 2. RPG Adventure Form - Scenarios (Expert Mode)
;; ─────────────────────────────────────────────

(section "2. RPG Adventure Form - Scenarios (Expert Mode)")

(let [inputs {:ten_tham_tu "Holmes"
              :dung_cu ["Kính lúp" "Đèn pin" "Bộ tăm bông lấy mẫu" "Sổ tay thám tử"]
              :tiep_can_hien_truong "Khám nghiệm phòng ngủ trước"
              :kham_nghiem_tu_thi "Kiểm tra ly rượu uống dở cạnh giường"
              :thu_pham "Cả Quản gia Alfred và Isabella đồng phạm"}
      res (run-expert-loop rpg-form inputs {:marathon true})]
  (check "Thám tử hoàn hảo (Holmes) -> Điểm số 110" (= (:diem_so res) 110))
  (check "Thám tử hoàn hảo (Holmes) -> Hạng S" (str/includes? (:ket_luan res) "Hạng S"))
  (check "Thám tử hoàn hảo (Holmes) -> Kết quả cuối cùng hoàn tất" (= (:ket_qua_cuoi_cung res) "Xem xong")))

(let [inputs {:ten_tham_tu "Watson"
              :dung_cu []
              :tiep_can_hien_truong "Phỏng vấn các nghi phạm ngay lập tức"
              :phong_van_nghi_pham "Hỏi Bác sĩ Watson về vết xước trên tay"
              :thu_pham "Cả Quản gia Alfred và Isabella đồng phạm"}
      res (run-expert-loop rpg-form inputs {:marathon true})]
  (check "Thám tử nghiệp dư (Watson) -> Điểm số 40" (= (:diem_so res) 40))
  (check "Thám tử nghiệp dư (Watson) -> Hạng B" (str/includes? (:ket_luan res) "Hạng B")))

(let [inputs {:ten_tham_tu "Clouseau"
              :dung_cu []
              :tiep_can_hien_truong "Phỏng vấn các nghi phạm ngay lập tức"
              :phong_van_nghi_pham "Gặng hỏi Quản gia về mốc thời gian 10h tối"
              :thu_pham "Bác sĩ Watson"}
      res (run-expert-loop rpg-form inputs {:marathon true})]
  (check "Người qua đường (Clouseau) -> Điểm số 0" (= (:diem_so res) 0))
  (check "Người qua đường (Clouseau) -> Hạng F" (str/includes? (:ket_luan res) "Hạng F")))

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

(section "4. Engine eval-expr :not-answered Propagation")

(let [ctx {:selectedByUser {:unanswered :not-answered}}]
  (check "eval :mod with :not-answered"       (= :not-answered (eval-expr [:mod [:var :unanswered] 5] ctx)))
  (check "eval :count with :not-answered"     (= :not-answered (eval-expr [:count [:var :unanswered]] ctx)))
  (check "eval :first with :not-answered"     (= :not-answered (eval-expr [:first [:var :unanswered]] ctx)))
  (check "eval :concat with :not-answered"    (= :not-answered (eval-expr [:concat [:var :unanswered] [:array "Potion"]] ctx)))
  (check "eval :array with :not-answered"     (= :not-answered (eval-expr [:array [:var :unanswered]] ctx))))

(println (str "\n" (apply str (repeat 50 "─"))))
(println (str "KẾT QUẢ SCENARIOS: " @pass-count " passed / " @fail-count " failed"))
(when (pos? @fail-count)
  (System/exit 1))
