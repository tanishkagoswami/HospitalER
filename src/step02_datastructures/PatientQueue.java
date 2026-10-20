package step02_datastructures;

import step01_model.Patient;

/**
 * FILE 5 — PatientQueue (interface)
 *
 * Every priority-queue implementation in this project implements this
 * interface, so the benchmark can compare them on identical workloads:
 *
 *   IndexedMaxHeap      insert O(log n)  extractMax O(log n)  update O(log n)   <- our main structure
 *   SortedArrayQueue    insert O(n)      extractMax O(1)
 *   UnsortedArrayQueue  insert O(1)      extractMax O(n)
 *   JavaPriorityQueue   insert O(log n)  extractMax O(log n)  update O(n)       <- java.util baseline
 */
public interface PatientQueue {

    void insert(Patient p);

    Patient extractMax();     // remove and return the most urgent patient (null if empty)

    Patient peek();

    int size();

    String name();

    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * The single ordering rule used everywhere:
     * higher priority wins; on a tie, whoever arrived first (smaller seq) wins.
     * The tie rule makes the queue "stable" — equal patients are served FIFO.
     */
    static boolean higher(Patient a, Patient b) {
        if (a.priority != b.priority) return a.priority > b.priority;
        return a.seq < b.seq;
    }
}
