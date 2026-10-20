package step05_simulation;

import java.util.ArrayList;
import java.util.List;

import step01_model.Department;
import step01_model.Doctor;
import step01_model.Event;
import step01_model.Patient;
import step02_datastructures.MinHeap;
import step03_triage.TriageScorer;

/**
 * FILE 18 — EDSimulator  (discrete-event simulation of the Emergency Department)
 *
 * Every decision in the ED is made by a priority queue:
 *
 *   events            MinHeap<Event>              — what happens next, earliest first (the clock)
 *   panel.waiting     IndexedMaxHeap (one/panel)  — that panel's patients, most urgent first
 *   panel.idle        MinHeap<Doctor> (one/panel) — that panel's free doctors, least-busy first
 *   allocateDoctors   max-heap of panels          — which panel needs the next doctor most
 *
 * Main loop:
 *   1. Take ALL events at the earliest time `now` from the event heap.
 *   2. Handle each one:
 *        ARRIVAL       -> score the patient, insert into THEIR panel's heap
 *        DETERIORATE   -> if still waiting, worsen vitals, re-score, updatePriority (increase-key)
 *        TREATMENT_END -> doctor goes back into their panel's idle heap
 *        AGING_TICK    -> recompute every waiting patient's priority
 *   3. Dispatch, panel by panel: while that panel has an idle doctor AND a
 *      waiting patient, pair its most urgent patient with its least-busy doctor.
 *
 * Handling every event at the same minute BEFORE dispatching matters: if
 * two patients arrive at minute 50, the sicker one gets the doctor even
 * if their row came second in the file.
 */
public class EDSimulator {

    /** One completed patient. */
    public record Treated(Patient patient, long startMin, long waitMin, int doctorId) {}

    private final List<Patient> patients;
    private final Strategy strategy;
    private final boolean verbose;
    private final DepartmentPanel[] panels = new DepartmentPanel[Department.values().length];
    private final Doctor[] allDoctors;

    private final MinHeap<Event> events = new MinHeap<>(Event::compare);
    private final List<Treated> treated = new ArrayList<>();
    private long eventSeq = 0, arrivalSeq = 0;
    private int maxTotalWaiting = 0;
    private long nextBoardTime = BOARD_INTERVAL;   // first board after one hour
    public static final int BOARD_INTERVAL = 60;   // live demo prints the panel boards every hour

    /** Builds the ED with `totalDoctors` split across panels by workload (see allocateDoctors). */
    public EDSimulator(List<Patient> input, int totalDoctors, Strategy strategy, boolean verbose) {
        this(input, allocateDoctors(input, totalDoctors), strategy, verbose);
    }

    /** Builds the ED with an explicit number of doctors per panel (index = Department.ordinal()). */
    public EDSimulator(List<Patient> input, int[] doctorsPerPanel, Strategy strategy, boolean verbose) {
        this.patients = new ArrayList<>(input.size());
        for (Patient p : input) patients.add(p.copy());   // never mutate the original dataset
        this.strategy = strategy;
        this.verbose = verbose;

        List<Doctor> all = new ArrayList<>();
        int nextId = 1;
        for (Department d : Department.values()) {
            Doctor[] docs = new Doctor[doctorsPerPanel[d.ordinal()]];
            for (int i = 0; i < docs.length; i++) {
                docs[i] = new Doctor(nextId++, d);
                all.add(docs[i]);
            }
            panels[d.ordinal()] = new DepartmentPanel(d, docs);
        }
        this.allDoctors = all.toArray(new Doctor[0]);
    }

    /**
     * Splits doctors across panels using a MAX-heap (greedy):
     *   1. Every panel that has patients gets 1 doctor.
     *   2. Put panels in a max-heap keyed by (workload minutes / doctors so far).
     *   3. Repeatedly extract the most overloaded panel, give it one more
     *      doctor, re-insert it with its new (lower) key.
     * Each step is O(log P) for P panels.
     */
    public static int[] allocateDoctors(List<Patient> patients, int totalDoctors) {
        int k = Department.values().length;
        long[] workload = new long[k];
        for (Patient p : patients) workload[p.department.ordinal()] += p.treatmentMin;

        int[] count = new int[k];
        int used = 0;
        for (int d = 0; d < k; d++) if (workload[d] > 0) { count[d] = 1; used++; }
        if (used > totalDoctors)
            throw new IllegalArgumentException("Need at least " + used + " doctors (one per active panel)");

        // max-heap: MinHeap with the comparison reversed
        MinHeap<Integer> neediest = new MinHeap<>((a, b) ->
                Double.compare((double) workload[b] / count[b], (double) workload[a] / count[a]));
        for (int d = 0; d < k; d++) if (count[d] > 0) neediest.insert(d);

        for (; used < totalDoctors; used++) {
            int d = neediest.extractMin();   // the most overloaded panel
            count[d]++;
            neediest.insert(d);              // re-insert with its new key
        }
        return count;
    }

    public List<Treated> run() {
        for (Patient p : patients) {
            events.insert(new Event(p.arrivalMin, Event.Type.ARRIVAL, p, null, eventSeq++));
            if (p.deteriorateAfterMin >= 0)
                events.insert(new Event(p.arrivalMin + p.deteriorateAfterMin, Event.Type.DETERIORATE, p, null, eventSeq++));
        }
        if (strategy.usesAging() && !patients.isEmpty())
            events.insert(new Event(Strategy.AGING_INTERVAL, Event.Type.AGING_TICK, null, null, eventSeq++));

        while (!events.isEmpty()) {
            long now = events.peek().time;
            while (!events.isEmpty() && events.peek().time == now) handle(events.extractMin(), now);
            for (DepartmentPanel panel : panels) dispatch(panel, now);

            int total = 0;
            for (DepartmentPanel panel : panels) {
                total += panel.waiting.size();
                panel.maxQueueLength = Math.max(panel.maxQueueLength, panel.waiting.size());
            }
            maxTotalWaiting = Math.max(maxTotalWaiting, total);

            if (verbose && now >= nextBoardTime) {
                printBoards(now);
                nextBoardTime = (now / BOARD_INTERVAL + 1) * BOARD_INTERVAL;
            }
        }
        return treated;
    }

    private DepartmentPanel panelOf(Patient p) {
        return panels[p.department.ordinal()];
    }

    private void handle(Event e, long now) {
        switch (e.type) {
            case ARRIVAL -> {
                Patient p = e.patient;
                p.seq = arrivalSeq++;
                p.baseScore = TriageScorer.score(p);
                p.priority = strategy.priority(p, now);
                DepartmentPanel panel = panelOf(p);
                panel.waiting.insert(p);
                log(now, "ARRIVE   %-7s ESI-%d %-22s -> %-18s score %5.1f [%s] waiting=%d",
                        p.id, p.esi, p.complaint, p.department.label, p.baseScore,
                        TriageScorer.explain(p), panel.waiting.size());
            }
            case DETERIORATE -> {
                Patient p = e.patient;
                DepartmentPanel panel = panelOf(p);
                if (!panel.waiting.contains(p)) return;          // already being treated — nothing to do
                double before = p.baseScore;
                TriageScorer.deteriorate(p);
                p.baseScore = TriageScorer.score(p);
                p.deteriorated = true;
                panel.waiting.updatePriority(p, strategy.priority(p, now));   // O(log n) increase-key
                log(now, "WORSENS  %-7s (%s) score %5.1f -> %5.1f  SpO2 %d%%, HR %d  -> moved up the queue",
                        p.id, p.department.label, before, p.baseScore, p.spo2, p.heartRate);
            }
            case TREATMENT_END -> {
                panelOf(e.patient).idle.insert(e.doctor);
                log(now, "DONE     %-7s by %s (%s)", e.patient.id, e.doctor, e.doctor.department.label);
            }
            case AGING_TICK -> {
                boolean anyoneWaiting = false;
                for (DepartmentPanel panel : panels) {
                    // collect first, then update: updating moves patients between slots
                    Patient[] waitingNow = new Patient[panel.waiting.size()];
                    for (int i = 0; i < waitingNow.length; i++) waitingNow[i] = panel.waiting.at(i);
                    for (Patient p : waitingNow) panel.waiting.updatePriority(p, strategy.priority(p, now));
                    anyoneWaiting |= !panel.waiting.isEmpty();
                }
                if (!events.isEmpty() || anyoneWaiting)   // stop ticking once the ED is empty
                    events.insert(new Event(now + Strategy.AGING_INTERVAL, Event.Type.AGING_TICK, null, null, eventSeq++));
            }
        }
    }

    private void dispatch(DepartmentPanel panel, long now) {
        while (!panel.idle.isEmpty() && !panel.waiting.isEmpty()) {
            Patient p = panel.waiting.extractMax();   // most urgent patient in THIS panel
            Doctor d = panel.idle.extractMin();       // least-busy free doctor in THIS panel
            d.busyMinutes += p.treatmentMin;
            d.patientsSeen++;
            long wait = now - p.arrivalMin;
            treated.add(new Treated(p, now, wait, d.id));
            events.insert(new Event(now + p.treatmentMin, Event.Type.TREATMENT_END, p, d, eventSeq++));
            log(now, "TREAT    %-7s ESI-%d by %s (%s) after waiting %d min",
                    p.id, p.esi, d, panel.department.label, wait);
        }
    }

    private void printBoards(long now) {
        System.out.printf("%n========== PANEL BOARD at %s (waiting patients, highest priority first) ==========%n",
                clock(now));
        for (DepartmentPanel panel : panels) System.out.print(panel.board(now, 5));
        System.out.println();
    }

    private void log(long now, String fmt, Object... args) {
        if (verbose) System.out.printf("[%s] " + fmt + "%n", prepend(clock(now), args));
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    /** Minutes -> "Day 1 14:05". */
    public static String clock(long min) {
        return String.format("Day %d %02d:%02d", min / 1440 + 1, (min % 1440) / 60, min % 60);
    }

    public DepartmentPanel[] panels() {
        return panels;
    }

    public Doctor[] doctors() {
        return allDoctors;
    }

    public int maxTotalWaiting() {
        return maxTotalWaiting;
    }

    public Strategy strategy() {
        return strategy;
    }
}
