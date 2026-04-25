# typed: false
# frozen_string_literal: true

# =============================================================================
# Homebrew Formula: bb-form
# Tap:  brew tap drringo/bb-form https://github.com/drringo/bb-form
# Install: brew install bb-form
# =============================================================================
class BbForm < Formula
  desc "Terminal form UI tool — thu thập dữ liệu qua form đẹp trên terminal"
  homepage "https://github.com/drringo/bb-form"
  license "Apache-2.0"
  version "0.1.0"

  # ──────────────────────────────────────────────
  # macOS binaries
  # ──────────────────────────────────────────────
  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/drringo/bb-form/releases/download/v#{version}/bb-form-macos-arm64.tar.gz"
      sha256 "4cf2e02a57a701f756ff0c5a793746e9ebf2d7a252efa700e1659885423b67dd"
    else
      url "https://github.com/drringo/bb-form/releases/download/v#{version}/bb-form-macos-x86_64.tar.gz"
      sha256 "4cf2e02a57a701f756ff0c5a793746e9ebf2d7a252efa700e1659885423b67dd"
    end
  end

  # ──────────────────────────────────────────────
  # Linux binary
  # ──────────────────────────────────────────────
  on_linux do
    url "https://github.com/drringo/bb-form/releases/download/v#{version}/bb-form-linux-x86_64.tar.gz"
    sha256 "4cf2e02a57a701f756ff0c5a793746e9ebf2d7a252efa700e1659885423b67dd"
  end

  # ──────────────────────────────────────────────
  # Dependencies — Homebrew sẽ tự cài nếu chưa có
  # ──────────────────────────────────────────────
  depends_on "babashka/tap/babashka"
  depends_on "charmbracelet/tap/gum"

  # ──────────────────────────────────────────────
  # Installation
  # ──────────────────────────────────────────────
  def install
    # Đặt uberscript vào libexec (không trong PATH)
    libexec.install "bb-form.clj"

    # Tạo wrapper script trỏ đến uberscript với đường dẫn tuyệt đối
    (bin/"bb-form").write <<~EOS
      #!/usr/bin/env bash
      exec bb "#{libexec}/bb-form.clj" "$@"
    EOS
    chmod 0755, bin/"bb-form"
  end

  # ──────────────────────────────────────────────
  # Test
  # ──────────────────────────────────────────────
  test do
    # Kiểm tra wrapper script tồn tại và chạy được
    assert_predicate bin/"bb-form", :executable?

    # Tạo form JSON tối giản và chạy thử với giá trị có sẵn
    (testpath/"test-form.json").write JSON.generate({
      "title" => "Test",
      "description" => "Test form",
      "fields" => [
        { "id" => "name", "label" => "Name", "type" => "text", "required" => false }
      ]
    })

    # Chạy với giá trị điền sẵn để không cần tương tác
    output = shell_output(
      "#{bin}/bb-form test-form.json name:testuser --out test-result.json 2>&1",
      0
    )
    assert_match "testuser", (testpath/"test-result.json").read
  end
end
