#!/usr/bin/env python3
# NOTE: Chạy qua venv: scripts/.venv/bin/python3 scripts/run_hiring_model.py '...'
# Clojure gọi script này qua clojure.java.shell/sh với đường dẫn venv python ở trên.
"""
Bridge script: Clojure gọi script này qua subprocess.
Nhận tham số dạng JSON từ sys.argv[1], chạy Stan model thực sự, trả kết quả JSON ra stdout.

Cách dùng thủ công:
  scripts/.venv/bin/python3 scripts/run_hiring_model.py '{"experience": 5, "test_score": 8}'

Output (stdout, JSON):
  {"prob": 98, "variance": 0.0013, "lower_bound": 88, "upper_bound": 100,
   "confidence": "95%", "method": "MCMC-Stan (cmdstanpy)", "chains": 4, "samples": 4000, "rhat": 1.0016}
"""

import sys
import json
import os
import logging

# ─────────────────────────────────────────────────────────────────────────────
# BƯỚC 1 — Đọc và parse tham số đầu vào
# ─────────────────────────────────────────────────────────────────────────────
# Clojure truyền JSON qua sys.argv[1], ví dụ: '{"experience": 5, "test_score": 8}'
# Script này là subprocess, không dùng stdin, mà đọc từ command-line argument.
try:
    input_data     = json.loads(sys.argv[1])
    new_experience = float(input_data["experience"])   # số năm kinh nghiệm
    new_test_score = float(input_data["test_score"])   # điểm test kỹ năng (1-10)
except Exception as e:
    print(json.dumps({"error": f"Tham số đầu vào không hợp lệ: {e}"}), flush=True)
    sys.exit(1)

# ─────────────────────────────────────────────────────────────────────────────
# BƯỚC 2 — Import cmdstanpy (thư viện Python giao tiếp với CmdStan)
# ─────────────────────────────────────────────────────────────────────────────
# cmdstanpy là Python wrapper cho CmdStan (bộ công cụ C++ chạy Stan MCMC).
# Nếu chưa cài: bash scripts/setup_stan.sh
try:
    import cmdstanpy
except ImportError:
    print(json.dumps({
        "error": "cmdstanpy chưa được cài. Chạy: bash scripts/setup_stan.sh"
    }), flush=True)
    sys.exit(1)

# Tắt log verbose của cmdstanpy (chỉ giữ lại ERROR để tránh nhiễu stdout)
cmdstanpy.utils.get_logger().setLevel(logging.ERROR)

# ─────────────────────────────────────────────────────────────────────────────
# BƯỚC 3 — Tìm đường dẫn tới file .stan
# ─────────────────────────────────────────────────────────────────────────────
# File .stan là mô hình thống kê viết bằng ngôn ngữ Stan (probabilistic programming).
# Vị trí: formulas/stan_models/hiring_model.stan (tương đối với project root)
script_dir   = os.path.dirname(os.path.abspath(__file__))  # thư mục scripts/
project_root = os.path.dirname(script_dir)                  # thư mục gốc dự án
stan_file    = os.path.join(project_root, "formulas", "stan_models", "hiring_model.stan")

if not os.path.exists(stan_file):
    print(json.dumps({"error": f"Không tìm thấy Stan model tại: {stan_file}"}), flush=True)
    sys.exit(1)

# ─────────────────────────────────────────────────────────────────────────────
# BƯỚC 4 — Chuẩn bị dữ liệu đầu vào cho Stan (data block)
# ─────────────────────────────────────────────────────────────────────────────
# Dict này ánh xạ 1-1 với phần `data { }` trong hiring_model.stan:
#   int N                      ← số lượng ứng viên lịch sử
#   vector[N] experience       ← số năm kinh nghiệm của từng người
#   vector[N] test_score       ← điểm test kỹ năng của từng người
#   array[N] int hired         ← kết quả tuyển dụng (0 = từ chối, 1 = tuyển)
#   real new_experience        ← kinh nghiệm của ứng viên cần dự đoán
#   real new_test_score        ← điểm test của ứng viên cần dự đoán
#
# 20 dòng dữ liệu bên dưới là lịch sử tuyển dụng giả lập (training set).
# Trong thực tế, dữ liệu này nên đọc từ database hoặc file CSV.
training_data = {
    "N":              20,
    "experience":     [1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8,  9,  9, 10, 10, 1],
    "test_score":     [5, 4, 7, 5, 8, 6, 9, 7, 9, 7, 9, 8, 9, 8, 10, 9, 10,  9, 10, 3],
    "hired":          [0, 0, 1, 0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1,  1, 1,  1,  1,  1, 0],
    "new_experience": new_experience,   # ứng viên mới — từ tham số đầu vào
    "new_test_score": new_test_score,   # ứng viên mới — từ tham số đầu vào
}

# ─────────────────────────────────────────────────────────────────────────────
# BƯỚC 5a — COMPILE mô hình Stan thành binary C++
# ─────────────────────────────────────────────────────────────────────────────
# ▶▶▶ ĐÂY LÀ BƯỚC COMPILE MÔ HÌNH ◀◀◀
#
# CmdStanModel() đọc file hiring_model.stan và dùng CmdStan (C++ toolchain)
# để compile nó thành một binary thực thi (file nhị phân, tương tự .exe).
#
# Quá trình:
#   hiring_model.stan
#     → Stan compiler (stanc3) → C++ source code
#     → g++/clang++ compiler   → binary executable
#
# File binary được cache lại tại cùng thư mục với .stan (hiring_model).
# Nếu .stan không thay đổi, lần sau sẽ dùng binary đã build sẵn (nhanh hơn).
try:
    model = cmdstanpy.CmdStanModel(stan_file=stan_file)
    # Sau dòng này: model là object đại diện cho binary đã compile.
    # model.exe_file chứa đường dẫn tới file binary.

    # ─────────────────────────────────────────────────────────────────────────
    # BƯỚC 5b — CHẠY MCMC SAMPLING (đây là lúc mô hình thực sự chạy)
    # ─────────────────────────────────────────────────────────────────────────
    # ▶▶▶ ĐÂY LÀ BƯỚC CHẠY MÔ HÌNH BAYESIAN THỰC SỰ ◀◀◀
    #
    # model.sample() gọi binary vừa compile ở trên và chạy thuật toán MCMC:
    #   - Thuật toán: HMC-NUTS (Hamiltonian Monte Carlo - No-U-Turn Sampler)
    #   - chains=4      → chạy 4 chuỗi Markov song song (mỗi chuỗi độc lập)
    #   - iter_warmup=500  → 500 bước "warmup" (burn-in): NUTS tự điều chỉnh step size,
    #                        các sample này bị loại bỏ, không dùng để suy luận
    #   - iter_sampling=1000 → 1000 bước lấy mẫu thực sự mỗi chain
    #   → tổng posterior samples = 4 chains × 1000 = 4000 samples
    #
    # Trong mỗi bước, Stan:
    #   1. Tính gradient của log-posterior ∇ log p(θ|data)
    #   2. Mô phỏng quỹ đạo Hamiltonian trong không gian tham số
    #   3. Dùng Metropolis acceptance để quyết định chấp nhận hay từ chối bước đi
    #
    # Kết quả `fit` chứa toàn bộ posterior distribution của:
    #   - alpha     (intercept của logistic regression)
    #   - beta_exp  (trọng số kinh nghiệm)
    #   - beta_score (trọng số điểm test)
    #   - prob_hire  (xác suất tuyển ứng viên mới — generated quantity)
    fit = model.sample(
        data=training_data,
        chains=4,
        iter_warmup=500,
        iter_sampling=1000,
        show_progress=False,   # tắt progress bar (dùng trong subprocess)
        show_console=False,    # tắt log của CmdStan binary
    )
    # Sau dòng này: fit là CmdStanMCMC object chứa toàn bộ 4000 posterior samples.

    # ─────────────────────────────────────────────────────────────────────────
    # BƯỚC 6 — Trích xuất posterior predictive của prob_hire
    # ─────────────────────────────────────────────────────────────────────────
    # prob_hire được định nghĩa trong block `generated quantities` của .stan:
    #   real prob_hire = inv_logit(alpha + beta_exp * new_experience + beta_score * new_test_score);
    #
    # fit.stan_variable("prob_hire") trả về numpy array shape (4000,) —
    # 4000 giá trị xác suất được lấy mẫu từ phân phối posterior.
    # Đây không phải một con số duy nhất, mà là một phân phối đầy đủ.
    prob_samples = fit.stan_variable("prob_hire")  # numpy array, shape: (4000,)

    # Tóm tắt thống kê của phân phối posterior prob_hire:
    mean_prob = float(prob_samples.mean())   # posterior mean (ước lượng trung tâm)
    std_prob  = float(prob_samples.std())    # độ lệch chuẩn posterior

    # Khoảng tin cậy Bayesian 95% (Credible Interval) — lấy percentile 2.5% và 97.5%
    sorted_samples = sorted(prob_samples)
    lower_95 = float(sorted_samples[int(len(prob_samples) * 0.025)])  # percentile 2.5%
    upper_95 = float(sorted_samples[int(len(prob_samples) * 0.975)])  # percentile 97.5%

    # ─────────────────────────────────────────────────────────────────────────
    # BƯỚC 7 — Đóng gói kết quả thành JSON và in ra stdout
    # ─────────────────────────────────────────────────────────────────────────
    # Clojure đọc stdout của subprocess này và parse JSON để lấy kết quả.
    result = {
        "prob":        round(mean_prob * 100),       # xác suất tuyển (%) — posterior mean
        "variance":    round(std_prob ** 2, 4),      # phương sai posterior
        "lower_bound": round(lower_95 * 100),        # cận dưới 95% CI (%)
        "upper_bound": round(upper_95 * 100),        # cận trên 95% CI (%)
        "confidence":  "95%",
        "method":      "MCMC-Stan (cmdstanpy)",
        "chains":      4,
        "samples":     len(prob_samples),            # tổng số posterior samples (4000)
        # R-hat: chẩn đoán hội tụ MCMC. R-hat ≈ 1.0 là tốt. R-hat > 1.01 = chưa hội tụ.
        "rhat":        round(float(fit.summary()["R_hat"].get("prob_hire", 1.0)), 4),
    }

    # In JSON ra stdout — Clojure subprocess sẽ đọc dòng này
    print(json.dumps(result), flush=True)

except Exception as e:
    # Nếu compile hoặc MCMC thất bại, trả lỗi dạng JSON để Clojure xử lý
    print(json.dumps({"error": f"Stan model thất bại: {str(e)}"}), flush=True)
    sys.exit(1)
