# bb-form

**Hướng dẫn bằng tiếng Việt ở đây [README.vi.md](./README.vi.md)**

A script using Clojure Babashka and Charm-gum to collect data from beautiful forms in the terminal with an optimized user interface.

# System Requirements

- [Clojure babashka](https://babashka.org/)
- [Charm-gum](https://github.com/charmbracelet/gum)

# Quick Install with bbin

If you have [bbin](https://github.com/babashka/bbin) installed:

```bash
# Install from GitHub
bbin install github:drringo/bb-form

# Or install from local folder (for development)
bbin install .
```

Then you can run:
```bash
bb-form form.json [--values values.json] [--out output.json] [field1:value1 ...]
```

# User Interface Features

## Clean Screen
- Automatically clears the screen before showing the first form
- Clean interface, no old terminal content

## Fixed Status Line
- Shows error/status messages in a fixed position after the form title
- Error messages start with "::::" for easy recognition
- Error messages disappear only when the user enters a valid value
- Uses GUM style to display beautiful error messages with a red border

## Improved User Experience
- Continuously updates the screen to keep the interface clean
- Clear and easy-to-read error messages
- Consistent interface throughout the form process
- Reasonable spacing between UI components

# File Explanation

- `src/com/drbinhthanh/bb_form.clj`: Main Clojure file containing all form and UI logic
- `bb.edn`: Config file for bbin/babashka, compatible with bbin
- `form.json`: Form config file, supports branching questions
- `values.json`: File containing default values for the form
- `result.json`: Output file after filling the form in the terminal

## bb.edn Configuration

The `bb.edn` file is configured for bbin compatibility:

```clojure
{:deps {io.github.drringo/bb-form {:local/root "."}
        cheshire/cheshire {:mvn/version "5.11.0"}
        babashka/process {:mvn/version "0.5.22"}}
 :paths [".","src"] 
 :bbin/bin {bb-form {:main-opts ["-m" "com.drbinhthanh.bb-form"]}}}
```

### Config explanation:
- `:deps`: Required dependencies (cheshire for JSON, babashka/process for subprocess)
- `:paths`: Source code search paths
- `:bbin/bin`: bbin config with `:main-opts` to call the correct namespace

# Manual Usage (without bbin)

```bash
bb src/com/drbinhthanh/bb_form.clj form.json [--values values.json] [--out output.json] [field1:value1 ...]
```

## Usage Examples

```bash
# Basic form
bb-form form_sample.json

# Form with values file
bb-form form_sample.json --values values.json

# Form with custom output file
bb-form form_sample.json --out my_result.json

# Form with both values and output
bb-form form_sample.json --values values.json --out custom_output.json

# Form with values from command line
bb-form form_sample.json name:"Nguyen Van A" age:25
```

# How to create `form.json`

### Basic structure:
```json
{
  "title": "Form title",
  "description": "Form description",
  "fields": [
    {
      "id": "field_name",
      "label": "Display label",
      "type": "field_type",
      "required": true/false,
      "options": ["option 1", "option 2"],
      "regex": "^[a-zA-Z0-9]+$",
      "branch": {
        "option": [
          {
            "id": "subfield",
            "label": "Subfield label",
            "type": "field_type",
            "required": true/false
          }
        ]
      }
    }
  ]
}
```

### Example text field with regex:
```json
{
  "id": "email",
  "label": "Email",
  "type": "text",
  "required": true,
  "regex": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
  "regexError": "Invalid email format. Example: user@example.com"
}
```

### Supported field types:
- `text`: Text input (supports regex validation)
- `number`: Integer input with validation
- `date`: Date input (format DD-MM-YYYY) with validation
- `select`: Dropdown single choice
- `multiselect`: Multiple choices

### Field attributes:
- `id`: Unique identifier for the field
- `label`: Display label for the user
- `type`: Field type (text, number, date, select, multiselect)
- `required`: Input validation (true/false)
- `options`: Options list for select/multiselect
- `branch`: Branching logic - show subfields based on selection
- `regex`: Regex for text field validation (optional)
- `regexError`: Custom error message for regex (optional)

### Validation rules:
- **text**: Supports regex validation with custom error message
- **number**: Must be an integer
- **date**: Must be in DD-MM-YYYY format, defaults to today if blank
  - Supports shortcuts: `04` (4th of current month/year), `1204` (12th April current year)

### Validation improvements:
- Error messages show immediately when input is invalid
- Screen updates to show clear error messages
- Real-time validation with fast UI feedback

### Branching feature:
- Show subfields based on parent field selection
- Supports multi-level branching
- Auto hide/show fields based on logic

## Output file

Results are saved to `result.json` with the structure:
```json
{
  "selectedByUser": {
    "field1": "value1",
    "field2": "value2",
    "field_with_branch": "selected_option",
    "field_with_branch_branch": {
      "selected_option": {
        "subfield1": "subvalue1",
        "subfield2": "subvalue2"
      }
    },
    "multiselect_field": ["option1", "option2"],
    "multiselect_field_branch": {
      "option1": {
        "subfield1": "subvalue1"
      },
      "option2": {
        "subfield2": "subvalue2"
      }
    }
  }
}
```

### Data structure:
- **Top level**: All fields are grouped under the `"selectedByUser"` key
- **Simple fields**: Direct value (text, number, date, select)
- **Fields with branching**:
  - Main value is stored directly
  - Subfields are stored under `"{field_id}_branch"`
- **Multiselect fields**:
  - List of selected options stored directly
  - Subfields stored under `"{field_id}_branch"`
- **Unified structure**: Both select and multiselect use the `"_branch"` suffix
- **Multi-level support**: Subfields can have their own branches
- **Deep nesting**: Subfields can continue branching, creating `{subfield_id}_branch`

### Complex structure example:
```json
{
  "selectedByUser": {
    "name": "Nguyen Van A",
    "age": 25,
    "gender": "Female",
    "gender_branch": {
      "Female": {
        "is_pregnant": "Yes",
        "is_pregnant_branch": {
          "Yes": {
            "gestational_age": 20
          }
        }
      }
    },
    "symptoms": ["Fever", "Shortness of breath"],
    "symptoms_branch": {
      "Fever": {
        "temperature": 38.5
      },
      "Shortness of breath": {
        "breath_level": "Medium"
      }
    },
    "exam_date": "2024-01-15",
    "notes": "Additional notes"
  }
}
```

### How to access data:
```javascript
// Main value
const gender = result.selectedByUser.gender; // "Female"
const symptoms = result.selectedByUser.symptoms; // ["Fever", "Shortness of breath"]

// Subfields of select
const isPregnant = result.selectedByUser.gender_branch["Female"].is_pregnant; // "Yes"
const gestationalAge = result.selectedByUser.gender_branch["Female"].is_pregnant_branch["Yes"].gestational_age; // 20

// Subfields of multiselect
const temperature = result.selectedByUser.symptoms_branch["Fever"].temperature; // 38.5
const breathLevel = result.selectedByUser.symptoms_branch["Shortness of breath"].breath_level; // "Medium"
```

### Notes:
- All top-level fields are grouped under `"selectedByUser"`
- **Main value always present**: Fields with branching still store the selected value directly
- **Unified structure**: Both select and multiselect use the `"_branch"` suffix
- **Key in _branch**: Is the selected value (e.g. "Female", "Fever", "Shortness of breath")
- **Easy to process**: No need to check data type when accessing main value
- **Clear structure**: Subfields are organized by logic
- **Deep nesting**: Unlimited branching supported

# Recent Updates

## Current version
- ✅ **Fixed status line**: Error messages shown in a fixed position with prefix "::::"
- ✅ **GUM UI**: Uses Charm-gum for beautiful error messages
- ✅ **Real-time validation**: Error messages show immediately on invalid input
- ✅ **User experience**: Consistent and easy-to-use interface
- ✅ **Full bbin support**: Install and run via bbin

## Technical improvements
- Fixed validation syntax bugs
- Optimized error message display
- Improved screen clearing and re-render logic
- Increased UI stability
- Fixed `bb.edn` config for bbin compatibility
- Added `:main-opts` to call the correct namespace with bbin

## bbin fixes
- ✅ **Fixed `bb.edn` config**: Changed from `:ns-default` to `:main-opts` for bbin compatibility
- ✅ **Syntax fix**: Removed extra curly braces in config
- ✅ **Fully compatible**: Now can install and run via bbin without errors

# Troubleshooting

## Common issues

### Error "Could not resolve sym to a function"
- **Cause**: `bb.edn` config is not correct for bbin
- **Solution**: Make sure to use `:main-opts` instead of `:ns-default` in bbin config

### Error "FileNotFoundException --values"
- **Cause**: bbin cannot find the file in the current directory
- **Solution**: Make sure to run the command from the directory containing the form and values files

### Error "Command not found: bb-form"
- **Cause**: Script not installed with bbin
- **Solution**: Run `bbin install .` from the project directory

## How to check installation

```bash
# List installed scripts
bbin ls

# Check if the script works
bb-form --help
```
