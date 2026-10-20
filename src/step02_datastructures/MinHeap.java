package step02_datastructures;

import java.util.Arrays;
import java.util.Comparator;

/**
 * FILE 7 — MinHeap<T>  (generic)
 *
 * A second hand-written heap. The ordering comes from a Comparator, so one
 * class serves every other priority queue in the project:
 *   1. EVENT queue          — earliest event first (drives the simulation clock)
 *   2. IDLE-DOCTOR queues   — least-busy doctor first, one per panel
 *   3. DOCTOR ALLOCATION    — most overloaded panel first (reversed comparator)
 *   4. PANEL BOARD view     — waiting patients, most urgent first (reversed comparator)
 *   5. STATS                — wait times in ascending order (heap sort for median / P90)
 *   6. MIMIC LOADER         — real visits in arrival-time order
 *
 * A max-heap is just a min-heap with the comparison reversed:
 *     new MinHeap<>((a, b) -> compare(b, a))
 *
 * Same sift-up / sift-down idea as IndexedMaxHeap.
 */
public class MinHeap<T> {

    private Object[] heap = new Object[16];
    private int size = 0;
    private final Comparator<T> cmp;

    public MinHeap(Comparator<T> cmp) {
        this.cmp = cmp;
    }

    public void insert(T x) {
        if (size == heap.length) heap = Arrays.copyOf(heap, size * 2);
        heap[size] = x;
        int i = size++;
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (cmp.compare(get(i), get(parent)) >= 0) break;
            swap(i, parent);
            i = parent;
        }
    }

    public T extractMin() {
        if (size == 0) return null;
        T top = get(0);
        heap[0] = heap[--size];
        heap[size] = null;
        int i = 0;
        while (true) {
            int l = 2 * i + 1, r = 2 * i + 2, best = i;
            if (l < size && cmp.compare(get(l), get(best)) < 0) best = l;
            if (r < size && cmp.compare(get(r), get(best)) < 0) best = r;
            if (best == i) break;
            swap(i, best);
            i = best;
        }
        return top;
    }

    public T peek() {
        return size == 0 ? null : get(0);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @SuppressWarnings("unchecked")
    private T get(int i) {
        return (T) heap[i];
    }

    private void swap(int a, int b) {
        Object t = heap[a];
        heap[a] = heap[b];
        heap[b] = t;
    }
}
