package step01_model;

/**
 * FILE 2 — Doctor
 *
 * A doctor who treats one patient at a time, assigned to ONE panel.
 * Each panel keeps its idle doctors in a MIN-heap ordered by busyMinutes,
 * so the least-worked idle doctor in that panel gets the next patient
 * (simple load balancing — also a priority queue).
 */
public class Doctor {
    public final int id;
    public final Department department;
    public long busyMinutes = 0;   // total minutes spent treating
    public int patientsSeen = 0;

    public Doctor(int id, Department department) {
        this.id = id;
        this.department = department;
    }

    @Override
    public String toString() {
        return "Dr-" + id;
    }
}
