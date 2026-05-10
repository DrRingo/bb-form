(ns my.complex.formula
  (:require [clojure.string :as str]))

(defn calculate-risk [age name]
  (str "Risk for " (str/upper-case name) " is " (* age 2)))
