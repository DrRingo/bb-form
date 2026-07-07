# bb-form: Tách Biệt Logic & Presentation

> **Mục tiêu:** Chuyển hoá bb-form từ một công cụ terminal-only sang một nền tảng
> form đa đầu ra (multi-renderer), nơi file `.edn` đóng vai trò là **DSL** thuần
> tuý, và mọi cách hiển thị (terminal GUM, HTML, Windows Forms, v.v.) đều là
> các module độc lập triển khai trên cùng một **Intermediate Representation (IR)**.

---

## 1. Vấn đề hiện tại

Toàn bộ file `src/com/drbinhthanh/bb_form.clj` (~711 dòng) đang **trộn lẫn**
ba mối lo (concerns) hoàn toàn khác nhau:

| Concern | Ví dụ trong code hiện tại |
|---|---|
| **Parse / Load** | `load-form`, `load-formulas!` |
| **Logic Engine** | `eval-expr`, `eval-action`, `run-form` |
| **Presentation** | `gum-input`, `gum-select`, `ask-field`, `render-header` |

Hậu quả:

- Không thể render ra HTML hay WinForms mà không sửa phần logic.  
- Khó test: logic phụ thuộc vào side-effect của GUM.  
- Không có khái niệm "trạng thái form" tách biệt với "giao diện của trạng thái đó".

---

## 2. Tầm nhìn kiến trúc

```
┌─────────────────────────────────────┐
│          EDN / JSON (DSL)           │  ← source của truth
│  forms/job_application.edn, etc.    │
└──────────────────┬──────────────────┘
                   │  parse
                   ▼
┌─────────────────────────────────────┐
│       Form IR  (Clojure map)        │  ← Normalized, renderer-agnostic
│  {:form-meta …  :stages […]         │
│   :fields [{:id … :widget …}]}      │
└──────────────────┬──────────────────┘
                   │  drive
                   ▼
┌─────────────────────────────────────┐
│         Logic Engine (core)         │  ← eval-expr, run-form, state
│  Trả về FormSession:                │
│  {:answers {…} :current-field …}    │
└──────┬───────────────────┬──────────┘
       │ render            │ render
       ▼                   ▼
┌────────────┐    ┌────────────────┐    ┌─ … ──────────────┐
│  GUM       │    │  HTML Server   │    │  WinForms / etc  │
│  Renderer  │    │  Renderer      │    │  Renderer        │
└────────────┘    └────────────────┘    └──────────────────┘
```

---

## 3. Intermediate Representation (IR)

> **Phân biệt DSL và IR:**
> - **DSL (EDN)** = ngôn ngữ tác giả viết — ngắn gọn, dễ đọc, cho phép viết tắt.
> - **IR** = ngôn ngữ engine đọc — đầy đủ, normalised, đã phân tích tĩnh.
>
> Bước `parse → normalize → analyze` phải *tổng hợp* thông tin mà EDN không thể
> hiện trực tiếp: đồ thị phụ thuộc, thứ tự topo, scope biến, v.v. Đây mới là IR
> thực sự — không phải chép lại cấu trúc EDN.

IR là một **Clojure map thuần tuý** được xây dựng qua 3 bước biến đổi:

```
EDN (DSL)
  │  parse          → Raw Clojure map (1:1 với EDN)
  │  normalize      → chuẩn hoá kiểu, widget, label
  │  analyze        → bổ sung dep-graph, topo-order, scope-class
  ▼
Form IR  (renderer-agnostic, engine-ready)
```

---

### 3.1 FieldNode IR — đơn vị cơ bản

Mỗi field trong EDN được nâng cấp thành một **FieldNode** có đầy đủ phân tích
tĩnh (static analysis). Renderer chỉ cần đọc FieldNode; engine chỉ cần đọc
FieldNode — không ai phải đọc lại EDN gốc.

```clojure
{;; ── Định danh ─────────────────────────────────────────────────────────
 :node-id    :lich_su_no_xau          ;; keyword, bất biến, khoá duy nhất
 :stage-id   :stage_1_ca_nhan        ;; stage chứa node này

 ;; ── Loại node & widget hint ───────────────────────────────────────────
 ;; field-type: loại dữ liệu logic — engine quan tâm
 ;; widget    : gợi ý trình bày  — renderer quan tâm
 :field-type :select
 :widget     {:type :dropdown :multi? false}

 ;; ── Nhãn đã phân tích ─────────────────────────────────────────────────
 ;; Static: chuỗi cố định (đã qua interpolate check)
 ;; Dynamic: danh sách branch, mỗi branch có :show-if + :text
 ;;          → renderer render nhãn đúng với context hiện tại
 :label {:kind     :static
         :text     "Bạn có từng bị nợ xấu trong 5 năm qua không?"
         :interp?  false}   ;; true nếu có {{var}} cần nội suy lúc render

 ;; ── Options (chỉ với :select / :radio / :multiselect) ─────────────────
 :options ["Không, tôi luôn trả đúng hạn" "Có, tôi từng bị nợ xấu"]

 ;; ── Điều kiện hiển thị đã phân tích ──────────────────────────────────
 ;; show-if-ast: cây biểu thức gốc — engine eval
 ;; show-if-deps: tập biến mà show-if phụ thuộc — dùng để build dep-graph
 :show-if-ast  nil                    ;; nil = luôn hiện
 :show-if-deps #{}                    ;; tập #{:var-keyword …}

 ;; ── Phân loại vị trí trong đồ thị (topo analysis) ────────────────────
 ;;
 ;;  :forward  — field chỉ phụ thuộc vào các field đứng TRƯỚC nó.
 ;;              Luồng tuyến tính thông thường.
 ;;
 ;;  :backward — field phụ thuộc vào biến do field đứng SAU nó sinh ra.
 ;;              Buộc engine phải chạy restarting loop (không thể resolve
 ;;              trong một lượt quét thẳng từ trên xuống).
 ;;              Ví dụ: :giai_trinh_rui_ro ở đầu stage nhưng chờ
 ;;              :local_risk_points do :lich_su_no_xau sinh ra cuối stage.
 ;;
 ;;  :computed — hidden var: không có UI, chỉ tính toán.
 ;;              Có thể là :forward hoặc :backward tùy dep.
 ;;
 ;;  :terminal — leaf node: không có field nào phụ thuộc vào nó.
 ;;              Engine có thể bỏ qua recheck sau khi field này trả lời.
 ;;
 :topo-class :forward   ;; :forward | :backward | :computed | :terminal

 ;; ── Scope của node ────────────────────────────────────────────────────
 ;;
 ;;  :global  — biến sống suốt form (khai báo trong :variables của form)
 ;;             hoặc field :selectedByUser thông thường.
 ;;
 ;;  :local   — hidden var chỉ có giá trị trong phạm vi stage hiện tại.
 ;;             Sau khi stage kết thúc, engine LOCK biến này (past-hidden-vars).
 ;;             Các stage sau không được đọc giá trị này qua [:var …].
 ;;
 ;;  :cross-stage — biến được khai báo local nhưng được copy sang global
 ;;                 thông qua [:set global-var [:var local-var]] trong :on-complete.
 ;;
 :scope-class :local    ;; :global | :local | :cross-stage

 ;; ── Validation rules (đã normalize) ──────────────────────────────────
 :validations [{:rule :required}
               {:rule :integer}]

 ;; ── Actions (AST gốc, engine eval) ───────────────────────────────────
 ;; actions-ast: danh sách action chạy ngay sau khi field được trả lời.
 ;; actions-write-vars: tập biến mà actions này ghi vào — dùng để build
 ;;                     dep-graph (ai phụ thuộc vào các biến này?).
 :actions-ast       [[:set :global_credit_score [:if …]]]
 :actions-write-vars #{:global_credit_score}

 ;; ── Renderer hints (tuỳ chọn, renderer tự suy ra nếu không có) ───────
 :renderer-hints {:placeholder nil
                  :css-class   "field-credit-history"
                  :icon        "📋"}}
```

---

### 3.2 StageIR — đơn vị tổ chức

```clojure
{:stage-id    :stage_1_ca_nhan

 ;; ── Hooks ─────────────────────────────────────────────────────────────
 :on-begin    [[:print "▶ GIAI ĐOẠN 1: Thu thập thông tin"]]
 :on-complete [[:set :global_total_assets [:var :local_asset_value]]]

 ;; ── Danh sách node đã sắp xếp theo thứ tự EDN gốc ────────────────────
 ;; (thứ tự này là "thứ tự khai báo", KHÔNG phải thứ tự thực thi)
 :field-ids [:giai_trinh_rui_ro :ho_ten :ngay_sinh :lich_su_no_xau
             :local_risk_points :loai_hinh_cong_viec]

 ;; ── Thứ tự thực thi topo trong stage ─────────────────────────────────
 ;; Normalizer phân tích dep-graph nội bộ stage và trả về:
 ;;
 ;; :linear-order   — thứ tự an toàn nếu KHÔNG có backward dependency.
 ;;                   Runner có thể quét một lượt từ trên xuống.
 ;;
 ;; :requires-loop? — true nếu stage có ít nhất một :backward node.
 ;;                   Runner PHẢI dùng restarting loop cho stage này.
 ;;
 ;; :loop-triggers  — map: biến → danh sách node bị ảnh hưởng khi biến thay đổi.
 ;;                   Cho phép runner biết: sau khi :local_risk_points thay đổi,
 ;;                   chỉ cần recheck [:giai_trinh_rui_ro], không cần recheck toàn bộ stage.
 ;;                   (Tối ưu hoá: tránh re-render không cần thiết)
 ;;
 :execution-model
 {:linear-order   [:ho_ten :ngay_sinh :lich_su_no_xau
                   :local_risk_points :loai_hinh_cong_viec
                   :giai_trinh_rui_ro]      ;; backward node đặt cuối
  :requires-loop? true
  :loop-triggers  {:local_risk_points [:giai_trinh_rui_ro]
                   :global_credit_score []}}}
```

---

### 3.3 FormIR — toàn bộ form

```clojure
{;; ── Metadata ──────────────────────────────────────────────────────────
 :form-meta
 {:title       "Hệ Thống Thẩm Định Vay Vốn Đa Tầng"
  :description "..."
  :source-file "forms/loan_approval.edn"
  :version     1}

 ;; ── Biến toàn cục — đã được typed (infer từ giá trị khởi tạo) ────────
 :global-vars
 {:global_credit_score {:initial 100  :inferred-type :integer}
  :global_total_assets {:initial 0    :inferred-type :integer}
  :loan_status         {:initial "Chờ thẩm định" :inferred-type :string}}

 ;; ── Imports đã được resolve ──────────────────────────────────────────
 :imports [{:path "../formulas/candidate_score.clj"
            :alias :score
            :resolved? true
            :exported-fns #{:analyze :summarize}}]

 ;; ── Đồ thị phụ thuộc toàn form (cross-stage dependency graph) ────────
 ;;
 ;; Đây là thứ normalizer *suy ra* từ DSL — DSL không biểu diễn tường minh.
 ;; dep-graph[node-id] = tập các node-id mà node này phụ thuộc vào
 ;; (xét cả show-if-deps và actions-write-vars theo chiều ngược)
 ;;
 :dep-graph
 {:thong_bao_chap_thuan  #{:local_du_dieu_kien}
  :local_du_dieu_kien    #{:global_credit_score :global_total_assets :so_tien_muon_vay}
  :giai_trinh_rui_ro     #{:local_risk_points}
  :local_risk_points     #{:lich_su_no_xau}
  :thong_tin_nguoi_dong_so_huu #{:danh_sach_tai_san}}

 ;; ── Các node viết vào biến toàn cục (cross-stage write map) ──────────
 ;; write-map[var-keyword] = node-id nào ghi vào biến đó
 ;; Dùng để phát hiện cross-stage dependency khi stage sau đọc biến mà stage trước ghi.
 :write-map
 {:global_credit_score  [:lich_su_no_xau :loai_hinh_cong_viec]
  :global_total_assets  [:stage_2_tai_san/on-complete]
  :loan_status          [:thong_bao_chap_thuan :thong_bao_tu_choi]}

 ;; ── Danh sách stages theo thứ tự ─────────────────────────────────────
 :stages
 [<StageIR stage_1_ca_nhan>
  <StageIR stage_2_tai_san>
  <StageIR stage_3_quyet_dinh>]

 ;; ── Index nhanh: node-id → stage-id (tra cứu O(1)) ──────────────────
 :node-index
 {:giai_trinh_rui_ro           :stage_1_ca_nhan
  :ho_ten                      :stage_1_ca_nhan
  :lich_su_no_xau              :stage_1_ca_nhan
  :local_risk_points           :stage_1_ca_nhan
  :so_tien_muon_vay            :stage_2_tai_san
  :danh_sach_tai_san           :stage_2_tai_san
  :thong_bao_chap_thuan        :stage_3_quyet_dinh
  :thong_bao_tu_choi           :stage_3_quyet_dinh}}
```

---

### 3.4 FormSession — trạng thái runtime

`FormSession` là output của engine sau mỗi bước — **không phải IR** (IR là bất
biến sau parse), nhưng nó phải tương thích với IR để renderer có thể render
đúng field theo `FieldNode` tương ứng.

```clojure
{;; ── Answers: dữ liệu người dùng đã nhập ─────────────────────────────
 :answers
 {:selectedByUser {:ho_ten "Nguyễn A" :lich_su_no_xau "Có, tôi từng bị nợ xấu"}
  :HiddenVar      {:global_credit_score 50
                   :local_risk_points   50
                   :local_du_dieu_kien  false}}

 ;; ── Navigation state ──────────────────────────────────────────────────
 :current-stage-id  :stage_1_ca_nhan
 :pending-node-id   :giai_trinh_rui_ro   ;; node đang chờ input; nil = stage done

 ;; ── Loop state (chỉ có nghĩa khi stage :requires-loop? = true) ───────
 ;; Ghi lại trạng thái của restarting loop để renderer biết
 ;; đây là lần hỏi "bổ sung" (backward), không phải lần hỏi đầu tiên.
 :loop-pass         2                    ;; lần quét thứ mấy trong stage
 :newly-visible     #{:giai_trinh_rui_ro} ;; node vừa trở nên visible trong pass này

 ;; ── Scope locks ──────────────────────────────────────────────────────
 ;; Sau khi stage kết thúc, engine lock các local var để stage sau không đọc được
 :locked-vars       #{}                  ;; tăng lên sau mỗi stage hoàn tất

 ;; ── Validation errors ────────────────────────────────────────────────
 :validation-errors {}

 ;; ── Form lifecycle ────────────────────────────────────────────────────
 :form-status       :in-progress   ;; :not-started | :in-progress | :complete | :error
 :status-message    "⚠️ Điểm rủi ro cao, cần giải trình"}
```

---

### 3.5 Sơ đồ biến đổi DSL → IR

Đây là những gì bước **analyze** *bổ sung* so với DSL gốc — phần IR thực sự:

| Thuộc tính IR | Có trong DSL? | Được suy ra từ |
|---|:---:|---|
| `:show-if-deps` | ❌ | Phân tích tĩnh cây `:show-if-ast` |
| `:actions-write-vars` | ❌ | Phân tích tĩnh cây `:actions-ast` |
| `:topo-class` (`:backward` / `:forward`) | ❌ | So sánh vị trí khai báo vs dep-graph |
| `:scope-class` (`:local` / `:global`) | Một phần | `:hidden` → `:local`; `:variables` → `:global` |
| `:dep-graph` (toàn form) | ❌ | Union của `show-if-deps` + `write-map` |
| `:execution-model` (`:requires-loop?`, `:loop-triggers`) | ❌ | Phát hiện cycle trong dep-graph nội bộ stage |
| `:write-map` | ❌ | Tổng hợp `actions-write-vars` của tất cả nodes |
| `:node-index` | ❌ | Index ngược từ node-id → stage-id |
| `:label.interp?` | ❌ | Phát hiện `{{…}}` trong chuỗi label |
| `:widget` | Một phần | Infer từ `:field-type` nếu không khai báo tường minh |

---

## 4. Phân tách Module

### 4.1 Cây thư mục đề xuất

```
src/com/drbinhthanh/
├── core/
│   ├── parser.clj        ;; EDN/JSON → Raw Clojure map (1:1)
│   ├── normalizer.clj    ;; chuẩn hoá keyword, widget, label, validations
│   ├── analyzer.clj      ;; static analysis → dep-graph, topo-class,
│   │                     ;;   scope-class, execution-model, write-map
│   │                     ;;   (tạo ra FormIR hoàn chỉnh từ normalized map)
│   ├── engine.clj        ;; eval-expr, eval-action (pure functions)
│   └── runner.clj        ;; run-form: đọc FormIR, emit FormSession events
│                         ;;   (dùng :requires-loop? / :loop-triggers từ IR)
│
├── renderers/
│   ├── protocol.clj      ;; FormRenderer protocol / interface
│   ├── gum.clj           ;; Terminal renderer (GUM)
│   ├── html.clj          ;; HTML/Hiccup renderer
│   └── winforms.clj      ;; (placeholder) Windows Forms renderer
│
└── bb_form.clj           ;; CLI entry point (mỏng, chỉ wire các module)
```

### 4.2 FormRenderer Protocol

```clojure
(ns com.drbinhthanh.renderers.protocol)

(defprotocol FormRenderer
  ;; Render toàn bộ form header (title, description, status)
  (render-header [this form-meta session])

  ;; Render một field và trả về giá trị người dùng nhập
  ;; Trả về: {:value <any> :action :next | :back | :quit}
  (render-field [this field session])

  ;; Render thông báo lỗi validation
  (render-error [this field-id message session])

  ;; Render thông báo hoàn tất
  (render-complete [this session])

  ;; Render info field (không cần input, chỉ đọc)
  (render-info [this field session])

  ;; Chờ một sự kiện tiếp theo từ người dùng (Enter, click, v.v.)
  (await-continue [this session]))
```

### 4.3 GUM Renderer (hiện tại → refactor)

```clojure
(ns com.drbinhthanh.renderers.gum
  (:require [com.drbinhthanh.renderers.protocol :refer [FormRenderer]]
            [babashka.process :refer [shell]]
            [clojure.string :as str]))

(defrecord GumRenderer []
  FormRenderer
  
  (render-header [_ form-meta session]
    (println "\n📝" (:title form-meta))
    (println (:description form-meta) "\n")
    (when-let [msg (:status-message session)]
      (when-not (str/blank? msg)
        (shell {:out :inherit} "gum" "style"
               "--foreground" "#ff0000" msg))))

  (render-field [_ field session]
    (let [value (case (:field-type field)
                  :text        (-> (shell {:out :string} "gum" "input"
                                          "--placeholder" (get-in field [:label :text]))
                                   :out str/trim)
                  :number      (-> (shell {:out :string} "gum" "input" …) :out str/trim)
                  :select      (-> (apply shell {:out :string} "gum" "choose"
                                          "--header" (get-in field [:label :text])
                                          (:options field))
                                   :out str/trim)
                  :multiselect (-> (apply shell {:out :string} "gum" "choose"
                                          "--no-limit" "--header" …
                                          (:options field))
                                   :out str/split-lines))]
      {:value value :action :next}))

  (render-error [_ _ message _]
    (shell {:out :inherit} "gum" "style"
           "--foreground" "#ff0000" "--border" "normal" message))

  (render-complete [_ session]
    (println "\n✅ Form hoàn tất!"))

  (render-info [_ field session]
    (println "\n" (get-in field [:label :text]))
    (shell {:out :inherit} "gum" "input" "--placeholder" "Nhấn Enter để tiếp tục..."))

  (await-continue [_ _]
    (shell {:out :inherit} "gum" "input" "--placeholder" "Nhấn Enter để tiếp tục...")))
```

### 4.4 HTML Renderer (mới)

```clojure
(ns com.drbinhthanh.renderers.html
  (:require [com.drbinhthanh.renderers.protocol :refer [FormRenderer]]
            [hiccup.core :refer [html]]
            [ring.adapter.jetty :refer [run-jetty]]))

;; Server-side: dùng hiccup để render HTML; state được giữ trong atom/session
;; Client-side: sử dụng HTMX để cập nhật từng field không load lại trang

(defrecord HtmlRenderer [server-state]
  FormRenderer

  (render-header [_ form-meta session]
    (html
     [:div.form-header
      [:h1 (:title form-meta)]
      [:p (:description form-meta)]
      (when-let [msg (:status-message session)]
        [:div.status-error msg])]))

  (render-field [_ field session]
    ;; Render HTML element. Trong server model, trả về hiccup fragment.
    ;; Giá trị người dùng nhập được nhận qua HTTP POST (HTMX / form submit).
    (html
     [:div.field {:id (name (:id field))}
      [:label (get-in field [:label :text])]
      (case (:field-type field)
        :text     [:input {:type "text"
                           :name (name (:id field))
                           :placeholder (get-in field [:renderer-hints :placeholder])}]
        :number   [:input {:type "number" :name (name (:id field))}]
        :select   [:select {:name (name (:id field))}
                   (map (fn [opt] [:option {:value opt} opt]) (:options field))]
        :multiselect (map (fn [opt]
                            [:div [:input {:type "checkbox" :name (name (:id field)) :value opt}]
                             [:span opt]])
                          (:options field)))]))

  (render-error [_ field-id message _]
    (html [:div.error {:data-field (name field-id)} message]))

  (render-complete [_ session]
    (html [:div.complete "✅ Form đã hoàn tất. Dữ liệu đã được ghi nhận."]))

  (render-info [_ field session]
    (html [:div.info (get-in field [:label :text])]))

  (await-continue [_ _]
    ;; Trong web model: không block, chờ HTTP request tiếp theo
    nil))
```

---

## 5. Logic Engine (Pure, Renderer-agnostic)

`engine.clj` chứa `eval-expr` và `eval-action` **không thay đổi về ngữ nghĩa**,
nhưng được tách ra khỏi mọi side-effect liên quan đến GUM hay I/O:

```clojure
(ns com.drbinhthanh.core.engine)

;; eval-expr: PURE. Nhận AST + context → giá trị
(defn eval-expr [expr context] …)

;; eval-action: nhận action AST + context atom → cập nhật state
;; KHÔNG có :print side-effect GUM ở đây, thay bằng event
(defn eval-action [action context-atom emit-event-fn]
  (let [[op & args] action]
    (case op
      :set   (swap! context-atom assoc-in [:HiddenVar (keyword (first args))]
                    (eval-expr (second args) @context-atom))
      ;; :print không render trực tiếp → emit event để renderer quyết định
      :print (emit-event-fn {:type :message
                             :text (eval-expr (first args) @context-atom)})
      nil)))
```

`runner.clj` điều phối logic, nhưng giao tiếp với renderer qua **event-driven
callback**, không block trực tiếp:

```clojure
(ns com.drbinhthanh.core.runner)

(defn run-form
  "Chạy form theo IR. Giao tiếp với renderer qua callback.
   renderer: implements FormRenderer protocol
   on-complete: fn nhận FormSession cuối cùng"
  [form-ir renderer on-complete]
  …)
```

---

## 6. Luồng dữ liệu chi tiết

```
EDN file
  │
  │ parse (parser.clj)
  │   - slurp + edn/read-string → Raw Clojure map (1:1 với EDN)
  ▼
Raw Clojure map
  │
  │ normalize (normalizer.clj)
  │   - :type → :field-type (keyword)
  │   - :label string → {:kind :static :text … :interp? false}
  │   - infer :widget từ :field-type nếu thiếu
  │   - expand :fields flat → [{:stage-id :main :fields […]}]
  ▼
Normalized map
  │
  │ analyze (analyzer.clj)         ← đây là bước tạo ra IR thực sự
  │
  │   Với mỗi FieldNode:
  │   ├─ trích xuất :show-if-deps   (walk AST tìm [:var …])
  │   ├─ trích xuất :actions-write-vars (walk AST tìm [:set var …])
  │   └─ gán :scope-class           (:hidden → :local, else :global)
  │
  │   Với mỗi StageIR:
  │   ├─ build dep-graph nội bộ stage
  │   ├─ phát hiện :backward nodes  (dep vào var do node SAU sinh)
  │   ├─ tính :linear-order         (topo sort, backward nodes về cuối)
  │   ├─ đặt :requires-loop?        (true nếu có backward node)
  │   └─ tính :loop-triggers        {var → [node-id …]}
  │
  │   Với FormIR:
  │   ├─ build :dep-graph           (union tất cả stage dep-graphs)
  │   ├─ build :write-map           (aggregated actions-write-vars)
  │   └─ build :node-index          {node-id → stage-id}
  ▼
FormIR (stable, renderer-agnostic, engine-ready)
  │
  │ runner.run-form(form-ir, renderer, on-complete)
  ▼
  ┌───────────────────────────────────────────────────┐
  │         Runner Loop (IR-driven)                   │
  │                                                   │
  │  for each stage-ir in form-ir.stages:             │
  │    run on-begin actions                           │
  │    render-header(form-meta, session)              │
  │                                                   │
  │    if stage-ir.execution-model.requires-loop?:    │
  │      ;; Restarting loop — driven by loop-triggers │
  │      loop:                                        │
  │        newly-visible = eval show-if all nodes     │
  │        for node in linear-order if visible:       │
  │          if not answered: render-field → answer   │
  │          eval actions → dirty-vars                │
  │          affected = loop-triggers[dirty-vars]     │
  │        if newly-visible is empty: break           │
  │    else:                                          │
  │      ;; Linear scan — no loop needed              │
  │      for node in linear-order:                    │
  │        if show-if passes: render-field → answer   │
  │        eval actions                               │
  │                                                   │
  │    run on-complete actions                        │
  │    lock stage local vars → session.locked-vars    │
  └───────────────────────────────────────────────────┘
  │
  ▼
FormSession (final)
  │
  │ write-output! (edn / json)
  ▼
result.edn | result.json
```

---

## 7. Chiến lược triển khai

### Phase 0 — Không thay đổi hành vi (Refactor nội tại)

1. **Tách `engine.clj`**: di chuyển `eval-expr`, `eval-action`, `eval-actions`
   sang `core/engine.clj`. Đảm bảo pure.
2. **Tách `parser.clj`**: di chuyển `load-form`, `load-formulas!`,
   `detect-format`, `write-output!` sang `core/parser.clj`.
3. **Tách `gum.clj`**: di chuyển tất cả hàm `gum-*`, `ask-field`,
   `render-header`, `show-error`, `run-form` sang `renderers/gum.clj`.
   Implement `FormRenderer` protocol.
4. **`bb_form.clj`** chỉ còn: parse CLI args → load IR → wire GumRenderer →
   `run-form` → `write-output!`.

> Tất cả test hiện tại (nếu có) phải pass sau phase này.

### Phase 1 — HTML Renderer

1. Thêm `renderers/html.clj` implement `FormRenderer`.
2. Dùng **Ring + Hiccup + HTMX**: mỗi bước form là một HTMX fragment thay thế
   partial UI.
3. CLI thêm flag `--renderer html --port 8080` → khởi động server,
   mở browser.
4. Session được giữ bằng Ring session (cookie) hoặc EDN file tạm.

### Phase 2 — WinForms / Desktop Renderer

1. Bọc Clojure (Babashka) trong một script nhận JSON qua stdin (JSON RPC
   protocol đơn giản).
2. Host C# / Python (tkinter) giao tiếp qua stdin/stdout với engine.
3. Renderer phía desktop implement protocol theo ngôn ngữ đó, engine vẫn là
   Clojure.

### Phase 3 — Liveness & Incremental Evaluation (tùy chọn nâng cao)

- Engine emit **FieldEvent stream** thay vì block từng field:
  `{:type :field-visible :field-id :ho_ten}`,
  `{:type :field-value-changed :field-id :ui_score :value 8}`.
- Renderer subscribe và cập nhật UI reactive (thích hợp cho SPA / live form).

---

## 8. Ví dụ: cùng một Form IR, hai Renderer

### 8.1 GUM Terminal

```
📝 Hệ thống Phân Tích Kỹ Năng Ứng Viên

> Họ và tên ứng viên: _
```

### 8.2 HTML (với Hiccup + HTMX)

```html
<div class="form-container">
  <h1>Hệ thống Phân Tích Kỹ Năng Ứng Viên</h1>
  <div id="field-ho_ten" class="field">
    <label>Họ và tên ứng viên</label>
    <input type="text" name="ho_ten"
           hx-post="/form/next"
           hx-target="#field-ho_ten"
           hx-swap="outerHTML">
  </div>
</div>
```

---

## 9. Câu hỏi mở & Quyết định cần thống nhất

| # | Câu hỏi | Gợi ý |
|---|---------|-------|
| 1 | Renderer có cần biết về **thứ tự field** hay để runner quản lý? | Runner quản lý thứ tự, renderer chỉ render một field mỗi lần |
| 2 | HTML renderer: **full-page reload** hay **HTMX fragment**? | HTMX fragment — progressive enhancement, không cần SPA framework |
| 3 | **Session storage** cho HTML renderer: cookie, in-memory atom, hay EDN file? | In-memory atom cho dev; EDN file / SQLite cho production |
| 4 | **Multi-language support**: renderer nhận locale hay form IR có sẵn i18n? | Thêm `:i18n {:vi {…} :en {…}}` vào Form IR |
| 5 | **Widget hints** nên là phần của DSL hay chỉ do renderer tự suy ra? | Cả hai: DSL có thể `override`, renderer có default suy ra từ `:field-type` |
| 6 | Phase 0 refactor có phá vỡ `bb.edn` script aliases không? | Không, entry point `-main` vẫn ở `bb_form.clj` |

---

## 10. Tóm tắt nguyên tắc thiết kế

> **"EDN là sự thật duy nhất. IR là ngôn ngữ chung. Renderer là phương ngữ."**

1. **EDN = DSL**: file `.edn` chỉ mô tả **ý định** (intent), không phụ thuộc
   vào bất kỳ công nghệ render nào.
2. **IR = Giao ước**: mọi renderer đều nói chuyện qua cùng một schema. Thêm
   renderer mới không cần sửa engine hay parser.
3. **Engine = Pure Logic**: `eval-expr` là hàm thuần tuý — dễ test, dễ port
   sang ngôn ngữ khác nếu cần.
4. **Renderer = Plugin**: implement `FormRenderer` protocol là đủ để trở thành
   một output target hợp lệ.
5. **FormSession = Trạng thái form**: renderer không tự duy trì state — mọi
   state đi qua `FormSession`, engine cập nhật, renderer chỉ đọc.
