#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# setup_stan.sh — Cài đặt CmdStan + cmdstanpy vào virtualenv (chạy một lần)
# Dùng: bash scripts/setup_stan.sh
# ─────────────────────────────────────────────────────────────────────────────
set -e

VENV_DIR="scripts/.venv"

echo "📦 Bước 1: Tạo Python virtual environment tại $VENV_DIR..."
python3 -m venv "$VENV_DIR"

echo "📦 Bước 2: Cài cmdstanpy vào venv..."
"$VENV_DIR/bin/pip" install --upgrade pip --quiet
"$VENV_DIR/bin/pip" install cmdstanpy --quiet

echo "📦 Bước 3: Cài CmdStan (tự động tải bản mới nhất ~400MB, mất vài phút)..."
"$VENV_DIR/bin/python3" - <<'EOF'
import cmdstanpy, logging
cmdstanpy.utils.get_logger().setLevel(logging.INFO)
cmdstanpy.install_cmdstan()
print("✅ CmdStan đã được cài đặt tại:", cmdstanpy.cmdstan_path())
EOF

echo ""
echo "✅ Hoàn tất! Kiểm tra bằng:"
echo "   scripts/.venv/bin/python3 scripts/run_hiring_model.py '{\"experience\": 5, \"test_score\": 8}'"
echo ""
echo "Trong Clojure, chỉ cần gọi:"
echo "   (formulas.bayesian-hiring/run-stan-model 5 8)"
