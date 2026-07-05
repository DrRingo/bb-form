(ns com.drbinhthanh.bb-form.themes.tui-theme)

(def default-theme
  {:colors {:reset "\u001B[0m"
            :bold "\u001B[1m"
            :cyan "\u001B[36m"
            :yellow "\u001B[33m"
            :green "\u001B[32m"
            :red "\u001B[31m"
            :blue "\u001B[34m"
            :magenta "\u001B[35m"}
   :symbols {:header-prefix "📝 "
             :error-prefix "⚠️  "
             :info-prefix "ℹ️  "
             :prompt-prefix "? "
             :completed-prefix "✔ "
             :select-cursor "➔ "
             :select-empty "  "
             :multiselect-checked "[x] "
             :multiselect-unchecked "[ ] "}
   :layouts {:header-format "\n%s%s\n%s\n"
             :error-format "%s%s\n"
             :info-format "\n%s%s"
             :completed-format "%s%s ➔ %s"
             :prompt-format "%s%s%s: "
             :pause-prompt "Nhấn Enter để tiếp tục..."}})
