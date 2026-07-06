(ns com.drbinhthanh.bb-form.engines.tui
  (:require [clojure.string :as str]
            [com.drbinhthanh.bb-form.core :as core])
  (:import (org.jline.terminal TerminalBuilder)
           (org.jline.reader LineReaderBuilder)))

(defn ansi-color [theme color text]
  (let [code (get-in theme [:colors color] "")
        reset (get-in theme [:colors :reset] "\u001B[0m")]
    (str code text reset)))

(defn clear-screen []
  (print "\033[2J")
  (print "\033[H")
  (flush))

(defn render-header [form status-msg theme]
  (let [prefix (get-in theme [:symbols :header-prefix] "")
        layout (get-in theme [:layouts :header-format] "\n%s%s\n%s\n")]
    (print (format layout
                   (ansi-color theme :cyan (ansi-color theme :bold prefix))
                   (ansi-color theme :cyan (ansi-color theme :bold (:title form)))
                   (ansi-color theme :reset (:description form))))
    (flush)
    (when-not (str/blank? status-msg)
      (let [error-prefix (get-in theme [:symbols :error-prefix] "")
            error-layout (get-in theme [:layouts :error-format] "%s%s\n")]
        (print (ansi-color theme :bold
                           (ansi-color theme :red
                                       (format error-layout error-prefix status-msg))))
        (flush)))))

(defn show-error [message theme]
  (let [prefix (get-in theme [:symbols :error-prefix] "")
        layout (get-in theme [:layouts :error-format] "%s%s\n")]
    (print (ansi-color theme :bold
                       (ansi-color theme :red
                                   (format layout prefix message))))
    (flush)))

(defn pause [msg theme]
  (let [prefix (get-in theme [:symbols :info-prefix] "")
        layout (get-in theme [:layouts :info-format] "\n%s%s")
        prompt-txt (get-in theme [:layouts :pause-prompt] "Nhấn Enter để tiếp tục...")]
    (println (ansi-color theme :yellow (format layout prefix msg)))
    (let [terminal (.. (TerminalBuilder/builder) (system true) (build))
          reader (-> (LineReaderBuilder/builder)
                     (.terminal terminal)
                     .build)]
      (try
        (.readLine reader prompt-txt)
        (finally
          (.close terminal))))))

(defn tui-input [label default-val theme]
  (let [prefix (get-in theme [:symbols :prompt-prefix] "")
        layout (get-in theme [:layouts :prompt-format] "%s%s%s: ")
        default-txt (if default-val (str " [" default-val "]") "")
        prompt (format layout prefix label default-txt)
        terminal (.. (TerminalBuilder/builder) (system true) (build))
        reader (-> (LineReaderBuilder/builder)
                   (.terminal terminal)
                   .build)]
    (try
      (let [res (.readLine reader (ansi-color theme :bold prompt))]
        (if (str/blank? res)
          (or default-val "")
          res))
      (finally
        (.close terminal)))))

;; Menu rendering and navigation helper
(defn clear-menu-lines [n-lines]
  (dotimes [_ n-lines]
    (print "\u001B[1A\u001B[2K"))
  (flush))

(defn read-key [reader]
  (let [c (.read reader)]
    (cond
      (= c 27)
      (let [c2 (.read reader)]
        (if (= c2 91)
          (let [c3 (.read reader)]
            (case c3
              65 :up
              66 :down
              67 :right
              68 :left
              :esc))
          :esc))
      (or (= c 10) (= c 13)) :enter
      (= c 32) :space
      :else c)))

(defn render-options [options selected-index choices theme]
  (let [cursor (get-in theme [:symbols :select-cursor] "➔ ")
        empty-cursor (get-in theme [:symbols :select-empty] "  ")
        checked (get-in theme [:symbols :multiselect-checked] "[x] ")
        unchecked (get-in theme [:symbols :multiselect-unchecked] "[ ] ")]
    (doseq [idx (range (count options))]
      (let [opt (nth options idx)
            is-current (= idx selected-index)
            is-chosen (contains? choices opt)
            prefix (if is-current cursor empty-cursor)
            checkbox (if choices (if is-chosen checked unchecked) "")
            style-fn (if is-current #(ansi-color theme :cyan %) identity)]
        (println (style-fn (str prefix checkbox opt)))))
    (flush)))

(defn tui-menu [label options multiselect? theme]
  (let [terminal (.. (TerminalBuilder/builder) (system true) (build))
        prompt-prefix (get-in theme [:symbols :prompt-prefix] "")
        completed-prefix (get-in theme [:symbols :completed-prefix] "")
        completed-layout (get-in theme [:layouts :completed-format] "%s%s ➔ %s")]
    (try
      (.enterRawMode terminal)
      (let [reader (.reader terminal)]
        (println (ansi-color theme :bold (str prompt-prefix label)))
        (loop [selected-index 0
               choices #{}]
          (render-options options selected-index (when multiselect? choices) theme)
          (let [k (read-key reader)]
            (case k
              :up (do
                    (clear-menu-lines (count options))
                    (recur (mod (dec selected-index) (count options)) choices))
              :down (do
                      (clear-menu-lines (count options))
                      (recur (mod (inc selected-index) (count options)) choices))
              :space (if multiselect?
                       (let [opt (nth options selected-index)
                             new-choices (if (contains? choices opt)
                                           (disj choices opt)
                                           (conj choices opt))]
                         (clear-menu-lines (count options))
                         (recur selected-index new-choices))
                       (do
                         (clear-menu-lines (count options))
                         (recur selected-index choices)))
              :enter (let [res (if multiselect?
                                 (vec choices)
                                 (nth options selected-index))]
                       (clear-menu-lines (inc (count options)))
                       (println (ansi-color theme :green (format completed-layout completed-prefix label (if multiselect? (str/join ", " res) res))))
                       res)
              (if (or (= k 3) (= k 113))
                (do
                  (.close terminal)
                  (System/exit 0))
                (do
                  (clear-menu-lines (count options))
                  (recur selected-index choices)))))))
      (finally
        (.close terminal)))))

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
        (pause "Nhấn Enter để tiếp tục..." theme)
        (swap! answers-atom assoc-in [:selectedByUser id-k] resolved-label)))))

(defmethod ask-field-by-type :text [{:keys [id label required regex regexError]} form answers-atom theme]
  (let [id-k    (keyword id)
        pattern (core/->pattern regex)
        resolved-label (core/resolve-label label @answers-atom)
        value   (if (core/should-skip? id)
                  (core/get-prefilled id)
                  (loop []
                    (let [v (tui-input resolved-label nil theme)]
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
                  (let [v (tui-input resolved-label nil theme)]
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
                  (let [v (tui-input (str resolved-label " (DD-MM-YYYY hoặc gõ tắt: 04, 1204, 23+10, 2304-1)") (core/today) theme)]
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
                  (let [v (tui-input (str resolved-label " (DD-MM-YYYY HH:MM hoặc gõ tắt: h0823, h0823-1, 23+10 h0823)") default-val theme)]
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
                (tui-menu resolved-label opts false theme))]
    (swap! answers-atom assoc-in [:selectedByUser id-k] value)))

(defmethod ask-field-by-type :radio [field form answers-atom theme]
  (ask-field-by-type (assoc field :type :select) form answers-atom theme))

(defmethod ask-field-by-type :multiselect [{:keys [id label options]} form answers-atom theme]
  (let [id-k    (keyword id)
        resolved-label (core/resolve-label label @answers-atom)
        opts    (mapv core/normalize-str options)
        raw     (if (core/should-skip? id)
                  (core/get-prefilled id)
                  (tui-menu resolved-label opts true theme))
        choices (cond
                  (string? raw) [raw]
                  (sequential? raw) raw
                  :else [])]
    (swap! answers-atom assoc-in [:selectedByUser id-k] choices)))

(defn ask-field [field form answers-atom theme]
  (ask-field-by-type field form answers-atom theme))

(defn tui-adapter [theme]
  {:clear-screen clear-screen
   :render-header #(render-header %1 %2 theme)
   :show-error #(show-error % theme)
   :ask-field #(ask-field %1 %2 %3 theme)
   :pause #(pause % theme)})

(defn run [form answers-atom options]
  (let [default-theme-var (requiring-resolve 'com.drbinhthanh.bb-form.themes.tui-theme/default-theme)
        default-theme @default-theme-var
        theme (core/load-theme default-theme (:theme options))]
    (core/run-terminal-form form answers-atom (tui-adapter theme))))
