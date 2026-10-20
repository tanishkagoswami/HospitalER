package step01_model;

/**
 * FILE 3 — Event
 *
 * The simulator is "event-driven": instead of ticking every minute, it jumps
 * straight to the next interesting moment. All future events live in a
 * MIN-heap ordered by time (earliest event first).
 *
 * Order of the enum matters: at the same minute, a doctor finishing
 * (TREATMENT_END) is processed before new arrivals, so the freed doctor
 * is available for them.
 */
public class Event {

    public enum Type { TREATMENT_END, DETERIORATE, ARRIVAL, AGING_TICK }

    public final long time;
    public final Type type;
    public final Patient patient;   // null for AGING_TICK
    public final Doctor doctor;     // only for TREATMENT_END
    public final long seq;          // tie-breaker for equal time and type

    public Event(long time, Type type, Patient patient, Doctor doctor, long seq) {
        this.time = time;
        this.type = type;
        this.patient = patient;
        this.doctor = doctor;
        this.seq = seq;
    }

    /** Comparator logic for the event min-heap. */
    public static int compare(Event a, Event b) {
        if (a.time != b.time) return Long.compare(a.time, b.time);
        if (a.type != b.type) return a.type.compareTo(b.type);
        return Long.compare(a.seq, b.seq);
    }
}
