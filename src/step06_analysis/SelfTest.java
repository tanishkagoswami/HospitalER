package step06_analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import step01_model.Department;
import step01_model.Patient;
import step02_datastructures.IndexedMaxHeap;
import step02_datastructures.MinHeap;
import step02_datastructures.PatientQueue;
import step02_datastructures.SortedArrayQueue;
import step02_datastructures.UnsortedArrayQueue;
import step04_data.DataGenerator;
import step05_simulation.EDSimulator;

/**
 * FILE 21 — SelfTest
 *
 * Proves the data structures are correct before trusting any results.
 * Run this first (menu option 1). Each test prints PASS or FAIL.
 */
public final class SelfTest {

    private SelfTest() {}

    private static int passed = 0, failed = 0;

    public static boolean run() {
        passed = failed = 0;
        System.out.println("\n=== Self-test ===");
        testExtractOrder();
        testTiesAreFifo();
        testUpdatePriority();
        testAllQueuesAgree();
        testMinHeap();
        testDepartmentRouting();
        testDoctorAllocation();
        System.out.printf("Result: %d passed, %d failed%n", passed, failed);
        return failed == 0;
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (ok) passed++; else failed++;
    }

    private static List<Patient> randomPatients(int n, long seed) {
        Random r = new Random(seed);
        List<Patient> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(Patient.forBenchmark(i, r.nextInt(100)));
        return list;
    }

    /** Extracted priorities must never increase. */
    private static void testExtractOrder() {
        IndexedMaxHeap h = new IndexedMaxHeap();
        randomPatients(10_000, 1).forEach(h::insert);
        boolean ok = h.isValid();
        double prev = Double.MAX_VALUE;
        while (!h.isEmpty()) {
            double p = h.extractMax().priority;
            if (p > prev) ok = false;
            prev = p;
        }
        check("IndexedMaxHeap extracts 10,000 patients in priority order", ok);
    }

    /** Two patients with equal priority: the earlier arrival must come out first. */
    private static void testTiesAreFifo() {
        IndexedMaxHeap h = new IndexedMaxHeap();
        for (int i = 0; i < 50; i++) h.insert(Patient.forBenchmark(i, 60));   // all equal
        boolean ok = true;
        for (int i = 0; i < 50; i++) if (h.extractMax().seq != i) ok = false;
        check("Equal priorities are served first-come-first-served", ok);
    }

    /** Random increase/decrease-key operations keep the heap valid. */
    private static void testUpdatePriority() {
        IndexedMaxHeap h = new IndexedMaxHeap();
        List<Patient> ps = randomPatients(2_000, 2);
        ps.forEach(h::insert);
        Random r = new Random(3);
        boolean ok = true;
        for (int i = 0; i < 5_000; i++) {
            Patient p = ps.get(r.nextInt(ps.size()));
            h.updatePriority(p, r.nextInt(150));
            if (i % 500 == 0 && !h.isValid()) ok = false;
        }
        ok &= h.isValid();
        Patient target = ps.get(123);
        h.updatePriority(target, 1_000);
        ok &= h.peek() == target;
        check("updatePriority keeps heap valid; boosted patient reaches the top", ok);
    }

    /** All four structures must produce exactly the same order. */
    private static void testAllQueuesAgree() {
        List<Patient> data = randomPatients(3_000, 4);
        PatientQueue[] qs = {new IndexedMaxHeap(), new step02_datastructures.JavaPriorityQueue(),
                new SortedArrayQueue(), new UnsortedArrayQueue()};
        List<List<String>> orders = new ArrayList<>();
        for (PatientQueue q : qs) {
            data.forEach(q::insert);
            List<String> order = new ArrayList<>();
            while (!q.isEmpty()) order.add(q.extractMax().id);
            orders.add(order);
        }
        boolean ok = true;
        for (List<String> o : orders) ok &= o.equals(orders.get(0));
        check("Heap, java.util.PriorityQueue, sorted array and unsorted array agree", ok);
    }

    private static void testMinHeap() {
        MinHeap<Integer> h = new MinHeap<>(Comparator.<Integer>naturalOrder());
        Random r = new Random(5);
        for (int i = 0; i < 5_000; i++) h.insert(r.nextInt(1_000_000));
        boolean ok = true;
        int prev = Integer.MIN_VALUE;
        while (!h.isEmpty()) {
            int x = h.extractMin();
            if (x < prev) ok = false;
            prev = x;
        }
        check("MinHeap (event queue / doctor queue) extracts in ascending order", ok);
    }

    /** Complaints land in the right disease-wise panel (including MIMIC-style free text). */
    private static void testDepartmentRouting() {
        boolean ok = Department.route("chest pain") == Department.CARDIOLOGY
                && Department.route("Chest pain, Dyspnea") == Department.CARDIOLOGY
                && Department.route("stroke symptoms") == Department.NEUROLOGY
                && Department.route("fracture") == Department.TRAUMA_ORTHO
                && Department.route("severe asthma") == Department.RESPIRATORY
                && Department.route("Abd pain") == Department.GASTRO_ABDOMINAL
                && Department.route("rash") == Department.GENERAL_MEDICINE;
        check("Complaints are routed to the correct disease-wise panel", ok);
    }

    /** Max-heap doctor allocation uses every doctor and gives each busy panel at least one. */
    private static void testDoctorAllocation() {
        List<Patient> ps = DataGenerator.generate(2_000, 11, 6.5);
        int[] alloc = EDSimulator.allocateDoctors(ps, 12);
        int total = 0;
        boolean ok = true;
        for (int a : alloc) { total += a; if (a < 1) ok = false; }
        check("Doctor allocation (max-heap greedy) uses all 12 doctors, at least 1 per panel", ok && total == 12);
    }
}
