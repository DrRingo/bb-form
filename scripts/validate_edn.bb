(require '[clojure.edn :as edn])

(defn validate [path]
  (try
    (let [f (edn/read-string (slurp path))]
      (println (str "✅ " path " — OK"))
      (println "   keys:" (keys f))
      (println "   fields:" (count (:fields f))))
    (catch Exception e
      (println (str "❌ " path " — " (ex-message e))))))

(validate "form_sample.edn")
(validate "forms/radio/form.edn")

;; Kiểm tra load source file
(try
  (load-file "src/com/drbinhthanh/bb_form.clj")
  (println "✅ bb_form.clj — load OK")
  (catch Exception e
    (println "❌ bb_form.clj —" (ex-message e))))


