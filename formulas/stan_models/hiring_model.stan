data {
  int<lower=0> N;          // Số lượng ứng viên trong dữ liệu huấn luyện
  vector[N] experience;    // Số năm kinh nghiệm
  vector[N] test_score;    // Điểm bài test kỹ năng
  array[N] int<lower=0, upper=1> hired; // Kết quả được tuyển (0/1)
  
  // Dữ liệu của ứng viên mới cần dự đoán
  real new_experience;
  real new_test_score;
}

parameters {
  real alpha;          // Intercept
  real beta_exp;       // Trọng số cho kinh nghiệm
  real beta_score;     // Trọng số cho điểm bài test
}

model {
  // Priors (Niềm tin ban đầu)
  alpha ~ normal(0, 10);
  beta_exp ~ normal(0, 5);
  beta_score ~ normal(0, 5);
  
  // Likelihood (Logistic Regression)
  hired ~ bernoulli_logit(alpha + beta_exp * experience + beta_score * test_score);
}

generated quantities {
  // Posterior Predictive cho ứng viên mới
  real prob_hire = inv_logit(alpha + beta_exp * new_experience + beta_score * new_test_score);
}
