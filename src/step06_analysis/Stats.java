package step06_analysis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import step01_model.Department;
import step01_model.Doctor;
import step02_datastructures.MinHeap;
import step05_simulation.DepartmentPanel;
import step05_simulation.EDSimulator;
import step05_simulation.EDSimulator.Treated;

/**
 * FILE 19 — Stats
 *
 * Turns a simulation run into numbers for the report, grouped two ways:
 *   - by ESI level        (does the sickest patient get seen first?)
 *   - by disease panel    (is each panel coping with its load?)
 * For each group: count, average / median / 90th-percentile / max wait,
 * and % of patients seen within the target time.
 *
 * Median and percentiles need the waits in sorted order. That is done with
 * a MinHeap too (insert all, extract in ascending order = heap sort), so the
 * project never relies on a library sort.
 *
 * Target times follow the style of triage scales such as CTAS:
 *   ESI-1 immediately, ESI-2 within 15 min, ESI-3 30 min, ESI-4 60 min, ESI-5 120 min.
 */
public final class Stats {

    private Stats() {}

    public static final int[] TARGET_MIN = {0, 0, 15, 30, 60, 120};
    public static final String CSV_HEADER =
            "strategy,doctors,group_type,group,patients,avg_wait,median_wait,p90_wait,max_wait,pct_within_target";

    /** Summary of one group of patients. */
    public record Row(String group, int n, double avg, long median, long p90, long max, double pctWithin) {}

    /** Heap sort of the wait times, then the summary numbers. */
    private static Row compute(String group, List<Treated> members) {
        if (members.isEmpty()) return new Row(group, 0, 0, 0, 0, 0, 0);
        MinHeap<Long> heap = new MinHeap<>(Long::compare);
        int within = 0;
        double sum = 0;
        for (Treated t : members) {
            heap.insert(t.waitMin());
            sum += t.waitMin();
            if (t.waitMin() <= TARGET_MIN[t.patient().esi]) within++;
        }
        long[] sorted = new long[members.size()];
        for (int i = 0; i < sorted.length; i++) sorted[i] = heap.extractMin();
        int n = sorted.length;
        int p90 = (int) Math.min(n - 1, Math.ceil(0.9 * n) - 1);
        return new Row(group, n, sum / n, sorted[n / 2], sorted[p90], sorted[n - 1], 100.0 * within / n);
    }

    public static List<Row> byEsi(List<Treated> treated) {
        List<Row> rows = new ArrayList<>();
        rows.add(compute("ALL", treated));
        for (int esi = 1; esi <= 5; esi++) {
            List<Treated> m = new ArrayList<>();
            for (Treated t : treated) if (t.patient().esi == esi) m.add(t);
            rows.add(compute("ESI-" + esi, m));
        }
        return rows;
    }

    public static List<Row> byDepartment(List<Treated> treated) {
        List<Row> rows = new ArrayList<>();
        for (Department d : Department.values()) {
            List<Treated> m = new ArrayList<>();
            for (Treated t : treated) if (t.patient().department == d) m.add(t);
            rows.add(compute(d.label, m));
        }
        return rows;
    }

    public static void print(EDSimulator sim, List<Treated> treated) {
        System.out.printf("%n=== Strategy: %s | Doctors: %d | Patients treated: %d | Most waiting at once: %d ===%n",
                sim.strategy(), sim.doctors().length, treated.size(), sim.maxTotalWaiting());

        System.out.println("  By ESI level         Count   AvgWait  Median    P90     Max  %WithinTarget");
        for (Row r : byEsi(treated))
            System.out.printf(Locale.US, "  %-19s %6d %8.1f %7d %7d %7d %10.1f%%%n",
                    r.group(), r.n(), r.avg(), r.median(), r.p90(), r.max(), r.pctWithin());

        long span = 1;
        for (Treated t : treated) span = Math.max(span, t.startMin() + t.patient().treatmentMin);

        System.out.println("  By disease panel     Count   AvgWait  Median    P90     Max  %WithinTarget  Doctors  Busy  MaxQueue");
        List<Row> dept = byDepartment(treated);
        DepartmentPanel[] panels = sim.panels();
        for (int i = 0; i < panels.length; i++) {
            Row r = dept.get(i);
            long busy = 0;
            for (Doctor d : panels[i].doctors) busy += d.busyMinutes;
            double util = panels[i].doctors.length == 0 ? 0 : 100.0 * busy / (span * panels[i].doctors.length);
            System.out.printf(Locale.US, "  %-19s %6d %8.1f %7d %7d %7d %10.1f%% %9d %5.0f%% %8d%n",
                    r.group(), r.n(), r.avg(), r.median(), r.p90(), r.max(), r.pctWithin(),
                    panels[i].doctors.length, util, panels[i].maxQueueLength);
        }

        // patients who deteriorated while waiting — did the increase-key help them?
        long n = 0, sum = 0;
        for (Treated t : treated) if (t.patient().deteriorated) { n++; sum += t.waitMin(); }
        if (n > 0) System.out.printf(Locale.US, "  Deteriorated while waiting: %d patient(s), avg wait %.1f min%n", n, (double) sum / n);
    }

    public static void appendCsv(String path, String strategy, int doctors, List<Treated> treated) throws IOException {
        Path out = Path.of(path);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        boolean fresh = !Files.exists(out);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            if (fresh) w.println(CSV_HEADER);
            for (Row r : byEsi(treated)) write(w, strategy, doctors, "esi", r);
            for (Row r : byDepartment(treated)) write(w, strategy, doctors, "panel", r);
        }
    }

    private static void write(PrintWriter w, String strategy, int doctors, String type, Row r) {
        w.printf(Locale.US, "%s,%d,%s,%s,%d,%.2f,%d,%d,%d,%.2f%n", strategy, doctors, type, r.group(),
                r.n(), r.avg(), r.median(), r.p90(), r.max(), r.pctWithin());
    }
}
