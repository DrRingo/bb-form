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

          (= k "--out")
          (recur (drop 1 rest)
                 (assoc opts :output-file (first rest)))

          :else
          (recur rest
                 (update opts :kv-args (fnil conj []) k)))))))

(defn -main [& args]
  (let [[form-file & args] args
        {:keys [values-file kv-args output-file]} (parse-options args)
        kv-values (parse-kv-args kv-args)
        json-values (if values-file
                      (json/parse-string (slurp values-file) true)
                      {})
        prefilled (merge json-values kv-values)
        output-path (or output-file "result.json")]
    (if-not form-file
      (do (println "❌ Vui lòng nhập đường dẫn tới form.json") (System/exit 1))
      (let [form (json/parse-string (slurp (io/file form-file)) true)]
        ;; Khởi tạo atom với :selectedByUser là map rỗng, sau đó merge prefilled
        (reset! form.core/answers {:selectedByUser {}})
        (swap! form.core/answers update :selectedByUser merge prefilled)
        (form.core/run-form form)
        ;; Tạo thư mục nếu chưa tồn tại (chỉ khi có path)
        (let [output-file (io/file output-path)
              parent-dir (.getParentFile output-file)]
          (when parent-dir
            (.mkdirs parent-dir)))
        (spit output-path (json/generate-string @form.core/answers {:pretty true}))
        (println (str "\n💾 Đã lưu kết quả vào " output-path))))))

(apply -main *command-line-args*)

