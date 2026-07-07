# Thiết Kế Form Phân Nhánh Phi Tuyến Sử Dụng EDN

## 1. Vấn Đề Của Phân Nhánh Tuyến Tính (Dạng Cây)

Trong các thiết kế truyền thống, luồng điều hướng của form được định nghĩa theo cấu trúc lồng nhau (nested tree) bên trong một thuộc tính (như `branch`).
Cấu trúc này mặc dù có vẻ trực quan cho các trường hợp đơn giản, nhưng tồn tại vô số nhược điểm đối với các hệ thống phức tạp:

- **Hạn chế với điều kiện phức hợp:** Một nhánh con hiện chỉ có thể xuất hiện khi **một** câu hỏi cha trả về **một** đáp án cụ thể duy nhất.
- **Lặp lại cấu hình (Vấn đề DRY):** Nếu có nhiều đáp án hoặc các nhánh logic khác nhau cùng hội tụ về một câu hỏi tiếp theo, bạn phải lặp lại việc định nghĩa câu hỏi đó nhiều lần.
- **Thiếu tính linh động trong Logic (AND/OR/NOT):** Rất khó để mô hình hóa các quy tắc thực tế của Hệ Chuyên Gia (Expert System) hay các dạng hội thoại kiểu game. Ví dụ: *"Nếu Bệnh nhân > 60 tuổi VÀ Giới tính = Nữ VÀ Mức đường huyết cao thì hiển thị câu hỏi kê đơn"*.

## 2. Giải Pháp: Danh Sách Phẳng + Động Cơ Quy Tắc Bằng EDN (EDN Logic)

Giải pháp đưa ra là từ bỏ cấu trúc nhánh cây. Thay vào đó, ta mang **toàn bộ câu hỏi ra cùng một cấp (Flat List)** và quản lý việc hiển thị chúng hoặc làm biến đổi chúng thông qua các thuộc tính điều kiện (ví dụ `show-if`).

Đặc biệt nhất, toàn bộ hệ thống lưu trữ form từ nay sẽ vứt bỏ bộ 3 tiêu chuẩn phổ thông (JSON, YAML, TOML) để **chuyển quy chuẩn duy nhất sang EDN (Extensible Data Notation)**.

### Tại sao là EDN?
Khái niệm "EDN Logic" thay thế cho JsonLogic mang lại sức mạnh thiết kế biểu mẫu vượt trội bởi tính "Native" (thuần chủng) với Babashka/Clojure:
1. **Tinh gọn cực đại (Zero Noise):** Không còn hàng tá dấu ngoặc kép lỉnh kỉnh. Dấu phẩy được xem là khoảng trắng.
2. **Comment (Chú thích minh bạch):** EDN hỗ trợ dòng chú thích (`;`), giúp Designer hoặc Coder giải nghĩa trực tiếp ngay sát bên nhánh logic (VD: giải thích tại sao tuổi phải lớn hơn 18).
3. **Cú pháp S-Expression nguyên bản:** Trong EDN, mảng `[:= [:var :tuoi] 18]` có thể được phân tích cú pháp và thi hành ngay lập tức ở Babashka mà gần như không tốn thêm lớp trừu tượng tính toán nào (Zero impedance mismatch). 
4. **Hệ Kiểu Dữ Liệu Sâu Sắc (Rich Types):** EDN cung cấp sẵn Concept cấu trúc cấp cao mà JSON không có: Keywords (`:id`), Tập hợp/Sets (`#{1 2 3}`), hay Symbol. Giúp logic kiểm tra trở nên trong sáng.

## 3. Cấu Trúc File Form Phi Tuyến EDN

Dưới đây là cấu trúc form tổng quan, khai báo bằng EDN.

```clojure
{:title "Chẩn Đoán Thông Minh"
 :fields
 [{:id    :q1
   :type  :radio
   :label "Giới tính (Nam/Nữ)"
   :options [{:label "Nam" :value "Nam"} 
             {:label "Nữ" :value "Nữ"}]}
  
  {:id    :q2
   :type  :number
   :label "Tuổi"}
   
  {:id    :q3
   :label "Trạng thái mang thai"
   :type  :radio
   :options [{:label "Có" :value true} 
             {:label "Không" :value false}]
   ; Logic rẽ nhánh trong EDN: Gọn gàng, dễ lặp cấp và giống Clojure form 100%
   :show-if [:and 
              [:= [:var :q1] "Nữ"]   ; Chỉ hiện nếu rẽ vào nhánh Nữ
              [:>= [:var :q2] 12]]}  ; VÀ tuổi lớn hơn hoặc bằng 12
 ]}
```

## 4. Hiện Thực Hóa Ứng Dụng "Game Cốt Truyện" Với EDN
Tận dụng tính tương tác phi tuyến giống các game đối thoại (*Detroit: Become Human*, *Life is Strange*), kết hợp với sức mạnh cấu trúc của EDN.

### Ví Dụ A. Chẩn Đoán Y Khoa - Sức mạnh của Tập Hợp (Set) EDN
Khác với JSON phải dùng thuật toán lặp mảng (includes) rườm rà. EDN có Set `#{}` giúp logic kiểm tra tập phần tử gãy gọn.

```clojure
{:id      :chest_pain_duration
 :label   "Bạn bị biểu hiện tức ngực trong bao lâu rồi (số ngày)?"
 :type    :number
 :show-if [:or
            [:and
              [:= [:var :gender] "Nam"]
              [:>= [:var :age] 40]
              ; Dùng toán tử contains? với EDN Set #{...} cực nhanh và tường minh
              [:contains? #{"Khám định kỳ" "Hút thuốc" "Tiểu đường"} [:var :medical_history]]]
            [:> [:var :heart_rate] 100]]}
```

### Ví Dụ B. Hệ Thống Thẩm Vấn (Dynamic Label & Action Mutations)
Câu hỏi không chỉ tĩnh, mà nội dung có thể hiển thị linh hoạt dựa vào biến số ngầm. Các "Action" khi trả lời sẽ có tác dụng (side effects) tính toán điểm cộng trừ cho các biến số vô hình.

```clojure
{:id   :q_interrogation_1
 :type :radio
 ; 1. DYNAMIC LABEL: Mảng các trường hợp (Nọi dung thay đổi theo bối cảnh)
 :label [{:show-if [:< [:var :trust_level] 0]
          :text    "Nghe này {{player_name}}, tôi hết kiên nhẫn rồi! Cậu lấy cắp súng đúng không?"}
         {:show-if [:default]  ; EDN Keyword :default biểu đạt rõ ràng hơn true/false
          :text    "Nào {{player_name}}, bình tĩnh lại. Cậu có thấy súng ở hiện trường không?"}]
 
 :options 
 [
   {:label "Nói dối là không thấy"
    :value :lie
    ; 2. ACTIONS: Cập nhật biến trạng thái ngầm, điều khiển kịch bản dài hạn
    ; Ví dụ: trừ điểm thiện cảm, tăng mức căng thẳng
    :actions [[:set :trust_level [:- [:var :trust_level] 10]]
              [:set :stress_level [:+ [:var :stress_level] 20]]]}
   
   {:label "Giao nộp khẩu súng giấu trong áo"
    :value :give_gun
    ; 3. CONDITIONAL OPTIONS: Đáp án có điều kiện, chỉ hiện ra nếu thỏa
    :show-if [:= [:var :found_gun] true]
    :actions [[:set :trust_level [:+ [:var :trust_level] 15]]]}
    
   {:label "Im lặng"
    :value :silence}
 ]
 
 :show-if [:= [:var :in_interrogation_room] true]}
```

## 5. Cơ Chế Biến Ẩn (Hidden Variables) & Hiệu Ứng Cánh Bướm (Butterfly Effect)

Khác biệt lớn nhất giữa một "Form rẽ nhánh thường" và một "Game Đối Thoại Chuyên Sâu" nằm ở sự hiện diện của các **Biến Ẩn (Hidden Variables)**. 

Trong hệ thống rẽ nhánh truyền thống, luồng câu hỏi mắc bệnh **Phụ thuộc trực tiếp**. Ví dụ: Câu hỏi B chỉ hiện nếu Câu hỏi A = "Đồng ý".
Với EDN Form mới, nhờ việc lưu trữ trạng thái nằm ngoài câu trả lời (như ví dụ B ở trên), ta có thể dời đổi tư duy sang **Hệ Quả Tích Lũy**:

1. **Tách Rời Hành Động & Biến Số Ngầm:** Khi user nạp một đáp án, họ không chỉ điền dữ liệu hiển thị. Đáp án đó sẽ chạy ngầm các lệnh `:actions` để bù trừ các chỉ số vô hình (ví dụ: `[:- [:var :trust] 5]`, `[:+ [:var :stress] 10]`). Việc này giống như thanh đạo đức hay thiện cảm trong các game *RPG/Visual Novel*.
2. **Sự Biến Hóa Về Hình Thái (Morphing):** Những biến ẩn này sinh ra "Hiệu ứng cánh bướm". Một câu hỏi ở phần sau form có thể tự động bẻ cong nội dung văn phong (Label) dựa trên điểm cộng dồn từ 10 câu hỏi trước đó lại. Ví dụ: Nếu `trust_level < 0`, cảnh sát sẽ tự động chuyển hệ label sang đe dọa thay vì tra hỏi ôn hòa. Nếu `stress_level > 80`, câu hỏi sẽ hiển thị thêm luồng cảnh báo đỏ. User hoàn toàn không biết họ đang bị "chấm điểm ngầm".
3. **Mở Khóa Tùy Chọn Ẩn (Unlock Hidden Options):** Giống hệt game *Life is Strange*, bạn chỉ có thể có đáp án "Lấy súng bắn" nếu trước đó bạn có tính tò mò đi "mở ngăn kéo". Sự xuất hiện của mỗi Option con cũng được quản lý bởi `show-if` với biến ẩn, giúp ta móc nối các sự kiện rải rác mà không phá vỡ tính mạch lạc của Form.

Cơ chế này biến dự án từ một "Tờ giấy khảo sát điện tử" thăng cấp thành một hệ thống **Nhập vai rẽ nhánh (World State Engine)** thực thụ.

## 6. Giải Quyết Bài Toán Thứ Tự Câu Hỏi (Thuật Toán Restarting Loop)

Khi đưa mảng về Danh Sách Phẳng, ta mất đi tính luồng cây đóng kín. Form không còn đi từ trên xuống dưới một cách hiển nhiên nữa. Lúc này EDN Logic Engine sẽ hoạt động như một Cỗ Máy Trạng Thái (State Machine):

1. Hệ thống duy trì một Map Trạng Thái (State Context) tổng thể:
   ```clojure
   {:answers   {:q_name "Ringo"
                :found_gun true}
    :variables {:trust_level 5
                :stress_level 10}}
   ```
2. **Quét mảng từ Index 0**: Hệ thống duyệt tuần tự đi tìm CÂU HỎI ĐẦU TIÊN thoả mãn:
   - Thuộc tính `:id` chưa tồn tại trong lớp `:answers` của State Map.
   - Các biến số trong `:show-if` khi ráp lệnh chạy qua tính toán trả về `true`.
3. Câu hỏi được trích xuất (gồm cả nội suy Dynamic Label nếu có). User điền và nộp đáp án.
4. **Mutations**: Engine xử lý thuộc tính `:actions` trong option (nếu có) để trừ/cộng giá trị ở vùng `:variables` của State Context và lưu đáp án vô `:answers`.
5. **Cực kỳ quan trọng (Restart):** Quay ngoắt trở về quét lại mảng `fields` từ Index 0 bằng State Context vừa được cập nhật cấu trúc mới.
6. Vòng lặp kết thúc khi hết Form hoặc quét hết mảng mà không có bất kỳ câu hỏi nào thỏa mãn nữa.

## 7. Lợi Thế Hiệu Năng So Với Parse JSON Nhúng
Viết vòng lặp quét ngược liên tục liệu có lãng phí CPU?  

Thực tế thì không, với đặc thù Babashka sử dụng EDN gốc:
* Việc load cấu trúc từ ổ cứng vào RAM chỉ cần `(clojure.edn/read-string file)`. Không cần chuyển đổi cấu trúc trung gian như JSON.
* Logic Engine không cần duyệt AST đệ quy nặng nề như việc tích hợp JsonLogic trên Javascript. Bản chất điều kiện `[:and [:= x 1] [:> y 2]]` chính là một data structure list thuần túy. Ta có thể viết một Macro Interpreter duyệt nhánh trong giới hạn cỡ **5-10 mili-giây** cho một bộ EDN Form cấu trúc vài ngàn dòng để tính toán lại điểm biến đổi. Tốc độ này đủ mượt để xây dựng các Expert System có độ nhạy cực cao dưới hình thức Command Line CLI của Babashka.

---

## 8. Khai Báo Biến Ẩn Ở Đầu Form (Variable Schema)

Thay vì để biến ẩn xuất hiện ngẫu nhiên trong từng `:actions`, ta **khai báo tập trung toàn bộ biến ở đầu form** trong block `:variables`. Điều này mang lại:

- **Tính tường minh:** Designer biết ngay hệ thống đang theo dõi những "thước đo ngầm" nào.
- **Kiểu dữ liệu & ràng buộc:** Ngăn chặn lỗi logic do giá trị vượt ngưỡng.
- **Giá trị khởi tạo rõ ràng:** Không còn "biến bí ẩn từ đâu ra".

```clojure
{:title "Hệ Thống Thẩm Vấn - Vụ Án Số 7"

 ;; ═══════════════════════════════════════════════════════
 ;; KHAI BÁO BIẾN ẨN (STATE SCHEMA)
 ;; Chỉ Engineer/Logic-Designer quan tâm block này.
 ;; Form-Designer chỉ cần biết TÊN biến để dùng trong show-if.
 ;; ═══════════════════════════════════════════════════════
 :variables
 {:trust_level    {:init 50   :type :int   :min 0   :max 100
                   :desc "Độ tin tưởng của thám tử với nghi phạm"}
  :stress_level   {:init 0    :type :int   :min 0   :max 200
                   :desc "Mức căng thẳng tích lũy của nghi phạm"}
  :risk_score     {:init 0.0  :type :float
                   :desc "Điểm rủi ro y tế tổng hợp (tính bằng công thức phức)"}
  :moral_weight   {:init 100  :type :int   :min -200 :max 200
                   :desc "Thước đo đạo đức tích lũy theo chuỗi quyết định"}
  :found_gun      {:init false :type :bool}
  :chapter        {:init 1    :type :int
                   :desc "Chương hiện tại của câu chuyện"}}

 ;; (Tiếp theo: :import và :fields)
 }
```

> **Quy tắc:** Block `:variables` là **source of truth** cho toàn bộ trạng thái ẩn.
> Engine sẽ báo lỗi nếu `:actions` cố ghi vào một biến chưa được khai báo ở đây.

---

## 9. Hệ Thống Import Công Thức (Formula Library)

Vấn đề cốt lõi: các công thức tính toán phức tạp (ví dụ: điểm rủi ro tim mạch, chỉ số tâm lý) **không nên viết lẫn vào file form**. Nó sẽ làm nhiễu người thiết kế kịch bản câu hỏi.

EDN Form giải quyết bằng **cơ chế `:import`** — tương tự `require` trong Clojure, nhưng ở cấp độ dữ liệu:

```clojure
{:title "Chẩn Đoán Tim Mạch Nâng Cao"
 :variables { ... }

 ;; ═══════════════════════════════════════════════════════
 ;; IMPORT THƯ VIỆN CÔNG THỨC
 ;; Các file .edn này chứa các hàm (:fns) và hằng số (:consts)
 ;; được nạp vào namespace riêng, không xung đột với form.
 ;; ═══════════════════════════════════════════════════════
 :import ["./formulas/cardio_risk.edn"        ; Công thức nguy cơ tim mạch (Framingham Score)
          "./formulas/psych_score.edn"         ; Công thức tâm lý học hành vi
          "./formulas/story_engine.edn"]       ; Hàm điều khiển kịch bản game

 :fields [ ... ]
 }
```

### Cấu Trúc File Công Thức (`.edn`)

Mỗi file công thức là một EDN map với các key chuẩn:

```clojure
;; formulas/cardio_risk.edn
;; Thư viện tính điểm nguy cơ tim mạch 10 năm (dựa trên Framingham Score)

{:ns      :cardio-risk        ; Namespace định danh, tránh xung đột tên hàm
 :version "1.2.0"
 :author  "Dr. Nguyen Van An"

 :consts
 {:age-weight      0.49    ; Hệ số tuổi trong mô hình Framingham
  :smoking-penalty 12.0    ; Điểm phạt khi có tiền sử hút thuốc
  :hdl-bonus       -0.8}   ; Hệ số HDL tốt (âm = giảm rủi ro)

 :fns
 {:calc-risk
  ;; Tính điểm rủi ro tổng hợp → trả về float [0.0, 100.0]
  (fn [age gender bmi smoking hdl-level]
    (let [base    (* age (:age-weight consts))
          sex-adj (if (= gender "Nam") 8.0 0.0)
          bmi-adj (* (- bmi 22) 1.5)
          smk-adj (if smoking (:smoking-penalty consts) 0.0)
          hdl-adj (* hdl-level (:hdl-bonus consts))]
      (-> (+ base sex-adj bmi-adj smk-adj hdl-adj)
          (max 0.0)
          (min 100.0))))

  :risk-category
  ;; Phân loại mức độ nguy hiểm dựa trên điểm tổng
  (fn [score]
    (cond
      (< score 10) :low
      (< score 20) :moderate
      (< score 30) :high
      :else        :very-high))}}
```

```clojure
;; formulas/story_engine.edn
;; Hàm quản lý kịch bản phi tuyến — tách hoàn toàn khỏi nội dung form

{:ns :story-engine

 :fns
 {:escalate-stress
  ;; Tăng stress phi tuyến: mỗi lần nói dối stress tăng theo hàm mũ
  (fn [current-stress lie-count]
    (min 200 (+ current-stress (* 10 (Math/pow 1.3 lie-count)))))

  :unlock-chapter?
  ;; Kiểm tra điều kiện mở khóa chương mới dựa trên trust và moral
  (fn [trust moral chapter]
    (case chapter
      1 true
      2 (> trust 20)
      3 (and (> trust 40) (> moral 50))
      4 (or (< trust 10) (> moral 90))  ; Chương "cực đoan" — 2 con đường
      false))}}
```

---

## 10. Gọi Hàm Công Thức Trong `:actions` (Toán Tử `:call`)

Sau khi import, các hàm được gọi trong `:actions` qua toán tử **`:call`** với cú pháp:
`[:call :namespace/ten-ham arg1 arg2 ...]`

```clojure
{:id      :q_medical_history
 :type    :checkbox
 :label   "Bạn có tiền sử hút thuốc không?"
 :options [{:label "Có" :value true}
           {:label "Không" :value false}]

 :actions-on-change
 [;; Gọi hàm từ namespace :cardio-risk để tính lại điểm tổng hợp
  [:set :risk_score
        [:call :cardio-risk/calc-risk
               [:var :age]
               [:var :gender]
               [:var :bmi]
               [:var :smoking]
               [:var :hdl_level]]]

  ;; Gọi hàm stress phi tuyến từ namespace :story-engine
  [:set :stress_level
        [:call :story-engine/escalate-stress
               [:var :stress_level]
               [:var :lie_count]]]

  ;; Sau khi tính risk_score, phân loại ngay (hàm thuần túy, kết quả dùng ở show-if)
  [:set :risk_category
        [:call :cardio-risk/risk-category [:var :risk_score]]]]}
```

### Dùng Kết Quả Hàm Ngay Trong `:show-if`

```clojure
{:id      :q_urgent_warning
 :type    :display                       ; Chỉ hiển thị, không cần trả lời
 :label   "⚠️ CẢNH BÁO: Nguy cơ tim mạch của bạn ở mức RẤT CAO. Hãy đến cơ sở y tế ngay."
 :show-if [:= [:var :risk_category] :very-high]}

{:id      :q_chapter_4_unlock
 :type    :display
 :label   "🔒 Chương 4 đã mở — Con đường không thể quay lại..."
 :show-if [:call :story-engine/unlock-chapter?
                 [:var :trust_level]
                 [:var :moral_weight]
                 4]}                     ; Truyền literal value trực tiếp
```

---

## 11. Phân Tầng Thiết Kế — Tách Bạch Designer & Logic Engineer

Kiến trúc EDN Form với `:variables` + `:import` tạo ra **3 tầng trách nhiệm rõ ràng**, cho phép nhóm làm việc song song mà không xung đột:

```
┌─────────────────────────────────────────────────────────────┐
│  TẦNG 1: FORM DESIGNER  (viết trong :fields của form.edn)   │
│  • Viết :title, :label, :options, bố cục câu hỏi            │
│  • Dùng [:var :ten_bien] trong :show-if (chỉ cần biết tên)  │
│  • Không quan tâm công thức tính biến đó như thế nào        │
├─────────────────────────────────────────────────────────────┤
│  TẦNG 2: LOGIC ENGINEER  (viết :variables + :actions)       │
│  • Khai báo schema biến ẩn: kiểu, ràng buộc, giá trị đầu   │
│  • Gắn :actions-on-change vào từng câu hỏi liên quan        │
│  • Dùng [:call :ns/fn ...] gọi công thức từ tầng 3          │
├─────────────────────────────────────────────────────────────┤
│  TẦNG 3: FORMULA ENGINEER  (viết formulas/*.edn)            │
│  • Viết hàm/hằng số bằng Clojure/EDN thuần túy             │
│  • Unit-test độc lập bằng Babashka (không cần chạy form)    │
│  • Không cần biết form trông như thế nào                    │
└─────────────────────────────────────────────────────────────┘
```

### Cấu Trúc Thư Mục Đề Xuất

```
my-form-project/
├── forms/
│   ├── cardio_diagnosis.edn      ; Form chính (Tầng 1 & 2 viết)
│   └── interrogation_scene.edn   ; Form game kịch bản
│
├── formulas/                     ; Tầng 3 — Formula Engineers quản lý
│   ├── cardio_risk.edn           ; Mô hình Framingham Score
│   ├── psych_score.edn           ; Chỉ số tâm lý hành vi
│   └── story_engine.edn          ; Hàm điều khiển kịch bản
│
├── engine/
│   ├── interpreter.bb            ; EDN Logic Engine (Babashka)
│   └── loader.bb                 ; Xử lý :import, merge namespace
│
└── tests/
    ├── formulas_test.bb          ; Unit test cho từng formula file
    └── form_flow_test.bb         ; Integration test luồng form
```

### Bảng So Sánh Tổng Kết

| Khía cạnh | JSON + JsonLogic | EDN Form + Import |
|---|---|---|
| Logic phức tạp trong form | Lẫn lộn, khó đọc | Tách file riêng, import gọn |
| Unit test công thức | Không thể tách độc lập | Test `formulas/*.edn` riêng biệt |
| Cộng tác nhóm | 1 file to, git conflict cao | 3 tầng tách biệt, ít conflict |
| Tái sử dụng công thức | Copy-paste | `"./formulas/cardio_risk.edn"` dùng lại mọi form |
| Comment giải thích | Không có trong JSON | `;` comment gắn sát logic |
| Biến ẩn khai báo | "Bí ẩn", xuất hiện ngẫu nhiên | Tường minh ở `:variables` đầu form |
| Hàm tính phức tạp | Không thể nhúng hàm vào JSON | `[:call :ns/fn ...]` tự nhiên như Clojure |

---

## 12. Biến Xác Suất (Stochastic Variables) — Từ Điểm Số Sang Phân Phối

### Giới Hạn Của Biến Tất Định (Deterministic)

Trong các section trước, `risk_score` là một số thực cụ thể (`0.72`). Nhưng thực tế của chẩn đoán y khoa hay tâm lý học phức tạp hơn nhiều: **ta không thể chắc chắn 100% về bất kỳ kết luận nào** từ một bộ câu hỏi có hạn. Điều mà ta thực sự biết là một **phân phối xác suất** — "*khả năng cao nhất người này có nguy cơ tim mạch cao, nhưng vẫn còn độ bất định*".

Giải pháp: cho phép biến ẩn có kiểu `:stochastic`, tức là **biến mang giá trị là một phân phối xác suất**, không phải một con số.

### Khai Báo Biến Xác Suất Trong `:variables`

```clojure
:variables
{;; ── BIẾN TẤT ĐỊNH (như cũ) ───────────────────────────────
 :age          {:init 0    :type :int}
 :lie_count    {:init 0    :type :int}

 ;; ── BIẾN XÁC SUẤT (Stochastic) ───────────────────────────
 ;; Thay vì một con số, biến này LÀ một phân phối xác suất.
 ;; :prior khai báo niềm tin ban đầu TRƯỚC KHI có bằng chứng nào.

 :p_cardiac_risk
 {:type    :stochastic
  :backend :conjugate              ; Dùng công thức closed-form (nhanh)
  :prior   {:dist :beta            ; Beta(α, β) — lý tưởng cho xác suất ∈ [0,1]
            :alpha 1.0             ; Uniform prior: chưa biết gì về bệnh nhân
            :beta  1.0}
  :desc    "Xác suất bệnh nhân có nguy cơ tim mạch cao trong 10 năm tới"}

 :p_deception
 {:type    :stochastic
  :backend :conjugate
  :prior   {:dist :beta :alpha 1.0 :beta 5.0}  ; Informative prior: hầu hết người nói thật
  :desc    "Xác suất nghi phạm đang nói dối"}

 :disease_category
 {:type    :stochastic
  :backend :conjugate
  :prior   {:dist  :dirichlet           ; Dirichlet — cho xác suất phân loại nhiều nhãn
            :alpha [1.0 1.0 1.0 1.0]}   ; 4 nhóm bệnh, uniform prior
  :desc    "Phân phối xác suất trên 4 nhóm bệnh: [:low :moderate :high :critical]"}

 :true_stress_level
 {:type    :stochastic
  :backend :stan                         ; Mô hình phức tạp → dùng Stan MCMC
  :model   "./stan/stress_model.stan"    ; File Stan định nghĩa likelihood
  :prior   {:dist :normal :mu 0.0 :sigma 50.0}
  :desc    "Mức stress thực sự ẩn sau hành vi quan sát được (Latent variable)"}}
```

> **Hai chế độ backend:**
> - **`:conjugate`** — Dùng công thức cập nhật Bayes dạng đóng (Beta-Bernoulli, Dirichlet-Categorical, Normal-Normal). Cực nhanh, chạy inline trong Babashka, không cần phần mềm ngoài.
> - **`:stan`** — Gọi Stan qua subprocess khi mô hình đòi hỏi MCMC sampling (mô hình phân cấp, latent variable phức tạp). Chậm hơn nhưng mạnh hơn vô hạn.

---

## 13. Cập Nhật Posterior — Bằng Chứng Từ Câu Trả Lời (`:observe`)

Thay vì `:set` (gán giá trị cứng), biến xác suất được cập nhật bằng toán tử **`:observe`** — tức là "*Tôi vừa quan sát thấy bằng chứng này, hãy cập nhật niềm tin*":

```clojure
{:id      :q_chest_pain
 :type    :radio
 :label   "Bạn có bị đau tức ngực trong tháng qua không?"
 :options
 [{:label "Có, thường xuyên"
   :value :frequent
   :actions
   [;; Quan sát bằng chứng mạnh → cập nhật posterior Beta lên đáng kể
    [:observe :p_cardiac_risk {:likelihood :bernoulli :value 1
                               :weight 3.0}]       ; weight=3 → bằng chứng nặng ký
    [:set     :lie_count [:+ [:var :lie_count] 0]]]  ; biến tất định vẫn dùng :set

  {:label "Có, thỉnh thoảng"
   :value :occasional
   :actions
   [[:observe :p_cardiac_risk {:likelihood :bernoulli :value 1
                               :weight 1.0}]]}      ; bằng chứng yếu hơn

  {:label "Không"
   :value :no
   :actions
   [[:observe :p_cardiac_risk {:likelihood :bernoulli :value 0
                               :weight 1.0}]]}]}    ; bằng chứng âm tính
```

### Cơ Chế Cập Nhật Beta-Bernoulli (Conjugate)

Mỗi khi `:observe` được gọi, Engine thực hiện:

```
Prior:     Beta(α, β)
Evidence:  Bernoulli(value) với trọng số w
Posterior: Beta(α + w·value,  β + w·(1 - value))
```

Sau 5-6 câu hỏi, posterior Beta sẽ hội tụ về vùng phản ánh đúng thực tế bệnh nhân — **không cần bác sĩ tính tay**.

### Backend Stan — Mô Hình Phức Tạp

Với biến khai báo `:backend :stan`, sau mỗi `:observe`, Engine gọi subprocess Stan:

```clojure
;; Trong actions của một câu hỏi
[:observe :true_stress_level
          {:likelihood :normal
           :observed   [:var :response_time_ms]   ; Biến tất định làm proxy
           :sigma      50.0}]
```

```stan
// stan/stress_model.stan
// Engine tự inject dữ liệu quan sát vào block data{}

data {
  int<lower=0> N;
  real observed_response[N];  // Thời gian phản hồi (ms) — proxy của stress
}
parameters {
  real<lower=0> true_stress;  // Latent variable
  real<lower=0> sigma_obs;
}
model {
  true_stress ~ normal(0, 50);           // Prior
  observed_response ~ normal(true_stress, sigma_obs);  // Likelihood
}
```

Engine gọi Stan, lấy mẫu posterior, và lưu lại dưới dạng **summary statistics** (`mean`, `sd`, `q5`, `q95`) vào State Context.

---

## 14. Toán Tử Trên Phân Phối — Dùng Trong `:show-if` & `:label`

Vì biến xác suất không có giá trị đơn, ta cần các **toán tử chuyên biệt** để trích thông tin từ phân phối:

| Toán tử | Ý nghĩa | Trả về |
|---|---|---|
| `[:E :var]` | Giá trị kỳ vọng (expected value / mean) | `float` |
| `[:prob> :var threshold]` | P(var > threshold) | `float ∈ [0,1]` |
| `[:prob< :var threshold]` | P(var < threshold) | `float ∈ [0,1]` |
| `[:credible :var 0.95]` | Khoảng tin cậy 95% | `[lo hi]` |
| `[:mode :var]` | Giá trị có mật độ xác suất cao nhất | `float` |
| `[:argmax :var]` | Nhãn có xác suất cao nhất (cho Dirichlet) | `keyword` |
| `[:entropy :var]` | Độ bất định của phân phối | `float` |

### Ví Dụ Thực Tế — `:show-if` Dùng Xác Suất

```clojure
;; Hiển thị cảnh báo khẩn khi xác suất nguy cơ cao > 70%
{:id      :q_emergency_referral
 :type    :display
 :label   "⚠️ Nguy cơ tim mạch của bạn được đánh giá là RẤT CAO. Cần khám chuyên khoa ngay."
 :show-if [:> [:prob> :p_cardiac_risk 0.5]   ; P(risk > 0.5) > 0.70
               0.70]}

;; Chỉ hỏi thêm khi còn độ bất định cao (entropy lớn) — tránh hỏi thừa
{:id      :q_additional_symptoms
 :type    :multi-checkbox
 :label   "Bạn có triệu chứng nào dưới đây không? (Cần thêm thông tin)"
 :show-if [:> [:entropy :p_cardiac_risk] 0.6]}  ; Còn quá nhiều bất định

;; Mở chương kịch bản dựa trên nhãn có xác suất cao nhất
{:id      :q_verdict
 :type    :display
 :label   "Kết luận điều tra: Nghi phạm thuộc nhóm {{[:argmax :disease_category]}}"
 :show-if [:>= [:prob> :p_deception 0.5] 0.85]}  ; Chắc >85% là nói dối
```

### Dynamic Label Dùng Giá Trị Kỳ Vọng

```clojure
{:id    :q_risk_summary
 :type  :display
 :label [{:show-if [:> [:E :p_cardiac_risk] 0.6]
          :text    "Điểm rủi ro trung bình của bạn là {{[:E :p_cardiac_risk] | percent}}
                    (Khoảng tin cậy 95%: {{[:credible :p_cardiac_risk 0.95]}})"}
         {:show-if [:default]
          :text    "Chưa đủ thông tin để đánh giá nguy cơ. Vui lòng tiếp tục."}]}
```

---

## 15. Kiến Trúc 4 Tầng Mở Rộng — Tích Hợp Probabilistic Backend

Khi thêm biến xác suất, kiến trúc phát triển thành **4 tầng**:

```
┌─────────────────────────────────────────────────────────────────┐
│  TẦNG 1: FORM DESIGNER  (forms/*.edn — phần :fields)            │
│  • Chỉ quan tâm câu hỏi, nhãn, lựa chọn                        │
│  • Dùng [:prob> :var t], [:E :var] trong show-if như bình thường│
│  • Hoàn toàn mù tịt về phân phối là Beta hay Stan               │
├─────────────────────────────────────────────────────────────────┤
│  TẦNG 2: LOGIC ENGINEER  (forms/*.edn — phần :variables)        │
│  • Khai báo :prior, :backend, :model cho biến xác suất          │
│  • Viết [:observe ...] vào :actions với likelihood phù hợp      │
│  • Quyết định dùng :conjugate (nhanh) hay :stan (chính xác)     │
├─────────────────────────────────────────────────────────────────┤
│  TẦNG 3: FORMULA / STATS ENGINEER  (formulas/*.edn + stan/*.stan)│
│  • Viết Stan model (.stan) cho các latent variable phức tạp      │
│  • Viết conjugate update formulas trong EDN (unit-testable)      │
│  • Không cần biết form trông như thế nào                        │
├─────────────────────────────────────────────────────────────────┤
│  TẦNG 4: INFERENCE ENGINE  (engine/probabilistic.bb)            │
│  • Dispatch :observe → conjugate update hoặc Stan subprocess     │
│  • Quản lý State Context: {:answers {...}                        │
│                             :variables {:x 5}                    │
│                             :posteriors {:p_risk Beta(3.2 1.8)}} │
│  • Cache posterior summaries, tránh gọi Stan lại nếu ko đổi     │
└─────────────────────────────────────────────────────────────────┘
```

### Cấu Trúc Thư Mục Cập Nhật

```
my-form-project/
├── forms/
│   └── cardio_diagnosis.edn
│
├── formulas/                        ; Công thức tất định (Section 9)
│   └── cardio_risk.edn
│
├── stan/                            ; Stan models — cho biến xác suất phức
│   ├── stress_model.stan            ; Latent stress từ response time
│   ├── disease_classifier.stan      ; Phân loại bệnh đa nhãn (Dirichlet)
│   └── deception_detector.stan      ; Mô hình phát hiện nói dối
│
├── engine/
│   ├── interpreter.bb               ; EDN Logic Engine
│   ├── loader.bb                    ; Xử lý :import
│   ├── conjugate.bb                 ; Beta-Bernoulli, Dirichlet, Normal-Normal updates
│   └── stan_bridge.bb               ; Gọi Stan subprocess, parse output
│
└── tests/
    ├── conjugate_test.bb            ; Unit test công thức Bayes closed-form
    ├── stan_bridge_test.bb          ; Integration test với Stan
    └── form_flow_test.bb
```

### Bảng So Sánh: Biến Tất Định vs Biến Xác Suất

| Khía cạnh | Biến Tất Định (`:type :int/float`) | Biến Xác Suất (`:type :stochastic`) |
|---|---|---|
| Giá trị lưu trữ | Số đơn (`72`) | Phân phối (`Beta(3.2, 1.8)`) |
| Cập nhật | `[:set :x [:+ [:var :x] 5]]` | `[:observe :x {:likelihood ... :value ...}]` |
| Đọc giá trị | `[:var :x]` | `[:E :x]`, `[:prob> :x 0.5]`, `[:mode :x]` |
| Thể hiện | Điểm số tuyến tính | Độ bất định + phân phối hậu nghiệm |
| Phù hợp với | Score đơn giản, bộ đếm | Chẩn đoán, phân loại, phát hiện nói dối |
| Backend | Babashka thuần | `:conjugate` (Babashka) hoặc `:stan` (subprocess) |
| Câu hỏi thích nghi | Không | Hỏi thêm khi `entropy` còn cao |

---

## 16. Tương Thích Ngược — Unified Variable Protocol

### Nguyên Lý Nền Tảng: Biến Tất Định Là Trường Hợp Đặc Biệt

> **Một biến tất định chứa giá trị `x` tương đương với một biến xác suất có phân phối Dirac delta tại `x` — tức là phân phối đặt toàn bộ xác suất 100% vào một điểm duy nhất, không có độ bất định.**

Từ nguyên lý này, toàn bộ hệ thống được thiết kế theo **Unified Variable Protocol (UVP)**: mọi toán tử hoạt động trên mọi loại biến, chỉ khác nhau về ngữ nghĩa nội tại. Form viết theo kiểu cũ chạy được ngay trên engine mới mà **không cần sửa một dòng nào**.

### Bảng Toán Tử Thống Nhất — Auto-Coercion Rules

| Toán tử | Trên biến tất định | Trên biến xác suất |
|---|---|---|
| `[:var :x]` | Trả về giá trị lưu trữ | Trả về `E[x]` — kỳ vọng (scalar proxy) |
| `[:E :x]` | Trả về giá trị lưu trữ (hằng số = kỳ vọng của Dirac) | Trả về mean của phân phối |
| `[:mode :x]` | Trả về giá trị lưu trữ | Trả về mode của phân phối |
| `[:prob> :x t]` | `1.0` nếu `x > t`, `0.0` nếu không | `P(X > t)` tính từ CDF |
| `[:prob< :x t]` | `1.0` nếu `x < t`, `0.0` nếu không | `P(X < t)` tính từ CDF |
| `[:entropy :x]` | Luôn trả về `0.0` (Dirac — không có bất định) | Entropy của phân phối |
| `[:credible :x c]` | Trả về `[x x]` (khoảng điểm) | Khoảng tin cậy c% |
| `[:argmax :x]` | Trả về chính `x` | Nhãn có xác suất cao nhất |
| `[:set :x val]` | Gán giá trị | Collapse về Dirac delta tại `val` (reset posterior) |
| `[:observe :x ...]` | Gán `value` vào `x` (bỏ qua Bayes) | Bayesian update theo likelihood |

### Ví Dụ: Cùng Form, Hai Chế Độ Hoạt Động

```clojure
;; CÙNG một khai báo :show-if này...
:show-if [:> [:var :risk_score] 0.5]

;; ...hoạt động đúng với CẢ HAI loại biến:

;; Khi :risk_score là :type :float (tất định)
;;   → [:var :risk_score] = 0.72  → 0.72 > 0.5 → true ✓

;; Khi :risk_score là :type :stochastic / Beta(3.2, 1.8) (xác suất)
;;   → [:var :risk_score] = E[X] = 3.2/(3.2+1.8) = 0.64 → 0.64 > 0.5 → true ✓
```

Người dùng không cần thay đổi cú pháp `show-if`. Engine tự xử lý.

### Ví Dụ Thực Tế: Form Hỗn Hợp (Mixed Mode)

Một form có thể dùng **cả hai loại biến song song**, mỗi loại đóng vai trò phù hợp nhất với bài toán:

```clojure
{:title "Chẩn Đoán Tim Mạch — Mixed Mode"

 :variables
 {;; ── BIẾN TẤT ĐỊNH — Đơn giản, dùng cho bộ đếm & điểm cộng trừ
  :age          {:init 0    :type :int}
  :bmi          {:init 0.0  :type :float}
  :lie_count    {:init 0    :type :int}
  :symptom_score {:init 0   :type :int    ; Điểm triệu chứng cộng dồn thủ công
                  :desc "Mỗi triệu chứng xác nhận cộng thêm 10 điểm"}

  ;; ── BIẾN XÁC SUẤT — Dùng cho kết luận chẩn đoán cần độ tin cậy
  :p_cardiac_risk
  {:type    :stochastic
   :backend :conjugate
   :prior   {:dist :beta :alpha 1.0 :beta 1.0}
   :desc    "Xác suất có nguy cơ tim mạch cao — cập nhật tự động theo bằng chứng"}}

 :fields
 [;; Câu hỏi 1: Cập nhật biến TẤT ĐỊNH theo kiểu cổ điển
  {:id      :q_age
   :type    :number
   :label   "Tuổi của bạn?"
   :actions [[:set :age [:var :q_age]]]}

  ;; Câu hỏi 2: Cùng lúc cập nhật CỘNG CẢ HAI loại biến
  {:id      :q_chest_pain
   :type    :radio
   :label   "Bạn có đau tức ngực không?"
   :options
   [{:label "Có"
     :value true
     :actions
     [;; Cập nhật biến tất định theo kiểu cũ
      [:set    :symptom_score [:+ [:var :symptom_score] 10]]
      ;; Đồng thời cập nhật biến xác suất theo Bayes
      [:observe :p_cardiac_risk {:likelihood :bernoulli :value 1 :weight 2.0}]]}
    {:label "Không"
     :value false
     :actions
     [[:observe :p_cardiac_risk {:likelihood :bernoulli :value 0 :weight 1.0}]]}]}

  ;; Câu hỏi 3: show-if dùng CÙNG cú pháp [:var :x] cho cả hai loại
  {:id      :q_ecg_needed
   :type    :display
   :label   "Cần làm ECG ngay."
   ;; Điều kiện kết hợp: điểm cổ điển VÀ xác suất Bayes đều đồng ý
   :show-if [:and
              [:>= [:var :symptom_score] 20]           ; Biến tất định
              [:>  [:var :p_cardiac_risk] 0.6]]}       ; Biến xác suất → tự dùng E[X]

  ;; Câu hỏi 4: Dùng toán tử phong phú khi CẦN độ chính xác cao hơn
  {:id      :q_hospitalize
   :type    :display
   :label   "⚠️ Cần nhập viện khẩn cấp — Xác suất nguy cơ rất cao với độ tin cậy cao."
   :show-if [:and
              [:>  [:prob> :p_cardiac_risk 0.7] 0.80]  ; Phép toán xác suất đầy đủ
              [:< [:entropy :p_cardiac_risk]   0.3]]}  ; VÀ không còn nhiều bất định
 ]}
```

### Quy Tắc Chuyển Đổi (Migration Path)

Form cũ không cần sửa. Muốn nâng cấp một biến từ tất định sang xác suất, chỉ cần đổi khai báo trong `:variables`:

```clojure
;; TRƯỚC (tất định):
:risk_score {:init 0.0 :type :float}

;; SAU (xác suất) — không cần sửa bất kỳ :show-if nào trong :fields:
:risk_score {:type    :stochastic
             :backend :conjugate
             :prior   {:dist :beta :alpha 1.0 :beta 1.0}}
```

Toàn bộ `[:var :risk_score]` trong form tự động đọc `E[risk_score]`. Không cần sửa một dòng `:show-if` nào.

### Dispatch Logic Trong Engine

```clojure
;; engine/interpreter.bb — hàm eval-expr thống nhất

(defn eval-expr [expr state]
  (let [[op & args] expr]
    (case op
      ;; [:var :x] — tự động coerce sang scalar
      :var  (let [v (get-variable state (first args))]
              (if (stochastic? v)
                (expected-value v)      ; E[X] cho biến xác suất
                (:value v)))            ; Giá trị thô cho biến tất định

      ;; [:E :x] — kỳ vọng (explicit, nhưng cũng hoạt động trên tất định)
      :E    (let [v (get-variable state (first args))]
              (if (stochastic? v) (expected-value v) (:value v)))

      ;; [:prob> :x t] — P(X > t), hoạt động trên cả hai loại
      :prob> (let [v (get-variable state (first args))
                   t (eval-expr (second args) state)]
               (if (stochastic? v)
                 (prob-greater-than v t)
                 (if (> (:value v) t) 1.0 0.0)))  ; Dirac delta semantics

      ;; [:entropy :x] — độ bất định
      :entropy (let [v (get-variable state (first args))]
                 (if (stochastic? v) (distribution-entropy v) 0.0))

      ;; [:set :x val] — gán giá trị, hoạt động trên cả hai loại
      :set  (let [[var-name val-expr] args
                  val (eval-expr val-expr state)
                  v   (get-variable state var-name)]
              (if (stochastic? v)
                (collapse-to-dirac state var-name val)   ; Reset posterior về điểm
                (assoc-in state [:variables var-name :value] val)))

      ;; [:observe :x {...}] — Bayesian update, graceful degradation trên tất định
      :observe (let [[var-name evidence] args
                     v (get-variable state var-name)]
                 (if (stochastic? v)
                   (bayesian-update state var-name evidence)
                   ;; Graceful degradation: dùng :value từ evidence như [:set]
                   (assoc-in state [:variables var-name :value]
                             (:value evidence))))
      ; ... các toán tử khác
      )))
```

### Tổng Kết Tính Tương Thích

```
Form viết thuần tất định (cổ điển)
  ├─ Chạy trên engine cũ           ✓ (không đổi)
  └─ Chạy trên engine UVP mới      ✓ (không cần sửa)

Form viết thuần xác suất (mới)
  └─ Chỉ chạy trên engine UVP mới  ✓

Form hỗn hợp (mixed mode)
  └─ Chỉ chạy trên engine UVP mới  ✓

Nâng cấp 1 biến từ tất định → xác suất
  └─ Chỉ sửa :variables, không sửa :fields  ✓
```

---

## 17. Stan Là Inference Engine Chính — Không Viết Lại Thư Viện Xác Suất

### Triết Lý Thiết Kế

Section 12-13 đề cập cả hai backend `:conjugate` và `:stan`. Tuy nhiên, hướng thực dụng hơn là: **Stan làm tất cả**. Stan đã có sẵn đầy đủ họ phân phối (`beta`, `dirichlet`, `normal`, `poisson`, `categorical`, ...), hỗ trợ cả MCMC lẫn Variational Inference, và được tối ưu hóa bằng C++. Không có lý do gì để tự viết lại.

**Pipeline đơn giản:**
```
EDN :variables  ──auto-compile──►  Stan model (.stan)
EDN :observe    ──accumulate──►    Stan data  (.json)
Babashka        ──subprocess──►    CmdStan binary
CmdStan output  ──parse──►         State Context (mean, sd, q5, q95)
```

### Bước 1: EDN `:variables` → Stan Model Tự Động

Mỗi biến `:stochastic` trong EDN được ánh xạ trực tiếp sang các block của Stan. Không cần viết file `.stan` thủ công — engine tự sinh ra từ khai báo EDN.

**Khai báo EDN (Form Designer viết):**
```clojure
:variables
{:p_cardiac_risk
 {:type    :stochastic
  :prior   {:dist :beta :alpha 1.0 :beta 1.0}
  :observe {:likelihood :bernoulli}   ; Mỗi :observe dùng likelihood này
  :desc    "Xác suất nguy cơ tim mạch cao"}

 :true_stress
 {:type    :stochastic
  :prior   {:dist :normal :mu 0.0 :sigma 50.0}
  :observe {:likelihood :normal :sigma [:param]}  ; sigma tự học theo data
  :desc    "Mức stress ẩn"}

 :disease_type
 {:type    :stochastic
  :prior   {:dist :dirichlet :alpha [2.0 2.0 1.0 1.0]}  ; 4 nhóm: low/mod/high/critical
  :observe {:likelihood :categorical}
  :desc    "Phân loại bệnh"}}
```

**Stan model được tự sinh (engine/stan_bridge.bb tạo ra):**
```stan
// AUTO-GENERATED from cardio_diagnosis.edn — DO NOT EDIT MANUALLY
// Generated at: 2026-05-09T10:55:00+07:00

data {
  // ── Biến tất định (từ :answers trong State Context) ──
  int<lower=0>  age;
  real          bmi;

  // ── Observations cho p_cardiac_risk ──
  int<lower=0>           N_cardiac;
  array[N_cardiac] int   obs_cardiac;   // 0 hoặc 1
  vector[N_cardiac]      w_cardiac;     // Trọng số từ :weight

  // ── Observations cho true_stress ──
  int<lower=0>              N_stress;
  vector[N_stress]          obs_stress; // Giá trị liên tục (response time, v.v.)

  // ── Observations cho disease_type ──
  int<lower=0>              N_disease;
  array[N_disease] int      obs_disease; // 1-indexed category
}

parameters {
  // p_cardiac_risk ~ Beta → constrain [0,1]
  real<lower=0, upper=1>  p_cardiac_risk;

  // true_stress ~ Normal → unconstrained
  real                    true_stress;

  // Stan tự học sigma cho likelihood của true_stress
  real<lower=0>           sigma_stress;

  // disease_type ~ Dirichlet → simplex (tổng = 1)
  simplex[4]              disease_type;
}

model {
  // ── PRIORS (ánh xạ trực tiếp từ :prior trong EDN) ──
  p_cardiac_risk ~ beta(1.0, 1.0);
  true_stress    ~ normal(0.0, 50.0);
  sigma_stress   ~ exponential(0.1);     // Hyperprior cho sigma
  disease_type   ~ dirichlet([2.0, 2.0, 1.0, 1.0]');

  // ── LIKELIHOODS (từ observations tích lũy qua :observe) ──

  // p_cardiac_risk: Bernoulli có trọng số
  for (n in 1:N_cardiac) {
    target += w_cardiac[n] * bernoulli_lpmf(obs_cardiac[n] | p_cardiac_risk);
  }

  // true_stress: Normal likelihood
  for (n in 1:N_stress) {
    obs_stress[n] ~ normal(true_stress, sigma_stress);
  }

  // disease_type: Categorical
  for (n in 1:N_disease) {
    obs_disease[n] ~ categorical(disease_type);
  }
}

generated quantities {
  // Entropy của p_cardiac_risk (dùng cho [:entropy :p_cardiac_risk])
  real entropy_cardiac = - (p_cardiac_risk * log(p_cardiac_risk)
                           + (1 - p_cardiac_risk) * log1m(p_cardiac_risk));

  // P(p_cardiac_risk > 0.5) — dùng cho [:prob> :p_cardiac_risk 0.5]
  int  high_risk = (p_cardiac_risk > 0.5);

  // Nhãn bệnh có xác suất cao nhất — dùng cho [:argmax :disease_type]
  int  argmax_disease = categorical_rng(disease_type);
}
```

> **Điểm mấu chốt:** `beta(1.0, 1.0)`, `dirichlet(...)`, `normal(...)` là **hàm Stan gốc** — không phải code ta tự viết. Ta chỉ ánh xạ khai báo EDN sang cú pháp Stan.

### Bước 2: Observations Tích Lũy → Stan JSON Data

Sau mỗi câu trả lời chứa `:observe`, engine cập nhật **observation log** trong State Context và serialize sang JSON cho Stan:

```clojure
;; State Context sau 3 câu hỏi (Babashka in-memory)
{:answers   {:q_age 55 :q_gender "Nam" :q_chest_pain :frequent}
 :variables {:age 55 :bmi 27.3 :lie_count 0}
 :obs-log                          ; Tích lũy mọi :observe theo thời gian
 {:p_cardiac_risk [{:value 1 :weight 3.0}   ; q_chest_pain = :frequent
                   {:value 1 :weight 1.0}]  ; q_smoking = true
  :true_stress    [{:value 412.0}           ; response_time_ms câu hỏi 1
                   {:value 388.0}]}}        ; response_time_ms câu hỏi 2
```

**Babashka serialize sang `data.json` (định dạng CmdStan):**
```json
{
  "age": 55,
  "bmi": 27.3,
  "N_cardiac": 2,
  "obs_cardiac": [1, 1],
  "w_cardiac":   [3.0, 1.0],
  "N_stress": 2,
  "obs_stress":  [412.0, 388.0],
  "N_disease": 0,
  "obs_disease": []
}
```

### Bước 3: Gọi CmdStan Từ Babashka

```clojure
;; engine/stan_bridge.bb

(defn run-stan [model-path data-path output-path opts]
  (let [method    (:method opts :variational)   ; :variational (nhanh) hoặc :sample (chính xác)
        algo      (:algo  opts :meanfield)       ; meanfield | fullrank | nuts
        iter      (:iter  opts 1000)
        cmdstan   (System/getenv "CMDSTAN_HOME")]  ; /usr/local/cmdstan

    (shell/sh (str cmdstan "/bin/cardio_diagnosis")  ; Binary đã compile trước
              (str "method=" (name method))
              (str "algorithm=" (name algo))
              (str "iter=" iter)
              "data"    (str "file=" data-path)
              "output"  (str "file=" output-path))))

(defn infer! [state]
  (let [data-path   (write-stan-data! state)         ; Serialize obs-log → JSON
        output-path "/tmp/stan_output.csv"]
    (run-stan MODEL_PATH data-path output-path
              {:method :variational                   ; ADVI — đủ nhanh cho interactive
               :algo   :meanfield
               :iter   10000})
    (parse-stan-output output-path)))                ; → {:p_cardiac_risk {:mean 0.73 :sd 0.12 :q5 0.51 :q95 0.91}
                                                     ;    :true_stress     {:mean 401.0 :sd 25.0 ...}}
```

### Bước 4: Kết Quả Stan → State Context

CmdStan xuất CSV. Babashka parse lấy summary statistics và lưu vào `:posteriors`:

```clojure
;; State Context sau khi có kết quả Stan
{:answers   { ... }
 :variables { ... }
 :obs-log   { ... }

 ;; KẾT QUẢ TỪ STAN — Engine đọc từ đây khi evaluate show-if
 :posteriors
 {:p_cardiac_risk {:mean 0.73  :sd 0.12  :q5 0.51 :q95 0.91
                   :entropy 0.42              ; Từ generated quantities
                   :prob-above {0.5 0.91      ; P(x > 0.5) = 91%
                                0.7 0.61}}    ; P(x > 0.7) = 61%
  :true_stress    {:mean 401.0 :sd 25.3 :q5 360.0 :q95 445.0}
  :disease_type   {:mean [0.52 0.28 0.13 0.07]   ; Simplex
                   :argmax :low}}}
```

Khi engine evaluate `[:prob> :p_cardiac_risk 0.5]`, nó tra thẳng vào `:posteriors` cache — **không gọi Stan lại** nếu obs-log chưa đổi.

### Lựa Chọn Inference Method — Nhanh vs Chính Xác

Có thể khai báo `method` ở cấp form hoặc biến:

```clojure
{:title "Chẩn Đoán Tim Mạch"

 ;; Cấu hình inference mặc định cho toàn form
 :inference {:method    :variational   ; ADVI — ~100ms mỗi câu hỏi, phù hợp interactive
             :algorithm :meanfield
             :iter      5000}

 :variables
 {:p_cardiac_risk
  {:type    :stochastic
   :prior   {:dist :beta :alpha 1.0 :beta 1.0}
   :observe {:likelihood :bernoulli}
   ;; Override: biến này dùng MCMC đầy đủ (chạy lúc kết thúc form)
   :inference {:method :sample :chains 4 :iter 2000 :warmup 1000}}

  :true_stress
  {:type    :stochastic
   :prior   {:dist :normal :mu 0.0 :sigma 50.0}
   :observe {:likelihood :normal :sigma [:param]}}}}
   ; Kế thừa :inference mặc định từ form → ADVI
```

| Mode | Thời gian | Khi dùng |
|---|---|---|
| `variational / meanfield` | ~50–200ms | Sau mỗi câu trả lời (real-time) |
| `variational / fullrank` | ~500ms | Biến có nhiều correlation |
| `sample` (MCMC/NUTS) | ~5–30s | Lúc form kết thúc, báo cáo cuối |

### Quản Lý Stan Binary

Stan model cần được **compile một lần** thành binary C++ trước khi dùng. Babashka làm việc này lúc load form:

```clojure
;; engine/loader.bb

(defn prepare-form! [form-path]
  (let [form        (edn/read-string (slurp form-path))
        stan-src    (compile-to-stan form)           ; EDN → Stan model string
        stan-file   (cache-path form-path ".stan")
        binary-file (cache-path form-path "")]

    ;; Chỉ recompile nếu form thay đổi
    (when (stale? stan-file form-path)
      (spit stan-file stan-src)
      ;; CmdStan compile: ~10-30s một lần, sau đó cache mãi mãi
      (shell/sh "make" binary-file :dir CMDSTAN_HOME))

    {:form form :binary binary-file}))
```

### Tổng Quan Pipeline Hoàn Chỉnh

```
[Form Designer]
      │ viết cardio_diagnosis.edn
      ▼
[engine/loader.bb]
      │ parse EDN → auto-generate Stan model
      │ compile Stan → binary (một lần, cache)
      ▼
[User trả lời câu hỏi]
      │ engine tích lũy :observe vào obs-log
      │ serialize obs-log → data.json
      ▼
[CmdStan subprocess — ADVI]
      │ ~100ms per câu hỏi
      │ Stan tự xử lý: prior × likelihood → posterior
      ▼
[engine/stan_bridge.bb]
      │ parse CSV output
      │ cập nhật :posteriors trong State Context
      ▼
[Restarting Loop]
      │ evaluate [:var :p_cardiac_risk]  → đọc :mean từ :posteriors
      │ evaluate [:prob> :p_cardiac_risk 0.7] → đọc pre-computed
      │ evaluate [:entropy :p_cardiac_risk]   → đọc từ generated quantities
      ▼
[Hiển thị câu hỏi tiếp theo có show-if = true]
```

> **Lợi thế quyết định:** Ta không viết một dòng toán xác suất nào. `beta(α, β)`, `dirichlet(α)`, `normal(μ, σ)`, `bernoulli_lpmf`, weighted `target +=` — tất cả là **Stan built-in**, được tối ưu và kiểm chứng bởi cộng đồng thống kê học. EDN form chỉ là lớp **khai báo cấu hình** ngồi trên Stan.
