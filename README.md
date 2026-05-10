# bb-form

**Hướng dẫn bằng tiếng Việt: [README.vi.md](./README.vi.md)**

A Babashka + Charm Gum CLI tool to collect data through beautiful terminal forms, powered by a full-featured **EDN Logic Engine** with formula imports, dynamic labels, and Bayesian inference support.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🧠 **EDN Logic Engine** | Flat-list fields with `eval-expr` interpreter (`and/or/not/=/>/<`) |
| 🔀 **Dynamic `:show-if`** | Conditionally show/hide fields based on runtime state |
| 🗄️ **Hidden Variables** | Declare `:variables` to track background state |
| ⚡ **Side Effects** | `:actions` to mutate state on each answer |
| 📦 **Formula Imports** | Import `.edn` or `.clj` formula libraries with namespace aliases |
| 🔣 **`[:call]` Operator** | Call any imported function using `[:call :alias/fn-name arg …]` |
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

**Examples:**

```bash
# Basic interactive form
bb-form forms/job_application.edn

# Non-interactive batch run
bb-form forms/job_application.edn --values forms/values.edn --out result.edn

# Bayesian scoring example
bb-form forms/bayesian_recruitment.edn
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
| `:date` | Date input (DD-MM-YYYY) with shortcuts |
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
