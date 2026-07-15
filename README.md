# bb-form

**Hướng dẫn bằng tiếng Việt: [README.vi.md](./README.vi.md)**

A Babashka + Charm Gum CLI tool to collect data through beautiful terminal forms, powered by a full-featured **EDN Logic Engine** with formula imports, dynamic labels, and Bayesian inference support.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🧠 **Expert System** | Run with O'Doyle RETE forward-chaining rules and a backward-chaining solver when `:format :expert` is declared in the EDN file |
| 🔀 **Dynamic `:show-if`** | Conditionally show/hide fields based on runtime state |
| 🗄️ **Hidden Variables** | Declare `:variables` to track background state |
| ⚡ **Side Effects** | `:actions` to mutate state on each answer |
| 📦 **Formula Imports** | Import `.edn` or `.clj` formula libraries with namespace aliases |
| 🔣 **`[:call]` Operator** | Call any imported function using `[:call :alias/fn-name arg …]` or shorthand `[:alias/fn arg ...]` |
| 🔍 **`[:get]` Operator** | Extract properties from complex map results |
| 🎲 **Stochastic Support** | Integrate Bayesian Stan models via Clojure bridge scripts |
| 🏷️ **Dynamic Labels** | Interpolate computed values into labels with `{{expr}}` |
| 📋 **`:info` Field** | Read-only display field that saves its resolved label to output |
| 📁 **Multi-Stage Forms** | Organize complex forms into `:stages` with `on-begin`/`on-end` hooks |

---

## 📦 Installation

### Option 1 — bbin (recommended, all platforms)

```bash
# Requires babashka + bbin
bbin install io.github.drringo/bb-form
```

### Option 2 — Scoop (Windows)

```powershell
scoop bucket add drringo https://github.com/drringo/bb-form
scoop install bb-form
```

### Option 3 — Homebrew (macOS / Linux)

```bash
brew tap drringo/bb-form https://github.com/drringo/bb-form
brew install bb-form
```

### Option 4 — Manual (any platform)

```bash
git clone https://github.com/drringo/bb-form
cd bb-form
bb src/com/drbinhthanh/bb_form.clj <form.edn>
```

### Prerequisites

| Tool | Purpose | Install |
|---|---|---|
| [Babashka](https://babashka.org/) | Clojure runtime | `scoop install babashka` / `brew install borkdude/brew/babashka` |
| [Charm Gum](https://github.com/charmbracelet/gum) | Terminal UI | `winget install charmbracelet.gum` / `brew install charmbracelet/tap/gum` |

---

## 🚀 Usage

```bash
bb-form <form.edn> [OPTIONS]
```

| Option | Description |
|---|---|
| `--values <file.edn>` | Pre-fill answers from an EDN file (non-interactive / batch mode) |
| `--out <file.edn>` | Output result path (default: `result.edn`) |
| `--engine <gum|tui|formsmd|winform|web>` | Select the rendering engine: `gum` (requires Gum, default), `tui` (native Clojure TUI via JLine3), `formsmd` (compiles to Forms.md web Markdown/HTML), `winform` (placeholder), or `web` (placeholder) |

**Examples:**

```bash
# Basic interactive form (using default Gum engine)
bb-form forms/job_application.edn

# Using the new native Clojure JLine3 TUI engine
bb-form forms/job_application.edn --engine tui

# Run a form that automatically runs in Expert System mode via its EDN format configuration
bb-form forms/sanh-expert.edn

# Non-interactive batch run
bb-form forms/job_application.edn --values forms/values.edn --out result.edn

# Bayesian scoring example with native TUI
bb-form forms/bayesian_recruitment.edn --engine tui
```

---

## 📝 Form Structure

A form is an EDN map. Minimal example:

```clojure
{:title       "My Form"
 :description "Fill in the details below."

 ;; Hidden state variables (not shown to user)
 :variables {:score 0}

 :fields
 [{:id       :name
   :label    "Your name?"
   :type     :text
   :required true}

  {:id      :age
   :label   "Your age?"
   :type    :number
   :show-if [:>= [:var :score] 0]}

  {:id      :result
   :type    :info
   :label   "Your score is {{[:var :score]}} points."}]}
```

### Supported field types

| Type | Description |
|---|---|
| `:text` | Text input with optional `:regex` validation |
| `:number` | Integer input |
| `:date` | Date input (DD-MM-YYYY) with shortcuts (`DD`, `DDMM`, `DDMMYYYY`, `DD[+-]N`, `DDMM[+-]N`) |
| `:datetime` | Datetime input (DD-MM-YYYY HH:MM) with shortcuts (including time-only inputs) |
| `:select` | Single-choice dropdown |
| `:multiselect` | Multiple-choice selection |
| `:hidden` | Computed value, not displayed |
| `:info` | Read-only computed label, saved to output |

### EDN Logic Operators (`eval-expr`)

```clojure
[:var :field_id]               ; get variable value
[:= expr expr]                 ; equality
[:!= expr expr]                ; inequality
[:> :< :>= :<=]                ; numeric comparison
[:and e1 e2 ...]               ; logical AND
[:or  e1 e2 ...]               ; logical OR
[:not e]                       ; logical NOT
[:+ :- :* :/]                  ; arithmetic
[:if cond then else]           ; conditional expression
[:get map-expr :key]           ; get map property
[:contains? [:var :list] val]  ; list membership
[:call :alias/fn arg ...]      ; call imported formula function
[:str/includes? s sub]         ; string contains
```

---

## 📦 Importing Formula Libraries

Import `.edn` formula files or full Clojure `.clj` scripts:

```clojure
:import ["../formulas/cardio_risk.edn"
         ["../formulas/candidate_score.clj" :as :score]]
```

Use `:as :alias` to shorten long Clojure namespaces. Then call functions:

```clojure
:actions [[:set :result [:call :score/analyze-candidate [:var :x] [:var :y]]]]
```

---

## 🎲 Bayesian / Stan Integration

Bridge a Stan model through a `.clj` wrapper script:

```clojure
;; formulas/bayesian_hiring.clj
(ns formulas.bayesian-hiring)
(defn run-stan-model [experience test-score]
  ;; calls cmdstan via shell, returns posterior map
  {:prob 78 :variance 0.02 :lower_bound 50 :upper_bound 100 :confidence "95%"})
```

Import and call it in a form:

```clojure
:import [["../formulas/bayesian_hiring.clj" :as :stan]]
:actions [[:set :result [:call :stan/run-stan-model [:var :exp] [:var :score]]]]
:label   "Hire probability: {{[:get [:var :result] :prob]}}%"
```

---

## 📂 Project Structure

```
bb-form/
├── src/com/drbinhthanh/bb_form.clj   # Core engine
├── forms/                             # Sample forms
│   ├── job_application.edn            # Matrix scoring example
│   ├── bayesian_recruitment.edn       # Bayesian hiring example
│   └── health_check.edn
├── formulas/                          # Formula libraries
│   ├── candidate_score.clj
│   ├── bayesian_hiring.clj
│   ├── cardio_risk.edn
│   └── stan_models/hiring_model.stan
├── bucket/bb-form.json                # Scoop manifest
├── Formula/bb-form.rb                 # Homebrew formula
└── guide.md                           # Full Vietnamese user guide
```

---

## 🗓️ Changelog

### v2.3.0
- ✅ **Expert System Mode (`:format :expert` or `:format "expert"`)**:
  - Tightly integrated a RETE forward-chaining rules engine (`net.sekao/odoyle-rules`) and a backward-chaining solver (`resolve-var`) to interactively ask users only the logically required minimal set of questions to resolve the specified `:goals`.
  - Added support for rule prioritization (`:priority` in rules) to handle conflict resolution when multiple rules overwrite a single output.
  - Implemented automatic required variable detection from `:if` and `:then` formulas, making boilerplate `:exists :var` checks completely optional.
  - Added support for the `:require [list of vars]` block at the rule level to explicitly specify dependencies.
  - Supported namespace shorthand calling syntax (`[:ns/fn args...]` instead of `[:call :ns/fn args...]`).

### v2.2.0
- ✅ **Forms.md Web Engine (`--engine formsmd`)**:
  - Compiles EDN forms to Markdown-like templates and static HTML pages compatible with Forms.md engine.
  - Supports live browser preview with local web server via `--serve` flag (runs httpkit on port 8080).
  - Handles Stage-level conditional logic using official Forms.md jump condition syntax (`-> conditionExpr`).
  - Introduced `:form` field property (`"Email"`, `"Tel"`, `"URL"`, `"Rating"`, etc.) for specialized input mapping while retaining terminal backward compatibility.

### v2.1.0
- ✅ **Phase 7**: UI Engine Virtualization & Native JLine3 TUI support
  - Decoupled terminal logic into a centralized `com.drbinhthanh.bb-form.core` orchestrator.
  - Added new native JLine3-based TUI engine (`--engine tui`) supporting arrow key menu selections and interactive text prompts with no external CLI dependencies.
  - Maintained backward compatibility with default Charm Gum engine (`--engine gum`).
  - Added draft placeholder engines for Windows Forms (`winform`) and Web/HTML (`web`).
  - Added automatic type coercion for pre-filled and skipped CLI variables.

### v2.0.0
- ✅ **Phase 5**: `:import` formula libraries (`.edn` & `.clj`) with `:as` namespace alias
- ✅ **Phase 6**: Stochastic variable support via Bayesian Stan model bridge
- ✅ `[:call]` operator with alias resolution (priority: Clojure ns → EDN registry)
- ✅ `[:get]` operator to extract map properties
- ✅ `:info` field type — computed read-only display saved to output
- ✅ Dynamic label interpolation `{{expr}}`
- ✅ Bug fix: actions now execute correctly in batch `--values` mode

### v1.0.0 (Phases 1–4)
- ✅ Migration from `.json` to `.edn`
- ✅ Flat-list fields + `:show-if` EDN expressions
- ✅ `eval-expr` Logic Engine + Restarting Loop algorithm
- ✅ `:variables` hidden state + `:actions` side effects
- ✅ Multi-stage forms with `on-begin`/`on-end` hooks

---

## 📖 Documentation

- **Vietnamese user guide**: [`guide.md`](./guide.md)
- **Concept & design**: [`jsonlogic_concept.md`](./jsonlogic_concept.md)
