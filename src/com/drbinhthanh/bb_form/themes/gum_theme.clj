(ns com.drbinhthanh.bb-form.themes.gum-theme)

(def default-theme
  {:style-args {:header ["gum" "style" "--foreground" "#00ffff" "--bold"]
                :error ["gum" "style" "--foreground" "#ff0000" "--border" "normal" "--border-foreground" "#ff0000" "--margin" "1" "--padding" "1"]
                :info ["gum" "style" "--foreground" "#ffff00"]}
   :symbols {:header-prefix "📝 "
             :error-prefix "⚠️  "
             :info-prefix "ℹ️  "
             :prompt-prefix "? "}
   :layouts {:header-format "\n%s%s\n%s\n"
             :error-format "%s%s"
             :info-format "\n%s%s"
             :pause-prompt "Nhấn Enter để tiếp tục..."}
   :input-args {:placeholder "--placeholder"}})
