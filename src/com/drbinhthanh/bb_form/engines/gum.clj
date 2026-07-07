(ns com.drbinhthanh.bb-form.engines.gum
  (:require [babashka.process :refer [shell]]
            [clojure.string :as str]
            [com.drbinhthanh.bb-form.core :as core]))

(defn clear-screen []
  (print "\033[2J")
  (print "\033[H")
  (flush))

(defn render-header [form status-msg theme]
  (let [prefix (get-in theme [:symbols :header-prefix] "")
        layout (get-in theme [:layouts :header-format] "\n%s%s\n%s\n")
        title-styled (let [args (get-in theme [:style-args :header] ["gum" "style" "--foreground" "#00ffff" "--bold"])]
                       (-> (apply shell {:out :string} (concat args [(str prefix (:title form))]))
                           :out str/trimr))]
    (print (format layout "" title-styled (:description form)))
    (flush)
    (when-not (str/blank? status-msg)
      (let [error-prefix (get-in theme [:symbols :error-prefix] "")
            error-layout (get-in theme [:layouts :error-format] "%s%s")
            args (get-in theme [:style-args :error] ["gum" "style" "--foreground" "#ff0000" "--border" "normal" "--border-foreground" "#ff0000" "--margin" "1" "--padding" "1"])]
        (apply shell {:out :inherit} (concat args [(format error-layout error-prefix status-msg)]))
        (println)))))

(defn show-error [message theme]
  (let [prefix (get-in theme [:symbols :error-prefix] "")
        layout (get-in theme [:layouts :error-format] "%s%s")
        args (get-in theme [:style-args :error] ["gum" "style" "--foreground" "#ff0000" "--border" "normal" "--border-foreground" "#ff0000" "--margin" "1" "--padding" "1"])]
    (apply shell {:out :inherit} (concat args [(format layout prefix message)]))
    (println)))

(defn gum-input [label theme]
  (let [prefix (get-in theme [:symbols :prompt-prefix] "")
        placeholder-flag (get-in theme [:input-args :placeholder] "--placeholder")
        prompt (str prefix label)]
    (-> (shell {:out :string} "gum" "input" placeholder-flag prompt)
        :out str/trim)))

(defn gum-select [label options theme]
  (let [prefix (get-in theme [:symbols :prompt-prefix] "")
        prompt (str prefix label)]
    (-> (apply shell {:out :string}
               (concat ["gum" "choose" "--header" prompt] options))
        :out str/trim)))

(defn gum-multiselect [label options theme]
  (let [prefix (get-in theme [:symbols :prompt-prefix] "")
        prompt (str prefix label)]
    (-> (apply shell {:out :string}
               (concat ["gum" "choose" "--no-limit" "--header" prompt] options))
        :out str/split-lines)))

(defn pause [msg theme]
  (let [prefix (get-in theme [:symbols :info-prefix] "")
        layout (get-in theme [:layouts :info-format] "\n%s%s")
        prompt-txt (get-in theme [:layouts :pause-prompt] "Nhấn Enter để tiếp tục...")]
    (println (format layout prefix msg))
    (shell {:out :inherit} "gum" "input" "--placeholder" prompt-txt)))

;; Multimethod for prompting fields with Gum
(defmulti ask-field-by-type (fn [field form answers-atom theme] (keyword (:type field))))

(defmethod ask-field-by-type :hidden [_ _ _ _]
  nil)

(defmethod ask-field-by-type :info [{:keys [id label]} form answers-atom theme]
  (let [id-k (keyword id)
        resolved-label (core/resolve-label label @answers-atom)]
    (if (core/should-skip? id)
      (swap! answers-atom assoc-in [:selectedByUser id-k] resolved-label)
      (do
        (println "\n" resolved-label)
        (let [prompt-txt (get-in theme [:layouts :pause-prompt] "Nhấn Enter để tiếp tục...")]
          (shell {:out :inherit} "gum" "input" "--placeholder" prompt-txt))
        (swap! answers-atom assoc-in [:selectedByUser id-k] resolved-label)))))

(defmethod ask-field-by-type :text [{:keys [id label required regex regexError]} form answers-atom theme]
  (let [id-k    (keyword id)
        pattern (core/->pattern regex)
        resolved-label (core/resolve-label label @answers-atom)
        value   (if (core/should-skip? id)
                  (core/get-prefilled id)
                  (loop []
                    (let [v (gum-input resolved-label theme)]
                      (if (and pattern (not (re-matches pattern v)))
                        (do
                          (core/set-status! (or regexError (str "Giá trị không khớp với regex: " regex)))
                          (clear-screen)
                          (render-header form @core/status-line theme)
                          (recur))
                        (do
                          (core/clear-status!)
                          (clear-screen)
                          (render-header form @core/status-line theme)
                          v)))))]
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers-atom assoc-in [:selectedByUser id-k] (core/parse-value value "text")))))

(defmethod ask-field-by-type :number [{:keys [id label required]} form answers-atom theme]
  (let [id-k (keyword id)
        resolved-label (core/resolve-label label @answers-atom)
        value (if (core/should-skip? id)
                (core/get-prefilled id)
                (loop []
                  (let [v (gum-input resolved-label theme)]
                    (if (or (not required)
                            (try (Integer/parseInt v) true (catch Exception _ false)))
                      (do
                        (core/clear-status!)
                        (clear-screen)
                        (render-header form @core/status-line theme)
                        v)
                      (do
                        (core/set-status! "⚠️ Vui lòng nhập số nguyên!")
                        (clear-screen)
                        (render-header form @core/status-line theme)
                        (recur))))))]
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers-atom assoc-in [:selectedByUser id-k] (core/parse-value value "number")))))

(defmethod ask-field-by-type :date [{:keys [id label required]} form answers-atom theme]
  (let [id-k (keyword id)
        resolved-label (core/resolve-label label @answers-atom)
        value (if (core/should-skip? id)
                (core/get-prefilled id)
                (loop []
                  (let [v (gum-input (str resolved-label " (DD-MM-YYYY hoặc gõ tắt: 04, 1204, 23+10, 2304-1)") theme)]
                    (cond
                      (str/blank? v) (do (core/clear-status!) (clear-screen) (render-header form @core/status-line theme) (core/today))
                      :else
                      (let [expanded (core/expand-date-shortcut v)]
                        (if (not (core/valid-date? expanded))
                          (do
                            (core/set-status! "⚠️ Ngày tháng không hợp lệ. Ví dụ: 31-12-2023")
                            (clear-screen)
                            (render-header form @core/status-line theme)
                            (recur))
                          (do
                            (core/clear-status!)
                            (clear-screen)
                            (render-header form @core/status-line theme)
                            expanded)))))))]
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers-atom assoc-in [:selectedByUser id-k] (core/parse-value value "date")))))

(defmethod ask-field-by-type :datetime [{:keys [id label required]} form answers-atom theme]
  (let [id-k (keyword id)
        resolved-label (core/resolve-label label @answers-atom)
        default-val (let [now (java.time.LocalDateTime/now)]
                      (.format now (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy HH:mm")))
        value (if (core/should-skip? id)
                (core/get-prefilled id)
                (loop []
                  (let [v (gum-input (str resolved-label " (DD-MM-YYYY HH:MM hoặc gõ tắt: h0823, h0823-1, 23+10 h0823)") theme)]
                    (cond
                      (str/blank? v) (do (core/clear-status!) (clear-screen) (render-header form @core/status-line theme) default-val)
                      :else
                      (if (not (core/valid-datetime-input? v :datetime))
                        (do
                          (core/set-status! "⚠️ Nhập sai định dạng. Ví dụ: 23-04-2026 08:34 hoặc h0823")
                          (clear-screen)
                          (render-header form @core/status-line theme)
                          (recur))
                        (do
                          (core/clear-status!)
                          (clear-screen)
                          (render-header form @core/status-line theme)
                          v))))))]
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers-atom assoc-in [:selectedByUser id-k] (core/parse-value value "datetime")))))

(defmethod ask-field-by-type :select [{:keys [id label options]} form answers-atom theme]
  (let [id-k  (keyword id)
        resolved-label (core/resolve-label label @answers-atom)
        opts  (mapv core/normalize-str options)
        value (if (core/should-skip? id)
                (core/get-prefilled id)
                (gum-select resolved-label opts theme))]
    (swap! answers-atom assoc-in [:selectedByUser id-k] value)))

(defmethod ask-field-by-type :radio [field form answers-atom theme]
  (ask-field-by-type (assoc field :type :select) form answers-atom theme))

(defmethod ask-field-by-type :multiselect [{:keys [id label options]} form answers-atom theme]
  (let [id-k    (keyword id)
        resolved-label (core/resolve-label label @answers-atom)
        opts    (mapv core/normalize-str options)
        raw     (if (core/should-skip? id)
                  (core/get-prefilled id)
                  (gum-multiselect resolved-label opts theme))
        choices (cond
                  (string? raw) [raw]
                  (sequential? raw) raw
                  :else [])]
    (swap! answers-atom assoc-in [:selectedByUser id-k] choices)))

(defn ask-field [field form answers-atom theme]
  (ask-field-by-type field form answers-atom theme))

(defn gum-adapter [theme marathon?]
  {:clear-screen clear-screen
   :render-header #(render-header %1 %2 theme)
   :show-error #(show-error % theme)
   :ask-field #(ask-field %1 %2 %3 theme)
   :pause #(pause % theme)
   :marathon? marathon?})

(defn run [form answers-atom options]
  (let [default-theme-var (requiring-resolve 'com.drbinhthanh.bb-form.themes.gum-theme/default-theme)
        default-theme @default-theme-var
        theme (core/load-theme default-theme (:theme options))
        marathon? (boolean (:marathon options))]
    (core/run-terminal-form form answers-atom (gum-adapter theme marathon?))))
