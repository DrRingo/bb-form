(ns formulas.sanh-utils
  (:require [clojure.string :as str]))

(defn tinh-gio-vo-oi
  "Tính số giờ từ thời điểm vỡ ối đến hiện tại.
   Nhận chuỗi định dạng 'DD-MM-YYYY HH:mm'.
   Trả về số giờ nguyên (làm tròn xuống)."
  [datetime-str]
  (if (or (nil? datetime-str) (str/blank? (str datetime-str)))
    "chưa nhập"
    (try
      (let [trimmed  (str/trim (str datetime-str))
            vo-time  (try
                       (let [fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm")]
                         (java.time.LocalDateTime/parse trimmed fmt))
                       (catch Exception _
                         (let [fmt (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy HH:mm")]
                           (java.time.LocalDateTime/parse trimmed fmt))))
            now      (java.time.LocalDateTime/now)
            duration (java.time.Duration/between vo-time now)
            hours    (.toHours duration)]
        (if (neg? hours)
          "⚠️ Thời gian nhập vào ở tương lai, kiểm tra lại"
          (str hours)))
      (catch Exception _
        "⚠️ Định dạng không hợp lệ, nhập theo mẫu: DD-MM-YYYY HH:mm"))))
