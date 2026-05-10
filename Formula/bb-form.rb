class BbForm < Formula
  desc "Terminal form UI tool powered by an EDN Logic Engine (Babashka + Charm Gum)"
  homepage "https://github.com/drringo/bb-form"
  version "2.0.0"
  license "Apache-2.0"

  on_macos do
    url "https://github.com/drringo/bb-form/releases/download/v2.0.0/bb-form-macos-arm64.tar.gz"
    sha256 "PLACEHOLDER_WILL_BE_UPDATED_BY_CI"
  end

  on_linux do
    url "https://github.com/drringo/bb-form/releases/download/v2.0.0/bb-form-linux-x86_64.tar.gz"
    sha256 "PLACEHOLDER_WILL_BE_UPDATED_BY_CI"
  end

  depends_on "babashka"

  def install
    bin.install "bb-form.sh" => "bb-form"
    bin.install "bb-form.clj"
  end

  def caveats
    <<~EOS
      bb-form requires Charm Gum for the terminal UI.
      Install it with:
        brew install charmbracelet/tap/gum

      Then run:
        bb-form forms/job_application.edn
    EOS
  end

  test do
    system "#{bin}/bb-form", "--help"
  end
end
