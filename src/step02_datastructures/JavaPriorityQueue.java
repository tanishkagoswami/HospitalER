package step02_datastructures;

import java.util.PriorityQueue;

import step01_model.Patient;

/**
 * FILE 10 — JavaPriorityQueue  (library baseline, for comparison only)
 *
 * Wraps java.util.PriorityQueue with the same ordering rule.
 * It is also a binary heap, so insert/extract are O(log n) — but it has
 * no index map, so changing one patient's priority needs remove(o) + add(o),
 * and remove(o) is a linear search: O(n). The benchmark measures that gap.
 */
public class JavaPriorityQueue implements PatientQueue {

    private final PriorityQueue<Patient> pq = new PriorityQueue<>((a, b) -> {
        if (PatientQueue.higher(a, b)) return -1;
        if (PatientQueue.higher(b, a)) return 1;
        return 0;
    });

    @Override
    public void insert(Patient p) {
        pq.add(p);
    }

    @Override
    public Patient extractMax() {
        return pq.poll();
    }

    @Override
    public Patient peek() {
        return pq.peek();
    }

    /** The only way to "update" a key in java.util.PriorityQueue. */
    public void updatePriority(Patient p, double newPriority) {
        pq.remove(p);          // O(n)
        p.priority = newPriority;
        pq.add(p);             // O(log n)
    }

    @Override
    public int size() {
        return pq.size();
    }

    @Override
    public String name() {
        return "java.util.PriorityQueue";
    }
}
