(ns com.drbinhthanh.bb-form
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [com.drbinhthanh.bb-form.core :as core]
            [com.drbinhthanh.bb-form.engines.gum :as gum]
            [com.drbinhthanh.bb-form.engines.tui :as tui]
            [com.drbinhthanh.bb-form.engines.winform :as winform]
            [com.drbinhthanh.bb-form.engines.formsmd :as formsmd]))

;; Form loader - supports EDN and JSON
(defn load-form [file-path]
  (let [content (slurp (io/file file-path))]
    (cond
      (str/ends-with? file-path ".edn") (edn/read-string content)
      (str/ends-with? file-path ".json") (json/parse-string content true)
      :else (throw (ex-info (str "Định dạng file không được hỗ trợ: " file-path
                                 "\nHỗ trợ: .edn (khuyến nghị), .json (tương thích ngược)")
                            {:file file-path})))))

;; Detect format from file extension or explicit format override
(defn detect-format [output-path force-format]
  (cond
    (= force-format "edn")               :edn
    (= force-format "json")              :json
    (str/ends-with? output-path ".json") :json
    :else                                :edn))

;; Write answers to the output file
(defn write-output! [data output-path format]
  (case format
    :edn  (spit output-path (with-out-str (pprint/pprint data)))
    :json (spit output-path (json/generate-string data {:pretty true}))))

;; Parse key:value pairs from command line arguments
(defn parse-kv-args [args]
  (->>  args
        (filter #(str/includes? % ":"))
        (map #(str/split % #":" 2))
        (map (fn [[k v]] [(keyword k) v]))
        (into {})))

;; Parse command line options
(defn parse-options [args]
  (loop [args args
         opts {}]
    (if (empty? args)
      opts
      (let [[k & rest] args]
        (cond
          ;; Parse option --values
          (= k "--values")
          (if (empty? rest)
            (do (println "❌ Thiếu file values sau --values") (System/exit 1))
            (recur (drop 1 rest)
                   (assoc opts :values-file (first rest))))

          ;; Parse option --out
          (= k "--out")
          (if (empty? rest)
            (do (println "❌ Thiếu file output sau --out") (System/exit 1))
            (recur (drop 1 rest)
                   (assoc opts :output-file (first rest))))

          ;; Parse option --format (json | edn)
          (= k "--format")
          (if (empty? rest)
            (do (println "❌ Thiếu giá trị sau --format (json hoặc edn)") (System/exit 1))
            (let [fmt (str/lower-case (first rest))]
              (when-not (#{"json" "edn"} fmt)
                (println (str "❌ --format chỉ nhận \"json\" hoặc \"edn\", nhận được: " fmt))
                (System/exit 1))
              (recur (drop 1 rest)
                     (assoc opts :format fmt))))

          ;; Parse option --engine (gum | tui | winform | formsmd)
          (= k "--engine")
          (if (empty? rest)
            (do (println "❌ Thiếu giá trị sau --engine (gum, tui, winform, hoặc formsmd)") (System/exit 1))
            (let [eng (str/lower-case (first rest))]
              (when-not (#{"gum" "tui" "winform" "formsmd"} eng)
                (println (str "❌ --engine chỉ nhận \"gum\", \"tui\", \"winform\" hoặc \"formsmd\", nhận được: " eng))
                (System/exit 1))
              (recur (drop 1 rest)
                     (assoc opts :engine (keyword eng)))))

          ;; Parse option --serve
          (= k "--serve")
          (recur rest
                 (assoc opts :serve true))

          ;; Parse option --theme <path.edn>
          (= k "--theme")
          (if (empty? rest)
            (do (println "❌ Thiếu file theme sau --theme") (System/exit 1))
            (recur (drop 1 rest)
                   (assoc opts :theme (first rest))))

          ;; Support unsupported flags
          (str/starts-with? k "--")
          (do (println (str "❌ Option không được hỗ trợ: " k)) (System/exit 1))

          :else
          (recur rest
                 (update opts :kv-args (fnil conj []) k)))))))

;; CLI entry point
(defn -main [& args]
  (let [form-file   (first (filter #(and (not (str/starts-with? % "--"))
                                         (not (str/includes? % ":"))
                                         (or (str/ends-with? % ".edn")
                                             (str/ends-with? % ".json"))) args))
        {:keys [values-file kv-args output-file format engine theme serve]} (parse-options args)
        kv-values   (parse-kv-args kv-args)
        edn-values  (if values-file
                      (edn/read-string (slurp values-file))
                      {})
        prefilled   (merge edn-values kv-values)
        output-path (or output-file "result.edn")
        out-format  (detect-format output-path format)
        chosen-engine (or engine :gum)]

    (if-not form-file
      (do (println "❌ Vui lòng nhập đường dẫn tới file form")
          (println (str "Cách sử dụng: bb-form <form.edn|form.json>"
                        " [--values <values.edn>]"
                        " [--out <output.edn|output.json>]"
                        " [--format json|edn]"
                        " [--engine gum|tui|winform|formsmd]"
                        " [--serve]"
                        " [--theme <theme.edn>]"))
          (System/exit 1))
      (let [form (load-form form-file)]
        (when-let [imports (:import form)]
          (let [form-dir (if (.getParent (io/file form-file))
                           (.getParent (io/file form-file))
                           ".")]
            (core/load-formulas! imports form-dir)))

        (reset! core/answers {:selectedByUser {}
                              :HiddenVar (get form :variables {})})
        (swap! core/answers update :selectedByUser merge prefilled)

        ;; Delegate form execution to the chosen engine
        (case chosen-engine
          :gum (gum/run form core/answers {:theme theme})
          :tui (tui/run form core/answers {:theme theme})
          :winform (winform/run form core/answers {:theme theme})
          :formsmd (formsmd/run form core/answers {:theme theme :form-file form-file :output-file output-path :serve serve})
          (do
            (println "❌ Engine không hợp lệ:" chosen-engine)
            (System/exit 1)))

        (when-not (= chosen-engine :formsmd)
          (let [out-file   (io/file output-path)
                parent-dir (.getParentFile out-file)]
            (when parent-dir (.mkdirs parent-dir)))
          (write-output! @core/answers output-path out-format)
          (println (str "\n💾 Đã lưu kết quả " (name out-format) " vào " output-path)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))