<div align="center">

# 🎓 EduTrack — Intelligent University Academic Platform

**A complete university academic platform in pure Java — records management, exam analytics, scheduling, reporting and a REST API — powered end-to-end by classic data structures & algorithms.**

[![Java](https://img.shields.io/badge/Java_21%2B-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Dependencies](https://img.shields.io/badge/dependencies-none-2563EB)](#tech-stack)
[![Self-tests](https://img.shields.io/badge/self--tests-313_checks_passing-0E9F6E)](#-testing)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![GUI](https://img.shields.io/badge/GUI-Swing_%2B_Java2D-6B46C1)](#-screenshots)
[![API](https://img.shields.io/badge/REST_API-8_endpoints-D97706)](#-rest-api)

![EduTrack Dashboard](docs/screenshots/dashboard.png)

</div>

---

## ✨ What is EduTrack?

EduTrack models a real university academic system — **200 students, 12 faculty, 20 courses, 40 assignments, 981 exam records and a 100,000-event activity stream** — and answers the questions an academic office actually asks: *Who can teach what? Which exams conflict? Which students are at risk? How similar are two submissions? What's the fastest way to find anything?*

Every answer is computed with a **hand-implemented DSA algorithm** (no external libraries — JDK only), exposed through **two interfaces** (a Swing GUI and a console CLI), persisted to **CSV files**, and exposed to other systems through a **built-in REST API**.

## 🚀 Feature Highlights

| Area | What you get |
|---|---|
| 🗂️ **Records Browser** | Filter/sort students, faculty, courses · detail dialogs with marks, GPA, rank & recent activity · CSV export |
| ✏️ **Manage Records (CRUD)** | Add/remove students, faculty, courses (cascading deletes) · enroll/drop · marks entry with live grade preview · dirty tracking |
| 💾 **CSV Persistence** | Save/load `students.csv`, `faculty.csv`, `courses.csv`, `exams.csv` · one-click reset to generated data |
| 🔎 **Smart Search** | One box searches everything — KMP exact matching, AND multi-term ranking, Levenshtein *did-you-mean* fallback |
| 📊 **Exam Analytics** | Per-course statistics, GPA toppers, at-risk detection, report cards |
| 📈 **Activity Analytics** | 100k-event stream: actions, hourly rhythm, top courses/students |
| 🧾 **Reports & Export** | Official-style transcripts, grade sheets, department summaries, at-risk lists |
| 🌐 **REST API** | 8 JSON endpoints over JDK's built-in HTTP server — start/stop from the dashboard |
| 🧮 **6 DSA Modules** | 24 algorithms implemented from scratch and woven into the features (below) |

## 🧠 DSA Coverage

| Module | Algorithms | Used for |
|---|---|---|
| **M1 · String Algorithms** | KMP · Z-Function · Rabin-Karp · Aho-Corasick | Course/student search, repeated-phrase detection, code lookup, multi-keyword scans |
| **M2 · Suffix Structures** | Suffix Array · **SA-IS** (linear) · Kasai LCP · Suffix Automaton | Document indexing, match highlighting, cross-submission similarity |
| **M3 · Advanced DP** | Levenshtein · Damerau · Matrix-Chain · Bitmask DP · Optimal BST | Typo-tolerant queries, pipeline optimization, course selection, key organization |
| **M4 · Network Flow** | Hopcroft-Karp · Ford-Fulkerson · Edmonds-Karp · Dinic · König | Faculty→course matching, classroom allocation, conflict analysis |
| **M5 · NP-Completeness** | DPLL SAT · 3-SAT→CLIQUE · CLIQUE→IND-SET→VERTEX-COVER · VC 2-approx | Exam scheduling, constraint reductions, conflict covering |
| **M6 · Randomized & Parallel** | Randomized QuickSort (3-way) · ForkJoin Merge Sort · Reservoir Sampling | CGPA ranking, 1M-record benchmarks, stream sampling |

Every module has an interactive CLI menu **and** a dedicated GUI screen with visualizations (graph canvases, tree drawings, timetable grids, benchmark charts).

## 🖥️ Screenshots

<details open>
<summary><b>Platform</b></summary>

| Records | Manage (CRUD) | Exams & Grades |
|---|---|---|
| ![Records](docs/screenshots/records.png) | ![Manage](docs/screenshots/manage.png) | ![Exams](docs/screenshots/exams.png) |

| Activity Analytics | Reports & Export | Smart Search |
|---|---|---|
| ![Analytics](docs/screenshots/analytics.png) | ![Reports](docs/screenshots/reports.png) | ![Search](docs/screenshots/search.png) |

</details>

<details>
<summary><b>DSA Modules (M1–M6)</b></summary>

| M1 · String Algorithms | M2 · Suffix Structures |
|---|---|
| ![M1](docs/screenshots/m1-strings.png) | ![M2](docs/screenshots/m2-suffix.png) |

| M3 · Advanced DP | M4 · Network Flow |
|---|---|
| ![M3](docs/screenshots/m3-dp.png) | ![M4](docs/screenshots/m4-flow.png) |

| M5 · Exam Scheduling | M6 · Randomized & Parallel |
|---|---|
| ![M5](docs/screenshots/m5-scheduling.png) | ![M6](docs/screenshots/m6-randomized.png) |

</details>

## ⚡ Quick Start

**Requirements:** JDK 21 or newer. Nothing else.

```bash
# Clone and build
git clone https://github.com/rushmanthnalluri/EduTrack-Intelligent-University-Academic-Platform.git
cd EduTrack-Intelligent-University-Academic-Platform
javac -d bin -sourcepath src src/edutrack/gui/EduTrackGUI.java src/edutrack/EduTrack.java

# Run the GUI
java -cp bin edutrack.gui.EduTrackGUI

# Or run the console app (12-option menu)
java -cp bin edutrack.EduTrack
```

The app ships with a deterministic generated dataset (seed 42). Your edits are saved to `DataSets/*.csv` via **Manage Records → Save to CSV files** and loaded automatically on next start; *Reset to generated data* restores the original.

## 🌐 REST API

Start from the dashboard's **REST API Server** card or CLI option 10 (default port 8080):

```bash
curl http://localhost:8080/api/summary
curl http://localhost:8080/api/students?id=1000
curl http://localhost:8080/api/courses?code=CS201
curl "http://localhost:8080/api/search?q=data%20structures"
curl http://localhost:8080/api/analytics
```

| Endpoint | Description |
|---|---|
| `GET /api/summary` | Dataset counts + persistence flag |
| `GET /api/students` · `?id=N` | Students; detail adds exam records + weighted GPA |
| `GET /api/courses` · `?code=X` | Courses; detail adds exam statistics |
| `GET /api/faculty` | Faculty with expertise lists |
| `GET /api/exams?studentId=&course=` | Exam records, filterable |
| `GET /api/search?q=…` | Smart-search results with relevance scores |
| `GET /api/analytics` | Activity-stream analytics summary |

Errors return JSON `400/404/405` bodies. Handlers are thread-safe (copy-on-write snapshots).

## 🏗️ Project Structure

```
├── src/
│   ├── modules/                 Shared string matchers (KMP, Rabin-Karp, Z-Function)
│   └── edutrack/
│       ├── EduTrack.java        CLI entry point
│       ├── model/               Student, Faculty, Course, Assignment, Resource, ActivityEvent, ExamRecord
│       ├── data/                DataStore (data + CRUD API) · CsvStore (CSV persistence)
│       ├── modules/             M1–M6 algorithm modules (CLI + self-test each)
│       ├── features/            RecordQueries · ExamAnalytics · ActivityAnalytics · ManageSupport · ReportGenerator
│       ├── search/              SearchService (global smart search)
│       ├── api/                 ApiServer · JsonWriter (REST API)
│       └── gui/                 EduTrackGUI + theme/components + 12 panels
├── docs/
│   ├── screenshots/             13 verified GUI screenshots
│   └── EduTrack_Report.tex      Full LaTeX project report
├── DataSets/
│   └── Wikipedia.txt            1 MB document corpus for indexing/search demos
└── README.md
```

## 📚 Documentation

A full project report (architecture, data model, per-module algorithms with complexities, API reference, embedded screenshots, verification methodology) is available in LaTeX: [`docs/EduTrack_Report.tex`](docs/EduTrack_Report.tex). Compile it with any LaTeX distribution:

```bash
cd docs && pdflatex EduTrack_Report.tex   # or upload to Overleaf
```

## ✅ Testing

Every module ships a non-interactive self-test that exits non-zero on failure — **313 checks in total**, cross-verified against naive/brute-force references (SA-IS vs prefix-doubling on adversarial strings, Hopcroft-Karp vs exhaustive brute force, DPLL on pigeonhole formulas, reduction round-trips, reservoir uniformity bounds, 60 concurrent API requests, …):

```bash
java -cp bin edutrack.modules.M1StringAlgorithms
java -cp bin edutrack.modules.M2SuffixStructures
java -cp bin edutrack.modules.M3DynamicProgramming
java -cp bin edutrack.modules.M4NetworkFlow
java -cp bin edutrack.modules.M5NPCompleteness
java -cp bin edutrack.modules.M6RandomizedParallel
java -cp bin edutrack.features.ExamAnalytics
java -cp bin edutrack.features.RecordQueries
java -cp bin edutrack.features.ActivityAnalytics
java -cp bin edutrack.features.ManageSupport
java -cp bin edutrack.features.ReportGenerator
java -cp bin edutrack.search.SearchService
java -cp bin edutrack.api.ApiServer
```

## 🛠️ Tech Stack

- **Java 21+** — records-style model classes, modern APIs
- **Swing + Java2D** — custom graph/tree/timetable/chart canvases, `SwingWorker` for all background work
- **`com.sun.net.httpserver`** — REST API with zero external dependencies
- **Hand-rolled everything** — JSON writer, CSV reader/writer, every algorithm

## 📄 License

Released under the [MIT License](LICENSE).

---

<div align="center">
Built as a DSA-3 course project — demonstrating that string algorithms, suffix structures, dynamic programming, network flow, NP-completeness and randomized algorithms are one coherent toolkit for real systems.
</div>
