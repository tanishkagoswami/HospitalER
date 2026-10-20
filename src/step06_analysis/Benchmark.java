package step06_analysis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;

import step01_model.Patient;
import step02_datastructures.IndexedMaxHeap;
import step02_datastructures.JavaPriorityQueue;
import step02_datastructures.PatientQueue;
import step02_datastructures.SortedArrayQueue;
import step02_datastructures.UnsortedArrayQueue;

/**
 * FILE 20 — Benchmark
 *
 * Experiment A: insert N patients, then extract all N, for each queue.
 *   Expected: both heaps grow ~ n log n; the array versions grow ~ n^2.
 *   Every run also CHECKS the output order is correct (non-increasing priority).
 *
 * Experiment B: with N patients waiting, change the priority of M of them
 * (patients deteriorating).
 *   IndexedMaxHeap.updatePriority      O(log n) each
 *   PriorityQueue remove(o) + add(o)   O(n) each
 *
 * Results are written to results/benchmark.csv for plotting.
 */
public final class Benchmark {

    private Benchmark() {}

    // O(n^2) structures get too slow beyond these sizes
    private static final int SORTED_LIMIT = 200_000;    // shifting uses fast System.arraycopy
    private static final int UNSORTED_LIMIT = 50_000;   // full scan on every extract

    public static void run(String csvPath) throws IOException {
        int[] sizes = {1_000, 5_000, 20_000, 50_000, 200_000, 1_000_000};
        List<Supplier<PatientQueue>> queues = List.of(
                IndexedMaxHeap::new, JavaPriorityQueue::new, SortedArrayQueue::new, UnsortedArrayQueue::new);

        List<String> csv = new ArrayList<>();
        csv.add("experiment,structure,n,millis");

        // warm-up so the JIT compiler doesn't distort the first measurement
        for (Supplier<PatientQueue> q : queues) insertExtract(q.get(), randomPatients(5_000, 1));

        System.out.println("\n=== Experiment A: insert N then extract N (milliseconds) ===");
        System.out.printf("%-10s", "N");
        for (Supplier<PatientQueue> q : queues) System.out.printf("%26s", q.get().name());
        System.out.println();

        for (int n : sizes) {
            System.out.printf("%-10d", n);
            List<Patient> data = randomPatients(n, 42);
            for (Supplier<PatientQueue> q : queues) {
                PatientQueue pq = q.get();
                if ((pq instanceof SortedArrayQueue && n > SORTED_LIMIT)
                        || (pq instanceof UnsortedArrayQueue && n > UNSORTED_LIMIT)) {
                    System.out.printf("%26s", "skipped (too slow)");
                    continue;
                }
                double ms = insertExtract(pq, data);
                System.out.printf(Locale.US, "%26.1f", ms);
                csv.add(String.format(Locale.US, "insert_extract,%s,%d,%.2f", pq.name(), n, ms));
            }
            System.out.println();
        }

        System.out.println("\n=== Experiment B: update priority of M waiting patients ===");
        int[][] cases = {{10_000, 2_000}, {50_000, 5_000}, {100_000, 5_000}};
        for (int[] c : cases) {
            int n = c[0], m = c[1];
            double ours = updateIndexed(n, m), lib = updateJava(n, m);
            System.out.printf(Locale.US, "  N=%-7d M=%-6d IndexedMaxHeap %8.1f ms   java.util.PriorityQueue %9.1f ms   (%.0fx faster)%n",
                    n, m, ours, lib, lib / Math.max(ours, 0.01));
            csv.add(String.format(Locale.US, "update_priority,IndexedMaxHeap,%d,%.2f", n, ours));
            csv.add(String.format(Locale.US, "update_priority,java.util.PriorityQueue,%d,%.2f", n, lib));
        }

        Path out = Path.of(csvPath);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            csv.forEach(w::println);
        }
        System.out.println("\nSaved: " + csvPath);
    }

    /** Fresh copies each time, because extract order depends on priority/seq only. */
    private static List<Patient> randomPatients(int n, long seed) {
        Random r = new Random(seed);
        List<Patient> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(Patient.forBenchmark(i, 20 + r.nextInt(200)));  // many ties, like real triage
        return list;
    }

    private static double insertExtract(PatientQueue q, List<Patient> data) {
        long t0 = System.nanoTime();
        for (Patient p : data) q.insert(p);
        Patient prev = null;
        while (!q.isEmpty()) {
            Patient p = q.extractMax();
            if (prev != null && PatientQueue.higher(p, prev))
                throw new IllegalStateException(q.name() + " returned patients in the wrong order!");
            prev = p;
        }
        return (System.nanoTime() - t0) / 1e6;
    }

    private static double updateIndexed(int n, int m) {
        IndexedMaxHeap h = new IndexedMaxHeap();
        List<Patient> data = randomPatients(n, 7);
        data.forEach(h::insert);
        Random r = new Random(9);
        long t0 = System.nanoTime();
        for (int i = 0; i < m; i++) {
            Patient p = data.get(r.nextInt(n));
            h.updatePriority(p, p.priority + 15);
        }
        return (System.nanoTime() - t0) / 1e6;
    }

    private static double updateJava(int n, int m) {
        JavaPriorityQueue q = new JavaPriorityQueue();
        List<Patient> data = randomPatients(n, 7);
        data.forEach(q::insert);
        Random r = new Random(9);
        long t0 = System.nanoTime();
        for (int i = 0; i < m; i++) {
            Patient p = data.get(r.nextInt(n));
            q.updatePriority(p, p.priority + 15);
        }
        return (System.nanoTime() - t0) / 1e6;
    }
}
