(ns com.drbinhthanh.bb-form.engines.formsmd
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [com.drbinhthanh.bb-form.core :as core]
            [clojure.java.shell :as shell]
            [org.httpkit.server :as httpkit]))

(defonce warned-functions (atom #{}))

(defn- format-value [v]
  (cond
    (nil? v) nil
    (string? v) (str "\"" v "\"")
    :else (str v)))

;; ─────────────────────────────────────────────
;; Expression Compiler: EDN logic → Nunjucks JS
;; ─────────────────────────────────────────────

(defn compile-expr [expr]
  (cond
    (vector? expr)
    (let [[op & args] expr]
      (case op
        :var     (name (first args))
        :get     (str (compile-expr (first args)) "." (compile-expr (second args)))
        :and     (str "(" (str/join " and " (map compile-expr args)) ")")
        :or      (str "(" (str/join " or "  (map compile-expr args)) ")")
        :not     (str "not (" (compile-expr (first args)) ")")
        :=       (str "(" (compile-expr (first args)) " == "  (compile-expr (second args)) ")")
        :!=      (str "(" (compile-expr (first args)) " != "  (compile-expr (second args)) ")")
        :>       (str "(" (compile-expr (first args)) " > "   (compile-expr (second args)) ")")
        :<       (str "(" (compile-expr (first args)) " < "   (compile-expr (second args)) ")")
        :>=      (str "(" (compile-expr (first args)) " >= "  (compile-expr (second args)) ")")
        :<=      (str "(" (compile-expr (first args)) " <= "  (compile-expr (second args)) ")")
        :if      (str "(" (compile-expr (first args)) " ? "
                         (compile-expr (second args)) " : "
                         (compile-expr (nth args 2)) ")")
        :+       (str "(" (str/join " + " (map compile-expr args)) ")")
        :-       (str "(" (str/join " - " (map compile-expr args)) ")")
        :*       (str "(" (str/join " * " (map compile-expr args)) ")")
        :/       (str "(" (str/join " / " (map compile-expr args)) ")")
        :contains?    (str (compile-expr (first args)) " contains " (compile-expr (second args)))
        :count        (str "(" (compile-expr (first args)) " | length)")
        :first        (str (compile-expr (first args)) "[0]")
        :str/includes?   (str (compile-expr (first args)) " contains " (compile-expr (second args)))
        :str/lower-case  (str "(" (compile-expr (first args)) " | lower)")
        :str/upper-case  (str "(" (compile-expr (first args)) " | upper)")
        :call (let [func      (first args)
                    ns-part   (namespace func)
                    name-part (name func)
                    full-name (if ns-part (str ns-part "/" name-part) name-part)
                    func-name (str/replace full-name #"[-/]" {"-" "_" "/" "."})
                    params    (rest args)]
                (when-not (contains? @warned-functions full-name)
                  (swap! warned-functions conj full-name)
                  (println (str "⚠️  [formsmd] Hàm gọi ngoài: '" full-name
                                "' → JS: '" func-name
                                "'. Đảm bảo hàm này được khai báo trên đối tượng window trong HTML.")))
                (str func-name "(" (str/join ", " (map compile-expr params)) ")"))
        ;; fallback: unknown op, stringify
        (str expr)))
    (keyword? expr) (str "'" (name expr) "'")
    (string? expr)  (str "\"" expr "\"")
    :else           (str expr)))

;; ─────────────────────────────────────────────
;; Variable extraction for reactive block deps
;; ─────────────────────────────────────────────

(defn extract-vars [expr]
  (cond
    (and (vector? expr) (= :var (first expr)))
    #{(name (second expr))}

    (vector? expr)
    (apply set/union (map extract-vars (rest expr)))

    :else #{}))

;; ─────────────────────────────────────────────
;; Text interpolation: {{expr}} → {$ ... $} / {{ ... }}
;; ─────────────────────────────────────────────

(defn interpolate-text [text in-reactive?]
  (if (string? text)
    (str/replace text #"\{\{(.+?)\}\}"
                 (fn [[_ content]]
                   (let [expr     (try (clojure.edn/read-string content) (catch Exception _ content))
                         compiled (cond
                                    (vector? expr)  (compile-expr expr)
                                    (symbol? expr)  (name expr)
                                    (keyword? expr) (name expr)
                                    :else           (str expr))]
                     (if in-reactive?
                       (str "{{ " compiled " }}")
                       (str "{$ " compiled " $}")))))
    text))

;; ─────────────────────────────────────────────
;; Reactive block helper
;; ::: [{$ dep1 dep2 $}]  content :::
;; ─────────────────────────────────────────────

(defn- deps->str [deps]
  (if (seq deps)
    (str "[{$ " (str/join " " (sort deps)) " $}]")
    ""))

(defn- reactive-block [deps-set content]
  (let [ds (deps->str deps-set)]
    (str ":::" (when (seq ds) (str " " ds)) "\n"
         content "\n"
         ":::")))

;; ─────────────────────────────────────────────
;; Form settings (#! lines)
;; ─────────────────────────────────────────────

(defn generate-settings [form form-name]
  (let [id       (or (:id form) form-name)
        settings (cond-> [(str "#! id = " id)]
                   (:submit-button-text form) (conj (str "#! submit-button-text = " (:submit-button-text form)))
                   (:restart-button form)     (conj (str "#! restart-button = " (:restart-button form)))
                   (:post-url form)           (conj (str "#! post-url = " (:post-url form))))]
    (str (str/join "\n" settings) "\n\n")))

;; ─────────────────────────────────────────────
;; Global variables → Nunjucks {% set %} block
;; ─────────────────────────────────────────────

(defn compile-variables [variables]
  (if (seq variables)
    (let [stmts (for [[k v] variables]
                  (str "{% set " (name k) " = " (compile-expr v) " %}"))]
      ;; Wrap in a no-dep reactive block so formsmd picks them up
      (str (reactive-block #{} (str/join "\n" stmts)) "\n\n"))
    ""))

;; ─────────────────────────────────────────────
;; Field compiler
;; ─────────────────────────────────────────────

(defn compile-field [field]
  (let [{:keys [id type label required description placeholder options value regex min max step form]} field
        required? (true? required)
        id-str    (name id)
        req-str   (if required? "*" "")
        form-val  (when form (str/lower-case (str/trim (str form))))
        constructor (cond
                      (= form-val "email") "EmailInput"
                      (= form-val "url") "URLInput"
                      (or (= form-val "tel") (= form-val "telephone")) "TelInput"
                      (= form-val "password") "PasswordInput"
                      (= form-val "rating") "RatingInput"
                      (= form-val "opinionscale") "OpinionScale"
                      (= form-val "datetime") "DatetimeInput"
                      (= form-val "time") "TimeInput"
                      :else (case (keyword type)
                              :text        "TextInput"
                              :number      "NumberInput"
                              :date        "DateInput"
                              :select      "SelectBox"
                              :radio       "ChoiceInput"
                              :multiselect "ChoiceInput"
                              nil))
        params (cond-> []
                 label       (conj (str "question = "    (interpolate-text label false)))
                 description (conj (str "description = " (interpolate-text description false)))
                 placeholder (conj (str "placeholder = " placeholder))
                 value       (conj (str "value = "       (format-value value)))
                 regex       (conj (str "pattern = "     regex))
                 min         (conj (str "min = "         min))
                 max         (conj (str "max = "         max))
                 step        (conj (str "step = "        step))
                 options     (conj (str "choices = "     (str/join ", " (map core/normalize-str options))))
                 (= (keyword type) :multiselect) (conj "multiple = true"))]
    (if constructor
      (str id-str req-str " = " constructor "(\n"
           (str/join "\n" (map #(str "  | " %) params))
           "\n)")
      "")))

;; Hidden field → reactive block with {% set %}
(defn compile-hidden-field [field]
  (let [id-str     (name (:id field))
        value-expr (:value field)
        deps       (extract-vars value-expr)
        val-js     (compile-expr value-expr)]
    (reactive-block deps (str "{% set " id-str " = " val-js " %}"))))

;; Vector label (conditional text) → reactive if/elseif/else block
(defn compile-vector-label [label-vec]
  (let [deps    (apply set/union (map #(extract-vars (:show-if %)) label-vec))
        clauses (map-indexed
                  (fn [idx item]
                    (let [cond-expr    (:show-if item)
                          text-content (interpolate-text (:text item) true)]
                      (if (or (= cond-expr [:default]) (= cond-expr :default))
                        (str "{% else %}\n" text-content)
                        (str (if (zero? idx) "{% if " "{% elseif ")
                             (compile-expr cond-expr) " %}\n"
                             text-content))))
                  label-vec)]
    (reactive-block deps
      (str (str/join "\n" clauses) "\n{% endif %}"))))

;; Wrap a compiled field in a show-if reactive block (field-level conditional)
(defn wrap-conditional [field-content show-if-expr]
  (if show-if-expr
    (let [deps   (extract-vars show-if-expr)
          cond-js (compile-expr show-if-expr)]
      (reactive-block deps
        (str "{% if " cond-js " %}\n"
             field-content "\n"
             "{% endif %}")))
    field-content))

;; Actions (e.g. :set, :print) → reactive block triggered by field value
(defn compile-action [action]
  (let [[op & args] action]
    (case op
      :set   (let [var-name (name (first args))
                   val-js   (compile-expr (second args))]
               (str "{% set " var-name " = " val-js " %}"))
      :print (str "<p class=\"text-muted\">📢 " (interpolate-text (first args) true) "</p>")
      "")))

(defn compile-actions [actions trigger-vars]
  (when (seq actions)
    (reactive-block trigger-vars
      (str/join "\n" (map compile-action actions)))))

;; Combine field + actions block
(defn compile-field-with-actions [field]
  (let [field-content (if (vector? (:label field))
                        (compile-vector-label (:label field))
                        (case (keyword (:type field))
                          :info   (interpolate-text (:label field) false)
                          :hidden (compile-hidden-field field)
                          (compile-field field)))
        wrapped       (wrap-conditional field-content (:show-if field))
        actions-block (when (and (:actions field) (not= (keyword (:type field)) :hidden))
                        (compile-actions (:actions field) #{(name (:id field))}))]
    (str/join "\n"
      (remove nil?
        [wrapped actions-block]))))

;; ─────────────────────────────────────────────
;; Stage compiler
;;
;; Stage-level :show-if → formsmd "jump condition" syntax:
;;   --- \n -> conditionExpr \n <content>
;;
;; This is the CORRECT way to skip an entire slide in formsmd.
;; Do NOT wrap slide content in {% if %} reactive blocks.
;; ─────────────────────────────────────────────

(defn compile-stage [stage idx total-stages]
  (let [on-begin-str     (when (:on-begin stage)
                           (compile-actions (:on-begin stage) #{}))
        fields-str       (str/join "\n\n" (remove str/blank?
                                            (map compile-field-with-actions (:fields stage))))
        on-complete-str  (when (:on-complete stage)
                           (compile-actions (:on-complete stage) #{}))
        ;; Jump condition prefix (placed right after ---)
        jump-line        (when (:show-if stage)
                           (str "-> " (compile-expr (:show-if stage)) "\n"))
        stage-content    (str/join "\n"
                           (remove nil?
                             [jump-line on-begin-str fields-str on-complete-str]))
        slide-break      (when (< idx (dec total-stages))
                           "\n---\n")]
    (str stage-content slide-break)))

;; ─────────────────────────────────────────────
;; End slide
;; ─────────────────────────────────────────────

(defn compile-end-slide [form]
  (let [vars       (keys (:variables form))
        has-score? (some #(= :diem_tong %) vars)
        has-status? (some #(= :trang_thai %) vars)
        lines      (cond-> ["# Cảm ơn bạn!"]
                     has-score?  (conj "Tổng điểm của bạn: **{$ diem_tong $}**")
                     has-status? (conj "Trạng thái: **{$ trang_thai $}**")
                     true        (conj "Thông tin đăng ký của bạn đã được ghi nhận."))]
    (str "-> end\n" (str/join "\n" lines) "\n")))

;; ─────────────────────────────────────────────
;; Helpers
;; ─────────────────────────────────────────────

(defn- get-form-name [form-file]
  (if form-file
    (str/replace (.getName (io/file form-file)) #"\.(edn|json)$" "")
    "form"))

;; ─────────────────────────────────────────────
;; HTML wrapper generator
;; ─────────────────────────────────────────────

(defn generate-html [form-name md-content]
  ;; Escape backticks so the template string doesn't break JS template literals.
  ;; Also escape ${...} sequences to avoid JS template literal interpolation.
  (let [escaped (-> md-content
                    (str/replace #"`" "\\`")
                    (str/replace #"\$\{" "\\${"))]
    (str "<!DOCTYPE html>
<html lang=\"vi\">
<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>" form-name " - Forms.md</title>
    <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">
    <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>
    <link href=\"https://fonts.googleapis.com/css2?family=Inter:ital,opsz,wght@0,14..32,300..700;1,14..32,300..700&display=swap\" rel=\"stylesheet\">
    <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/formsmd/dist/css/formsmd.min.css\">
    <style>
        body {
            font-family: 'Inter', sans-serif;
            background: linear-gradient(135deg, #f0f4ff 0%, #faf5ff 100%);
            margin: 0;
            padding: 2rem 1rem;
            min-height: 100vh;
            display: flex;
            justify-content: center;
            align-items: flex-start;
        }
        #form-container {
            width: 100%;
            max-width: 680px;
            background: #ffffff;
            padding: 3rem 3.5rem;
            border-radius: 20px;
            box-shadow: 0 20px 60px -10px rgba(0,0,0,0.12), 0 8px 16px -8px rgba(0,0,0,0.06);
            border: 1px solid rgba(226, 232, 240, 0.8);
        }
        @media (max-width: 640px) {
            body { padding: 0; align-items: stretch; }
            #form-container { border-radius: 0; padding: 2rem 1.5rem; box-shadow: none; }
        }
    </style>
</head>
<body>
    <div id=\"form-container\"></div>

    <!-- Custom JS functions (defined by :import in bb-form) -->
    <!-- Add any external window.namespace.functionName definitions here -->
    <script>
        // Placeholder: bb-form detected external :call references.
        // Define your custom functions here if needed:
        // window.my_namespace = { my_function: function(a, b) { ... } };
    </script>

    <script src=\"https://cdn.jsdelivr.net/npm/formsmd/dist/js/formsmd.bundle.min.js\"></script>
    <script>
        const template = `\n" escaped "`;
        const formsmd = new Formsmd(template, document.getElementById(\"form-container\"), {
            colorScheme: \"light\"
        });
        formsmd.init();
    </script>
</body>
</html>")))

;; ─────────────────────────────────────────────
;; Browser opener & local server
;; ─────────────────────────────────────────────

(defn- open-browser [url]
  (try
    (let [os (str/lower-case (System/getProperty "os.name"))]
      (cond
        (str/includes? os "mac") (shell/sh "open" url)
        (str/includes? os "win") (shell/sh "cmd" "/c" "start" url)
        :else                    (shell/sh "xdg-open" url)))
    (catch Exception e
      (println "⚠️  Không thể mở trình duyệt tự động:" (.getMessage e)))))

(defn- mime-type [path]
  (cond
    (str/ends-with? path ".html") "text/html; charset=utf-8"
    (str/ends-with? path ".css")  "text/css"
    (str/ends-with? path ".js")   "application/javascript"
    :else                          "text/plain"))

(defn- serve-file [dir path]
  (let [file (io/file dir (subs path 1))]
    (if (and (.exists file) (.isFile file))
      {:status  200
       :headers {"Content-Type"   (mime-type path)
                 "Content-Length" (str (.length file))}
       ;; Read as bytes to avoid httpkit truncation with CRLF content
       :body    (java.io.FileInputStream. file)}
      {:status  404
       :headers {"Content-Type" "text/plain"}
       :body    "404 Not Found"})))

(defn- start-server [port dir]
  (httpkit/run-server
    (fn [req]
      (let [uri  (:uri req)
            path (if (= uri "/") "/index.html" uri)]
        (serve-file dir path)))
    {:port port}))

;; ─────────────────────────────────────────────
;; Main entry point
;; ─────────────────────────────────────────────

(defn run [form answers-atom options]
  (reset! warned-functions #{})
  (let [form-file  (:form-file options)
        form-name  (get-form-name form-file)
        exports-dir (io/file "exports")
        _           (.mkdirs exports-dir)
        out-md   (io/file exports-dir (str form-name ".md"))
        out-html (io/file exports-dir (str form-name ".html"))

        ;; ── Compile each section ──────────────────────
        settings-str    (generate-settings form form-name)

        ;; Start slide: -> start -> ButtonText  (ButtonText defaults to "Bắt đầu")
        start-slide-str (when (or (:title form) (:description form))
                          (str "-> start -> Bắt đầu\n"
                               (when (:title form)       (str "# " (:title form) "\n"))
                               (when (:description form) (str (:description form) "\n"))
                               "---\n\n"))

        ;; Global variables block
        vars-str  (compile-variables (:variables form))

        ;; Stages (or flat :fields wrapped in a single stage)
        stages    (or (:stages form) [{:fields (:fields form)}])
        stages-str (str/join "\n" (map-indexed
                                    (fn [idx s] (compile-stage s idx (count stages)))
                                    stages))

        ;; End slide
        end-slide-str (str "\n---\n" (compile-end-slide form))

        ;; Assemble final Markdown template
        final-md  (str settings-str
                        (or start-slide-str "")
                        vars-str
                        stages-str
                        end-slide-str)

        ;; Generate HTML wrapper
        final-html (generate-html form-name final-md)]

    (spit out-md   final-md)
    (spit out-html final-html)

    (println "🌐  [Forms.md Engine]")
    (println "✅  Kết xuất form:" (:title form))
    (println "💾  Markdown → " (.getAbsolutePath out-md))
    (println "🖥️   HTML    → " (.getAbsolutePath out-html))

    (when (seq @warned-functions)
      (println "\n⚠️  Các hàm gọi ngoài chưa được nhúng tự động vào HTML:")
      (doseq [f @warned-functions]
        (println "    •" f))
      (println "   → Khai báo chúng trên đối tượng window trong phần <script> của file HTML kết xuất."))

    (when (:serve options)
      (let [port        8080
            stop-server (start-server port exports-dir)
            url         (str "http://localhost:" port "/" form-name ".html")]
        (println "\n🚀  Server: " url)
        (println "👉  Nhấn ENTER để dừng...")
        (open-browser url)
        (read-line)
        (stop-server)
        (println "Server đã dừng.")))))
