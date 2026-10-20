package step02_datastructures;

import java.util.ArrayList;

import step01_model.Patient;

/**
 * FILE 9 — UnsortedArrayQueue  (naive baseline #2, for comparison only)
 *
 *   insert      O(1)  — append to the end
 *   extractMax  O(n)  — scan everyone to find the most urgent
 */
public class UnsortedArrayQueue implements PatientQueue {

    private final ArrayList<Patient> list = new ArrayList<>();

    @Override
    public void insert(Patient p) {
        list.add(p);
    }

    @Override
    public Patient extractMax() {
        if (list.isEmpty()) return null;
        int best = bestIndex();
        Patient p = list.get(best);
        int last = list.size() - 1;
        list.set(best, list.get(last));   // swap-with-last, then O(1) removal
        list.remove(last);
        return p;
    }

    @Override
    public Patient peek() {
        return list.isEmpty() ? null : list.get(bestIndex());
    }

    private int bestIndex() {
        int best = 0;
        for (int i = 1; i < list.size(); i++)
            if (PatientQueue.higher(list.get(i), list.get(best))) best = i;
        return best;
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public String name() {
        return "UnsortedArray";
    }
}
