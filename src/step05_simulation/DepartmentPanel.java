package step05_simulation;

import step01_model.Department;
import step01_model.Doctor;
import step01_model.Patient;
import step02_datastructures.IndexedMaxHeap;
import step02_datastructures.MinHeap;
import step02_datastructures.PatientQueue;

/**
 * FILE 17 — DepartmentPanel  (one disease-wise panel)
 *
 * Each panel owns TWO priority queues:
 *
 *   waiting  IndexedMaxHeap   — this panel's patients, most urgent first
 *   idle     MinHeap<Doctor>  — this panel's free doctors, least-busy first
 *
 * So the ED is segregated disease-wise, and INSIDE every panel the order is
 * decided strictly by priority score.
 */
public class DepartmentPanel {

    public final Department department;
    public final Doctor[] doctors;
    public final IndexedMaxHeap waiting = new IndexedMaxHeap();
    public final MinHeap<Doctor> idle = new MinHeap<>((a, b) ->
            a.busyMinutes != b.busyMinutes ? Long.compare(a.busyMinutes, b.busyMinutes) : Integer.compare(a.id, b.id));

    public int maxQueueLength = 0;

    public DepartmentPanel(Department department, Doctor[] doctors) {
        this.department = department;
        this.doctors = doctors;
        for (Doctor d : doctors) idle.insert(d);
    }

    /**
     * Text "board" of this panel: who is waiting, in priority order.
     *
     * Done WITHOUT disturbing the real queue: every waiting patient is copied
     * into a temporary max-heap (a MinHeap with the comparison reversed) and
     * the top `limit` are extracted — a heap-sort of the waiting room.
     */
    public String board(long now, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  +-- %-19s doctors free %d/%d | waiting %d%n",
                department.label, idle.size(), doctors.length, waiting.size()));

        MinHeap<Patient> view = new MinHeap<>((a, b) ->
                PatientQueue.higher(a, b) ? -1 : PatientQueue.higher(b, a) ? 1 : 0);
        for (int i = 0; i < waiting.size(); i++) view.insert(waiting.at(i));

        int rank = 1;
        while (!view.isEmpty() && rank <= limit) {
            Patient p = view.extractMin();   // most urgent first (comparator is reversed)
            sb.append(String.format("  |   %d. %-7s ESI-%d  priority %6.1f  %-22s waited %d min%s%n",
                    rank++, p.id, p.esi, p.priority, p.complaint, now - p.arrivalMin,
                    p.deteriorated ? "  (WORSENED)" : ""));
        }
        if (view.size() > 0) sb.append(String.format("  |   ... and %d more%n", view.size()));
        if (waiting.isEmpty()) sb.append("  |   (no one waiting)\n");
        return sb.toString();
    }
}
