# Intelligent Hospital Emergency Management System Using Priority Queues

A Java simulation of a hospital Emergency Department (ED), built strictly around
**priority queues**:

1. A triage scorer turns each patient's ESI level and vital signs into a priority score.
2. The patient is routed to a **disease-wise panel** (Cardiology, Neurology, Trauma &
   Ortho, Respiratory, Gastro & Abdominal, General Medicine).
3. Each panel has its **own max-heap of waiting patients** and its own doctors, so inside
   every panel the most urgent patient is always treated next.

Every "who/what goes next" decision in the program is made by a heap: patients, doctors,
the simulation clock, doctor allocation, and even the median/percentile statistics.
There is no library sorting anywhere. The only `HashMap`s are in file reading (CSV
column names, and joining the two MIMIC tables); none of them take part in queueing.
`java.util.PriorityQueue` appears only as a benchmark baseline.

Pure Java 17+ (tested on JDK 21). No external libraries.

---

## 1. Every priority queue in the project

| # | Priority queue | Type | Ordered by | Where |
|---|---|---|---|---|
| 1 | Waiting patients (one per panel) | **IndexedMaxHeap** (ours) | priority score, then arrival | `DepartmentPanel.waiting` |
| 2 | Idle doctors (one per panel) | MinHeap | minutes already worked | `DepartmentPanel.idle` |
| 3 | Simulation clock | MinHeap | event time | `EDSimulator.events` |
| 4 | Doctor allocation across panels | Max-heap (reversed MinHeap) | workload per doctor | `EDSimulator.allocateDoctors` |
| 5 | Panel board view | Max-heap (reversed MinHeap) | priority score | `DepartmentPanel.board` |
| 6 | Statistics | MinHeap (heap sort) | wait time | `Stats.compute` |
| 7 | Real-data loader | MinHeap | real arrival time | `MimicEdAdapter` |
| — | Baselines for comparison | sorted array, unsorted array, `java.util.PriorityQueue` | | `step02_datastructures` |

Even the FIFO baseline strategy is a priority queue: it is the same heap keyed by arrival time.

---

## 2. Read the files in this order

Every file has a header comment (`FILE n`) explaining what it does and why.

| # | File | What it does |
|---|------|--------------|
| 1 | `step01_model/Patient.java` | Patient: ESI level, vitals, complaint, panel, arrival, treatment time |
| 2 | `step01_model/Doctor.java` | Doctor, belongs to one panel |
| 3 | `step01_model/Event.java` | Simulation events and their time ordering |
| 4 | `step01_model/Department.java` | **The 6 disease-wise panels** + complaint → panel routing |
| 5 | `step02_datastructures/PatientQueue.java` | Common interface + the one ordering rule (priority, then FIFO) |
| 6 | `step02_datastructures/IndexedMaxHeap.java` | **Main structure**: array max-heap with O(log n) update-priority |
| 7 | `step02_datastructures/MinHeap.java` | Generic heap (min, or max with a reversed comparator) |
| 8 | `step02_datastructures/SortedArrayQueue.java` | Baseline PQ: O(n) insert, O(1) extract |
| 9 | `step02_datastructures/UnsortedArrayQueue.java` | Baseline PQ: O(1) insert, O(n) extract |
| 10 | `step02_datastructures/JavaPriorityQueue.java` | Baseline PQ: `java.util.PriorityQueue` (O(n) update) |
| 11 | `step03_triage/TriageScorer.java` | ESI + vitals → priority score; deterioration |
| 12 | `step04_data/CsvUtil.java` | Quote-aware CSV parsing, `.csv.gz` support |
| 13 | `step04_data/DataGenerator.java` | Synthetic dataset |
| 14 | `step04_data/CsvLoader.java` | Loads the standard CSV with validation |
| 15 | `step04_data/MimicEdAdapter.java` | Converts the **real MIMIC-IV-ED** dataset |
| 16 | `step05_simulation/Strategy.java` | FIFO vs static priority vs priority-with-aging |
| 17 | `step05_simulation/DepartmentPanel.java` | **One panel = its own patient heap + doctor heap** |
| 18 | `step05_simulation/EDSimulator.java` | Event-driven simulation across all panels |
| 19 | `step06_analysis/Stats.java` | Wait times by ESI level and by panel |
| 20 | `step06_analysis/Benchmark.java` | Performance experiments |
| 21 | `step06_analysis/SelfTest.java` | Correctness tests |
| 22 | `Main.java` | Menu that runs everything |

---

## 3. How the disease-wise panels work

```
 Patient arrives ──► TriageScorer ──► priority score
        │
        ▼  Department.route(chief complaint)
 ┌──────────────┬──────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
 │ Cardiology   │ Neurology    │ Trauma&Ortho │ Respiratory  │ Gastro&Abdo  │ General Med  │
 │ max-heap     │ max-heap     │ max-heap     │ max-heap     │ max-heap     │ max-heap     │
 │ doctor heap  │ doctor heap  │ doctor heap  │ doctor heap  │ doctor heap  │ doctor heap  │
 └──────────────┴──────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
        │  when a panel's doctor is free:
        ▼  extractMax() from THAT panel's heap → most urgent patient of that disease group
```

| Panel | Example complaints |
|---|---|
| Cardiology | cardiac arrest, chest pain, palpitations, syncope |
| Neurology | stroke symptoms, unresponsive, head injury, headache, dizziness |
| Trauma & Ortho | major trauma, fracture, laceration, sprain, burn, fall, leg pain |
| Respiratory | respiratory distress, asthma, shortness of breath, cough |
| Gastro & Abdominal | abdominal pain, GI bleed, vomiting, kidney stone, urinary |
| General Medicine | fever, sepsis, overdose, rash, sore throat, refills (everything else) |

Routing is keyword-based, so it also works on the free-text complaints in real MIMIC
data (e.g. "Chest pain, Dyspnea" → Cardiology, "s/p Fall" → Trauma, "SOB" → Respiratory).

**Doctors are split across panels with a max-heap.** Every panel that has patients
gets 1 doctor. Then the panel with the highest workload per doctor is repeatedly
extracted, given one more doctor, and re-inserted.

**Live demo (option 2)** prints a board every simulated hour, like this real output:

```
========== PANEL BOARD at Day 1 06:00 (waiting patients, highest priority first) ==========
  +-- Cardiology          doctors free 0/1 | waiting 1
  |   1. P00038  ESI-2  priority  122.5  chest pain             waited 58 min  (WORSENED)
  +-- Gastro & Abdominal  doctors free 0/1 | waiting 1
  |   1. P00036  ESI-3  priority   76.8  kidney stone           waited 67 min
  ...
```

---

## 4. Set up in VS Code

1. Install **JDK 17 or newer** (e.g. Eclipse Temurin 21). Check: `java -version`.
2. Install the VS Code extension **Extension Pack for Java** (Microsoft).
3. `File → Open Folder…` and choose the `HospitalER` folder (the one containing `src/`).
   The included `.vscode/` settings tell VS Code that `src/` is the source folder.
4. Open `src/Main.java` and click **Run** above `main`, or use the Run panel and pick
   **Run Hospital ER (menu)**. Type an option number in the terminal.

From a terminal instead (inside `HospitalER/`):

```bash
javac -d out $(find src -name "*.java")
java -cp out Main          # interactive menu
java -cp out Main 3        # run one option directly
```

Windows PowerShell compile command:
```powershell
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
```

Always run from the `HospitalER` folder so `data/` and `results/` are found.

---

## 5. Menu options

| Option | What happens |
|---|---|
| 1 | Self-test (7 tests): heap order, FIFO ties, update-priority, all PQs agree, MinHeap, panel routing, doctor allocation |
| 2 | Live demo: 40 patients, 6 panels × 1 doctor; every arrival/deterioration/treatment logged, panel boards every hour |
| 3 | 3 strategies × 7, 8, 10 doctors on 5,000 patients; results by ESI level and by panel → `results/strategy_comparison.csv` |
| 4 | Benchmarks the four priority-queue implementations → `results/benchmark.csv` |
| 5 | Regenerates `data/ed_patients_5000.csv` (same seed → same file every time) |
| 6 | Runs option 3 on the real MIMIC-IV-ED demo data |

---

## 6. Datasets

### 6a. Synthetic dataset (included: `data/ed_patients_5000.csv`)
5,000 patients over ~32 days, generated by `DataGenerator` with a fixed seed:
- **Arrivals**: Poisson process with an hour-of-day rate (quiet ~4 am, busiest ~4 pm),
  averaging 6.5 patients/hour.
- **ESI mix**: 1.2% / 12.3% / 42.8% / 30.9% / 12.9% for levels 1–5.
- **Panel mix**: Cardiology 8% · Neurology 12% · Trauma 18% · Respiratory 12% ·
  Gastro 23% · General Medicine 27%.
- **Vitals**: normal distributions whose means depend on ESI.
- **Treatment time**: ESI-1 60–120 min … ESI-5 5–15 min.
- **Deterioration**: ~6% of ESI-2/3 patients worsen if still waiting after 10–60 min.

Columns:
```
id,age,gender,esi,heart_rate,systolic_bp,spo2,resp_rate,temp_c,pain,
chief_complaint,department,arrival_min,treatment_min,deteriorate_after_min
```

### 6b. Real data: MIMIC-IV-ED (PhysioNet)
De-identified emergency visits from Beth Israel Deaconess Medical Center (Boston). Its
`triage` table has exactly the vitals this project uses, its `acuity` column **is** the
ESI level, and `chiefcomplaint` is routed to a panel by keyword.

- **MIMIC-IV-ED Demo**: 100 patients, open access, no login.
  https://physionet.org/content/mimic-iv-ed-demo/
  Download, unzip, copy `edstays.csv.gz` and `triage.csv.gz` into
  `data/mimic-iv-ed-demo/`, then run option 6.
- **Full MIMIC-IV-ED**: hundreds of thousands of visits. Same file layout, same adapter.
  Requires a PhysioNet credentialed account and CITI ethics training.

The adapter joins `edstays` and `triage` on `stay_id`, converts °F → °C, fills missing
vitals with normal values, skips rows with no acuity, keeps the real arrival order
(via a min-heap) but re-spaces arrivals (MIMIC dates are shifted for privacy), and
assigns treatment time by ESI.

### 6c. Any other dataset (e.g. Kaggle)
Write one more adapter that outputs the column format in 6a. The simulator never changes.

---

## 7. Results (synthetic dataset, from this exact code)

**7 doctors** (Cardiology 1, Neurology 1, Trauma 1, Respiratory 1, Gastro 2, General 1).
Average wait in minutes, worst wait in brackets:

| Level | FIFO | Static priority | Priority + aging |
|---|---|---|---|
| ESI-1 | 42.4 (275) | **13.8 (66)** | 14.8 (66) |
| ESI-2 | 63.1 (766) | **18.9 (281)** | 18.9 (170) |
| ESI-3 | 40.0 (715) | 30.7 (865) | 45.4 (914) |
| ESI-4 | 50.9 (719) | 81.5 (1329) | 70.5 (915) |
| ESI-5 | 130.9 (764) | 366.9 (**2124**) | 210.0 (**963**) |

By panel, the busiest one is General Medicine (81% busy, 1 doctor):

| General Medicine | FIFO | Static priority | Priority + aging |
|---|---|---|---|
| Avg wait | 129.0 | 225.5 | 168.0 |
| Worst wait | 766 | **2124** | **963** |
| Longest queue | 26 | 42 | 36 |

What it shows:
- **FIFO** makes an ESI-1 patient wait 42 min on average and ESI-2 up to 766 min.
- **Static priority** cuts ESI-1/2 waits to ~14–19 min, but in the overloaded General
  Medicine panel an ESI-5 patient waited 2124 min (~35 h). That is **starvation**.
- **Aging** keeps ESI-1/2 fast and more than halves the worst wait (2124 → 963).
- **Segregation has a cost.** One shared pool of 5 doctors handled this load; split into
  6 panels it needs 7–8, because a free cardiologist can't help a queue in Gastro.
  That trade-off belongs in your report.

**Benchmark** (insert N, then extract N; ms; numbers differ by machine):

| N | IndexedMaxHeap | java.util.PriorityQueue | SortedArray | UnsortedArray |
|---|---|---|---|---|
| 20,000 | 8.0 | 6.9 | 9.1 | 750 |
| 50,000 | 18.6 | 17.2 | 58.9 | 6,337 |
| 200,000 | 157 | 119 | 1,018 | too slow |
| 1,000,000 | 1,071 | 1,022 | too slow | too slow |

**Update priority** of 2,000 patients in a queue of 10,000: IndexedMaxHeap ~0.5 ms vs
`PriorityQueue` remove + add ~22 ms (about 40× faster). For plain insert/extract the two
heaps are about equal. Ours wins wherever patients deteriorate.

Open the CSVs in `results/` with Excel or Google Sheets to make graphs.

---

## 8. Complexity summary

| Operation | IndexedMaxHeap | java.util.PriorityQueue | Sorted array | Unsorted array |
|---|---|---|---|---|
| insert | O(log n) | O(log n) | O(n) | O(1) |
| extractMax | O(log n) | O(log n) | O(1) | O(n) |
| peek | O(1) | O(1) | O(1) | O(n) |
| update priority | **O(log n)** | O(n) | O(n) | O(n) to find, O(1) to change |
| contains | **O(1)** | O(n) | O(n) | O(n) |

Routing a patient to a panel is O(1) (array indexed by panel number). Each event costs
O(log n). A full run is O(P log P) for P patients, plus O(W log W) per aging tick.

---


## 10. Limitations 

- The triage score is inspired by early-warning scores but is **not clinically validated**.
- No pre-emption: a critical patient waits for the panel's next free doctor. In the demo,
  a deteriorating chest-pain patient waited 63 min because the only cardiologist was busy.
- Panels do not borrow doctors from each other.
- Treatment times are drawn from ranges, not real data. Keyword routing can misplace
  vague complaints into General Medicine.

## 11. Extensions (all priority-queue based)

- **Pre-emption** for ESI-1: compare the new patient against the heap of in-progress treatments
- **Cross-panel overflow**: a free doctor takes the top patient of the busiest other panel
  (a max-heap of panels keyed by their top patient's priority)
- **d-ary heap** (e.g. 4-ary) and benchmark it against the binary heap
- **Pairing heap / Fibonacci heap** for faster decrease/increase-key
