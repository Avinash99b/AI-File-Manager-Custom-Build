# AI File Manager

> AI-powered Android file management through natural language and Python.

AI File Manager is an open-source Android file manager that combines traditional file management with AI-generated Python workflows.

Instead of hardcoding hundreds of features, the AI writes Python code to solve your request. The generated code produces a structured filesystem action plan, which is then executed by the application's transaction engine.

The result is a flexible system capable of performing everything from simple file operations to complex automation workflows.

---

## ✨ Features

* 🤖 AI-powered file operations
* 🐍 AI-generated Python execution
* 🔒 Transaction-based filesystem engine
* 📂 Copy, Move, Delete, Rename & Create files
* 📊 Excel spreadsheet generation
* 🖼️ Image processing
* 📦 Large-scale batch operations
* 📝 Detailed execution logs
* ⚡ Natural language automation

---

## 🚀 Example Prompts

* Rename every image by its creation date
* Convert every JPEG to PNG
* Compress images while keeping OCR readability
* Generate 1000 random files for testing
* Organize Downloads by extension
* Create an Excel report of every file type
* Find duplicate files
* Generate random datasets
* Batch rename TV episodes
* Generate inventory reports

---

## 🏗️ Architecture

```text
Prompt
   │
   ▼
AI Model
   │
   ▼
Python Generator
   │
   ▼
Action Plan
   │
   ▼
Transaction Engine
   │
   ▼
Filesystem
```

The AI never directly modifies your filesystem.

Instead, it generates Python that produces a declarative list of filesystem actions. Those actions are validated and executed by the application's transaction engine.

---

## 📦 Bundled Python Environment

The AI has access to Python's standard library along with the following bundled packages:

### Scientific Computing

* NumPy
* SciPy
* Pandas
* SymPy

### Image Processing

* Pillow

### Data Visualization

* Matplotlib

### Web & Networking

* Requests
* urllib3
* certifi
* charset-normalizer
* idna

### HTML & XML

* BeautifulSoup4
* lxml

### Excel & Reports

* OpenPyXL
* ReportLab

### Data Formats

* PyYAML
* TOML
* JMESPath

### Date & Time

* python-dateutil
* pytz

### Utilities

* tqdm
* NetworkX
* regex
* pathspec
* packaging
* rich

This enables the AI to create sophisticated workflows such as data processing, spreadsheet generation, image manipulation, report creation, filesystem analysis, graph algorithms, symbolic mathematics, and much more.

---

## 🎯 Current Capabilities

* AI-assisted filesystem automation
* Batch file operations
* Image format conversion
* Spreadsheet generation
* Filesystem analysis
* Python-powered custom workflows

---

## 🚧 Roadmap

* OCR-powered workflows
* Archive management
* Duplicate detection
* AI-powered image optimization
* Semantic file search
* Plugin system
* More AI providers
* Improved UI/UX
* Additional bundled Python libraries

---

## 🤝 Contributing

Contributions, issues, discussions, and pull requests are always welcome.

If you have an idea for a new workflow or Python capability, feel free to open an issue.

---

## 📄 License

Licensed under the MIT License.
