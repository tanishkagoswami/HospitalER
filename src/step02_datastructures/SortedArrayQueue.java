package step02_datastructures;

import java.util.ArrayList;

import step01_model.Patient;

/**
 * FILE 8 — SortedArrayQueue  (naive baseline #1, for comparison only)
 *
 * Keeps patients sorted from LEAST to MOST urgent, so the most urgent is
 * always at the end.
 *   insert      O(n)  — binary search finds the spot in O(log n), but shifting
 *                       elements to make room costs O(n)
 *   extractMax  O(1)  — remove the last element
 */
public class SortedArrayQueue implements PatientQueue {

    private final ArrayList<Patient> list = new ArrayList<>();

    @Override
    public void insert(Patient p) {
        // find the first index whose patient is MORE urgent than p
        int lo = 0, hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (PatientQueue.higher(list.get(mid), p)) hi = mid;
            else lo = mid + 1;
        }
        list.add(lo, p);      // O(n) shift
    }

    @Override
    public Patient extractMax() {
        return list.isEmpty() ? null : list.remove(list.size() - 1);
    }

    @Override
    public Patient peek() {
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public String name() {
        return "SortedArray";
    }
}
