;; File: core.clj
(ns form.core
  (:require [babashka.process :refer [shell]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def answers (atom {}))

;; -------------------------------
;; Utility functions
;; -------------------------------
(defn normalize-str [v]
  (cond
    (keyword? v) (name v)
    (symbol? v)  (name v)
    :else (str v)))

(defn normalize-branch-key [v]
  (-> v normalize-str str/trim str/lower-case))

(defn should-skip? [id]
  (contains? @answers (keyword id)))

(defn get-prefilled [id]
  (get @answers (keyword id)))

(defn parse-value [v type]
  (case type
    "number" (try (Integer/parseInt (str v)) (catch Exception _ v))
    "text"   (str v)
    "date"   (str v)
    v))

;; -------------------------------
;; GUM UI
;; -------------------------------
(defn gum-input [label]
  (-> (shell {:out :string} "gum" "input" "--placeholder" label)
      :out str/trim))

(defn gum-select [label options]
  (-> (apply shell {:out :string}
             (concat ["gum" "choose" "--header" label] options))
      :out str/trim))

(defn gum-multiselect [label options]
  (-> (apply shell {:out :string}
             (concat ["gum" "choose" "--no-limit" "--header" label] options))
      :out str/split-lines))

;; -------------------------------
;; Field handling
;; -------------------------------
(defmulti ask-field (fn [field & _] (keyword (:type field))))

(defn handle-branch [branch value path]
  (let [raw-key (str/trim (str value))
        norm-key (normalize-branch-key value)
        norm-branch (into {} (map (fn [[k v]] [(normalize-branch-key k) v]) branch))]
    (when-let [subfields (get norm-branch norm-key)]
      (doseq [sub subfields]
        (ask-field sub (conj path (keyword raw-key)))))))

(defmethod ask-field :text [{:keys [id label required branch]} path]
  (let [id-k (keyword id)
        value (if (should-skip? id)
                (get-prefilled id)
                (gum-input label))]
    (when (or (not required) (not (str/blank? (str value))))
      (swap! answers update-in path assoc id-k (parse-value value "text")))
    (handle-branch branch value (conj path id-k))))

(defmethod ask-field :number [{:keys [id label required branch]} path]
  (let [id-k (keyword id)
        value (if (should-skip? id)
                (get-prefilled id)
                (loop []
                  (let [v (gum-input label)]
                    (if (or (not required)
                            (try (Integer/parseInt v) true (catch Exception _ false)))
                      v
                      (do (println "⚠️ Vui lòng nhập số nguyên!") (recur))))))]
    (swap! answers update-in path assoc id-k (parse-value value "number"))
    (handle-branch branch value (conj path id-k))))

(defmethod ask-field :date [{:keys [id label required branch]} path]
  (let [id-k (keyword id)
        value (if (should-skip? id)
                (get-prefilled id)
                (gum-input (str label " (YYYY-MM-DD)")))]
    (when (or (not required) (not (str/blank? value)))
      (swap! answers update-in path assoc id-k (parse-value value "date")))
    (handle-branch branch value (conj path id-k))))

(defmethod ask-field :select [{:keys [id label options branch]} path]
  (let [id-k (keyword id)
        value (if (should-skip? id)
                (get-prefilled id)
                (gum-select label options))]
    (swap! answers update-in path assoc id-k value)
    (handle-branch branch value (conj path id-k))))

(defmethod ask-field :multiselect [{:keys [id label options branch]} path]
  (let [id-k (keyword id)
        raw (if (should-skip? id)
              (get-prefilled id)
              (gum-multiselect label options))
        choices (cond
                  (string? raw) [raw]
                  (sequential? raw) raw
                  :else [])]
    (swap! answers update-in path assoc id-k choices)
    (doseq [choice choices]
      (handle-branch branch choice (conj path id-k)))))

;; -------------------------------
;; Entry point
;; -------------------------------
(defn run-form [form]
  (println "\n📝" (:title form))
  (println (:description form) "\n")
  (doseq [field (:fields form)]
    (ask-field field []))
  (spit "result.json" (json/generate-string @answers {:pretty true}))
  (println "\n✅ Kết quả đã lưu vào result.json"))


