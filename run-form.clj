#!/usr/bin/env bb

(load-file "./core.clj")

(require '[cheshire.core :as json]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(defn parse-kv-args [args]
  (->> args
       (filter #(str/includes? % ":"))
       (map #(str/split % #":" 2))
       (map (fn [[k v]] [(keyword k) v]))
       (into {})))

(defn parse-options [args]
  (loop [args args
         opts {}]
    (if (empty? args)
      opts
      (let [[k & rest] args]
        (cond
          (= k "--values")
          (recur (drop 1 rest)
                 (assoc opts :values-file (first rest)))

          :else
          (recur rest
                 (update opts :kv-args (fnil conj []) k)))))))

(defn -main [& args]
  (let [[form-file & args] args
        {:keys [values-file kv-args]} (parse-options args)
        kv-values (parse-kv-args kv-args)
        json-values (if values-file
                      (json/parse-string (slurp values-file) true)
                      {})
        prefilled (into {} (filter (fn [[k v]] (map? v)) (merge json-values kv-values)))]
    (if-not form-file
      (do (println "❌ Vui lòng nhập đường dẫn tới form.json") (System/exit 1))
      (let [form (json/parse-string (slurp (io/file form-file)) true)]
        (swap! form.core/answers merge prefilled)
        (form.core/run-form form)
        (spit "result.json" (json/generate-string @form.core/answers {:pretty true}))
        (println "\n💾 Đã lưu kết quả vào result.json")))))

(apply -main *command-line-args*)

