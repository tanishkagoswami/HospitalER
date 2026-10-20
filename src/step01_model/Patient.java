package step01_model;

/**
 * FILE 1 — Patient
 *
 * One patient arriving at the Emergency Department (ED).
 * Fields mirror what a real triage nurse records (same vitals as the
 * MIMIC-IV-ED "triage" table), plus fields the simulator needs.
 */
public class Patient {

    // ---------- Identity and triage data (from the dataset) ----------
    public final String id;
    public final int age;
    public final String gender;
    public final int esi;              // Emergency Severity Index: 1 = most critical ... 5 = least
    public final String complaint;     // chief complaint, e.g. "chest pain"
    public Department department;      // disease-wise panel (routed from the complaint)

    // Vitals are NOT final: a waiting patient can deteriorate
    public int heartRate;              // beats per minute
    public int systolicBP;             // mmHg
    public int spo2;                   // oxygen saturation %
    public int respRate;               // breaths per minute
    public double tempC;               // body temperature, Celsius
    public int pain;                   // 0-10 pain scale

    // ---------- Timing (from the dataset) ----------
    public final long arrivalMin;          // minutes since simulation start
    public final int treatmentMin;         // how long a doctor needs for this patient
    public final int deteriorateAfterMin;  // -1 = never; else gets worse after this many minutes of waiting

    // ---------- Filled in by the system ----------
    public long seq;            // arrival order, used to break ties (FIFO among equal priority)
    public double baseScore;    // triage score from TriageScorer
    public double priority;     // the key the priority queue orders by
    public int heapIndex = -1;  // current slot in IndexedMaxHeap's array (-1 = not waiting in a heap)
    public boolean deteriorated;// true if this patient got worse while waiting

    public Patient(String id, int age, String gender, int esi, String complaint,
                   int heartRate, int systolicBP, int spo2, int respRate, double tempC, int pain,
                   long arrivalMin, int treatmentMin, int deteriorateAfterMin) {
        this.id = id;
        this.age = age;
        this.gender = gender;
        this.esi = esi;
        this.complaint = complaint;
        this.department = Department.route(complaint);
        this.heartRate = heartRate;
        this.systolicBP = systolicBP;
        this.spo2 = spo2;
        this.respRate = respRate;
        this.tempC = tempC;
        this.pain = pain;
        this.arrivalMin = arrivalMin;
        this.treatmentMin = treatmentMin;
        this.deteriorateAfterMin = deteriorateAfterMin;
    }

    /** Fresh copy, so each simulation run starts from the original data. */
    public Patient copy() {
        Patient c = new Patient(id, age, gender, esi, complaint, heartRate, systolicBP, spo2,
                respRate, tempC, pain, arrivalMin, treatmentMin, deteriorateAfterMin);
        c.department = department;
        return c;
    }

    /** Minimal patient used only by the benchmark (priority is all that matters there). */
    public static Patient forBenchmark(int i, double priority) {
        Patient p = new Patient("B" + i, 40, "U", 3, "-", 80, 120, 98, 16, 37.0, 0, i, 0, -1);
        p.seq = i;
        p.priority = priority;
        return p;
    }

    @Override
    public String toString() {
        return String.format("%s(ESI-%d, %s, %s, score=%.1f)", id, esi, complaint, department.label, priority);
    }
}
