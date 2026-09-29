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

EduTrack models a real university academic system — **200 students, 12 faculty, 20 courses, 40 assignments, 981 exam records and a activity event stream** — and answers the questions an academic office actually asks: *Who can teach what? Which exams conflict? Which students are at risk? How similar are two submissions? What's the fastest way to find anything?*

Every answer is computed with a **hand-implemented DSA algorithm** (no external libraries — JDK only), exposed through **two interfaces** (a Swing GUI and a console CLI), persisted to **CSV files**, and exposed to other systems through a **built-in REST API**.

## 🚀 Feature Highlights

| Area | What you get |
|---|---|
| 🗂️ **Records Browser** | Filter/sort students, faculty, courses · detail dialogs with marks, GPA, rank & recent activity · CSV export |
| ✏️ **Manage Records (CRUD)** | Add/remove students, faculty, courses (cascading deletes) · enroll/drop · marks entry with live grade preview · dirty tracking |
| 💾 **CSV Persistence** | Save/load `students.csv`, `faculty.csv`, `courses.csv`, `exams.csv` · one-click reset to generated data |
| 🔎 **Smart Search** | One box searches everything — KMP exact matching, AND multi-term ranking, Levenshtein *did-you-mean* fallback |
| 📊 **Exam Analytics** | Per-course statistics, GPA toppers, at-risk detection, report cards |
| 📈 **Activity Analytics** | activity event stream: actions, hourly rhythm, top courses/students |
| 🧾 **Reports & Export** | Official-style transcripts, grade sheets, department summaries, at-risk lists |
| 🌐 **REST API** | 8 JSON endpoints over JDK's built-in HTTP server — start/stop from the dashboard |
| 🧮 **6 DSA Modules** | 24 algorithms implemented from scratch and woven into the features (below) |

## 🧠 DSA Coverage

| Module | Algorithms | Used for |
|---|---|---|
| **String Algorithms** | KMP · Z-Function · Rabin-Karp · Aho-Corasick | Course/student search, repeated-phrase detection, code lookup, multi-keyword scans |
| **Suffix Structures** | Suffix Array · **SA-IS** (linear) · Kasai LCP · Suffix Automaton | Document indexing, match highlighting, cross-submission similarity |
| **Advanced DP** | Levenshtein · Damerau · Matrix-Chain · Bitmask DP · Optimal BST | Typo-tolerant queries, pipeline optimization, course selection, key organization |
| **Network Flow** | Hopcroft-Karp · Ford-Fulkerson · Edmonds-Karp · Dinic · König | Faculty→course matching, classroom allocation, conflict analysis |
| **NP-Completeness** | DPLL SAT · 3-SAT→CLIQUE · CLIQUE→IND-SET→VERTEX-COVER · VC 2-approx | Exam scheduling, constraint reductions, conflict covering |
| **Randomized & Parallel** | Randomized QuickSort (3-way) · ForkJoin Merge Sort · Reservoir Sampling | CGPA ranking, 1M-record benchmarks, stream sampling |

Every module has an interactive CLI menu **and** a dedicated GUI screen with visualizations (graph canvases, tree drawings, timetable grids, benchmark charts).

## 🖥️ Screenshots

<details open>
<summary><b>Platform screens</b></summary>

**Dashboard** — home screen: live dataset statistics (200 students · 12 faculty · 20 courses · 40 assignments · 981 exam records · an activity event stream), one card per feature and algorithm area, and the interactive **REST API Server** card (port + start/stop).

![Dashboard](docs/screenshots/dashboard.png)

**Records Browser** — sortable, KMP-filterable tables for students, faculty and courses; double-click any row for a detail view with enrolled courses, marks, weighted GPA, CGPA rank and recent activity pulled from the activity event stream.

![Records](docs/screenshots/records.png)

**Manage Records** — the CRUD console: add/remove students, faculty and courses (cascading deletes show live impact counts), enroll/drop courses, and enter marks with a live grade preview. The amber dirty indicator, *Save to CSV files* and *Reset to generated data* drive persistence.

![Manage](docs/screenshots/manage.png)

**Exams & Grades** — per-course exam statistics (students, average, min, max, pass %) next to the overall grade-distribution chart, computed from 981 midsem+endsem records. Other tabs rank GPA toppers, flag at-risk students and print report cards.

![Exams](docs/screenshots/exams.png)

**Activity Analytics** — one pass over the activity event stream: summary stat cards (37 active days, 2,702.7 events/day, 13:00 UTC peak, 72.8% course-related), the hourly rhythm chart, per-action totals, and the top-10 courses and students.

![Analytics](docs/screenshots/analytics.png)

**Reports & Export** — an official-style transcript preview (per-course marks, credits attempted/earned, weighted GPA, stored CGPA, class rank) ready to save as text; grade sheets, department summaries and at-risk lists export as CSV.

![Reports](docs/screenshots/reports.png)

**Smart Search** — the query `data` ranked across resources, courses and assignments in 26 ms: exact-code and prefix matches outrank plain substring hits, and a typo'd query falls back to Levenshtein *did-you-mean* suggestions.

![Search](docs/screenshots/search.png)

</details>

<details>
<summary><b>Algorithm feature screens</b></summary>

**KMP keyword search** — searching `data` across all course codes/names and student names returns CS201 and CS301 with exact match positions in 1 ms. The other tabs run Z-Function repeated-phrase detection on assignment texts, Rabin-Karp rolling-hash code lookup, and an Aho-Corasick automaton that scans the whole assignment corpus (or the 1 MB Wikipedia document) for 10 academic keywords at once.

![Academic Search](docs/screenshots/m1-strings.png)

**Suffix-array indexing with match highlighting** — the suffix array of an assignment is built twice for comparison: prefix-doubling (4.4 ms) vs **SA-IS linear-time construction (1.7 ms)**, verified identical. Searching `deadline` locates the occurrence in 0.056 ms and highlights it inside the document view. Other tabs report repeated phrases (Kasai LCP), cross-submission similarity and suffix-automaton statistics.

![Document Similarity](docs/screenshots/m2-suffix.png)

**Optimal Binary Search Tree** — the minimum-expected-cost search tree over the 12 most-accessed course codes, with real access frequencies counted from the activity stream. Node color marks the root, and the expected search cost (163,233) is compared against a balanced BST (185,753) — 12.1% lower. The other tabs cover Levenshtein/Damerau query correction, matrix-chain optimization and the bitmask-DP course explorer.

![Query Optimization](docs/screenshots/m3-dp.png)

**Bipartite matching + König cover** — Hopcroft-Karp matches all 12 faculty to eligible courses (bold blue edges over gray eligibility edges); the amber nodes are the **minimum vertex cover** reconstructed via König's theorem, with |cover| = |matching| = 12 verified. The right side runs Ford-Fulkerson/Edmonds-Karp room-slot allocation (27/27 sections) and the Dinic scaled benchmark.

![Resource Allocation](docs/screenshots/m4-flow.png)

**DPLL exam scheduling + timetable** — the course-conflict graph (131 edges, department-colored) feeds a SAT encoding solved by DPLL: 5 slots suffice for 10 courses (clique bound 5, 265 clauses, 14 ms, schedule verified). Slot tags (S1–S5) appear on the graph nodes and the generated exam timetable renders as a MON–FRI grid below.

![Exam Scheduling](docs/screenshots/m5-scheduling.png)

**Randomized quicksort + benchmarks** — 200 students ranked by CGPA (1,834 comparisons, 1 ms); below, the 1,000,000-element benchmark charts compare randomized quicksort vs `Arrays.sort` vs a deterministic pivot, and the adversarial chart shows the deterministic variant's O(n²) comparison blow-up on sorted input (50M → 200M → 800M as n doubles).

![Ranking and Streams](docs/screenshots/m6-randomized.png)

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
│       ├── modules/             algorithm implementations (CLI + self-test each)
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
