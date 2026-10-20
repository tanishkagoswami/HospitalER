package step02_datastructures;

import java.util.Arrays;

import step01_model.Patient;

/**
 * FILE 6 — IndexedMaxHeap  (the heart of the project)
 *
 * A binary MAX-heap stored in a plain array. Every waiting patient also
 * remembers its own slot in that array (Patient.heapIndex) — that is what
 * makes it "indexed".
 *
 * Array layout for the node at index i:
 *     parent = (i - 1) / 2      left child = 2i + 1      right child = 2i + 2
 *
 * Example (priority scores):            array: [100, 80, 63, 40, 60, 20]
 *
 *                 100
 *               /     \
 *             80       63
 *            /  \     /
 *          40    60  20
 *
 * Why indexed?  When a waiting patient deteriorates we must RAISE their
 * priority (increase-key). A plain heap such as java.util.PriorityQueue
 * first has to SEARCH the array for that patient: O(n). Here the patient
 * already knows its slot, so we jump there in O(1) and sift up in O(log n).
 *
 *   insert          O(log n)
 *   extractMax      O(log n)
 *   peek            O(1)
 *   updatePriority  O(log n)
 *   contains        O(1)
 *
 * Rule: a patient can be waiting in only ONE IndexedMaxHeap at a time
 * (true in the ED — a patient is in exactly one panel's queue).
 */
public class IndexedMaxHeap implements PatientQueue {

    private Patient[] heap = new Patient[16];
    private int size = 0;

    @Override
    public void insert(Patient p) {
        if (p.heapIndex != -1) throw new IllegalArgumentException(p.id + " is already waiting in a heap");
        if (size == heap.length) heap = Arrays.copyOf(heap, size * 2);   // dynamic growth (amortised O(1))
        heap[size] = p;
        p.heapIndex = size;
        size++;
        siftUp(size - 1);
    }

    @Override
    public Patient extractMax() {
        if (size == 0) return null;
        Patient top = heap[0];
        size--;
        swap(0, size);          // move the last element to the root
        heap[size] = null;      // clear the old slot
        top.heapIndex = -1;     // top is no longer waiting
        if (size > 0) siftDown(0);
        return top;
    }

    @Override
    public Patient peek() {
        return size == 0 ? null : heap[0];
    }

    /** Change a waiting patient's priority: increase-key sifts up, decrease-key sifts down. */
    public void updatePriority(Patient p, double newPriority) {
        if (!contains(p)) return;             // patient is no longer waiting
        int i = p.heapIndex;
        double old = p.priority;
        p.priority = newPriority;
        if (newPriority > old) siftUp(i);
        else siftDown(i);
    }

    /** O(1): is this patient currently waiting in THIS heap? */
    public boolean contains(Patient p) {
        int i = p.heapIndex;
        return i >= 0 && i < size && heap[i] == p;
    }

    /** Read-only access to slot i (0 .. size-1), used to visit every waiting patient. */
    public Patient at(int i) {
        return heap[i];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public String name() {
        return "IndexedMaxHeap (ours)";
    }

    // ------------------------------------------------------------------
    // Heap helpers
    // ------------------------------------------------------------------

    /** Move node i up while it is more urgent than its parent. */
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (!PatientQueue.higher(heap[i], heap[parent])) break;
            swap(i, parent);
            i = parent;
        }
    }

    /** Move node i down while one of its children is more urgent. */
    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1, right = 2 * i + 2, best = i;
            if (left < size && PatientQueue.higher(heap[left], heap[best])) best = left;
            if (right < size && PatientQueue.higher(heap[right], heap[best])) best = right;
            if (best == i) return;
            swap(i, best);
            i = best;
        }
    }

    /** Swap two slots AND keep each patient's stored slot in sync — forgetting this is the classic bug. */
    private void swap(int a, int b) {
        Patient t = heap[a];
        heap[a] = heap[b];
        heap[b] = t;
        if (heap[a] != null) heap[a].heapIndex = a;
        if (heap[b] != null) heap[b].heapIndex = b;
    }

    /** Checks the heap property and every stored index; used by the self-test. */
    public boolean isValid() {
        for (int i = 0; i < size; i++) {
            int l = 2 * i + 1, r = 2 * i + 2;
            if (l < size && PatientQueue.higher(heap[l], heap[i])) return false;
            if (r < size && PatientQueue.higher(heap[r], heap[i])) return false;
            if (heap[i].heapIndex != i) return false;
        }
        return true;
    }
}
