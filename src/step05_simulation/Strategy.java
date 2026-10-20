package step05_simulation;

import step01_model.Patient;

/**
 * FILE 16 — Strategy
 *
 * How the waiting queue decides who goes next. The SAME heap is used for
 * all three; only the priority key changes. That is the point of the
 * experiment: same data structure, different keys, very different outcomes.
 *
 *  FIFO             key = -arrivalTime          (first come, first served — the baseline;
 *                                                 a FIFO queue is just a priority queue keyed by arrival)
 *  STATIC_PRIORITY  key = triage score          (sickest first — but low-ESI patients can starve)
 *  PRIORITY_AGING   key = triage score + aging  (sickest first, and waiting slowly raises priority)
 *
 * Aging bonus = AGING_RATE x minutesWaited, but a patient can only climb up
 * to a CEILING:
 *   ESI-2      can climb up to 199  (still always below ESI-1 = 200)
 *   ESI-3..5   can climb up to 79   (never above a newly arrived ESI-2 = 80)
 * So waiting long enough lets a stable ESI-5 pass newly arrived ESI-3/4
 * patients (no starvation), but no amount of waiting lets a minor case jump
 * ahead of a high-risk one.
 */
public enum Strategy {

    FIFO {
        @Override
        public double priority(Patient p, long now) {
            return -p.arrivalMin;
        }
    },

    STATIC_PRIORITY {
        @Override
        public double priority(Patient p, long now) {
            return p.baseScore;
        }
    },

    PRIORITY_AGING {
        @Override
        public double priority(Patient p, long now) {
            double waited = now - p.arrivalMin;
            double ceiling = p.esi == 2 ? 199 : 79;
            double bonus = Math.min(AGING_RATE * waited, Math.max(0, ceiling - p.baseScore));
            return p.baseScore + bonus;
        }

        @Override
        public boolean usesAging() {
            return true;
        }
    };

    /** Points gained per minute of waiting (ESI-5 reaches its ceiling after ~4 hours). */
    static final double AGING_RATE = 0.25;
    /** How often (in simulated minutes) aging priorities are recomputed. */
    public static final int AGING_INTERVAL = 5;

    public abstract double priority(Patient p, long now);

    public boolean usesAging() {
        return false;
    }
}
