package step03_triage;

import java.util.ArrayList;
import java.util.List;

import step01_model.Patient;

/**
 * FILE 11 — TriageScorer
 *
 * Turns a patient's ESI level and vital signs into one number: the
 * priority score. Higher score = more urgent.
 *
 *   score = ESI base score + vital-sign danger points
 *
 * ESI base:  1 -> 200   2 -> 80   3 -> 60   4 -> 40   5 -> 20
 *   (ESI-1 gets 200 so it ALWAYS beats everyone else — resuscitation
 *    cases cannot wait, even if a lower level has terrible vitals.)
 *
 * Danger points are loosely modelled on clinical early-warning scores
 * (e.g. NEWS2). They are NOT clinically validated — mention this as a
 * limitation in your report.
 */
public final class TriageScorer {

    private TriageScorer() {}

    private static final int[] ESI_BASE = {0, 200, 80, 60, 40, 20};

    public static double score(Patient p) {
        double s = ESI_BASE[p.esi];
        if (p.spo2 < 90) s += 15;                           // low oxygen
        if (p.systolicBP < 90) s += 15;                     // low blood pressure (shock)
        if (p.heartRate > 130 || p.heartRate < 40) s += 10; // dangerous heart rate
        if (p.respRate > 30 || p.respRate < 8) s += 10;     // breathing distress
        if (p.tempC > 39.5 || p.tempC < 35.0) s += 5;       // high fever / hypothermia
        if (p.age >= 65 || p.age < 5) s += 5;               // vulnerable age group
        if (p.pain >= 8) s += 3;                            // severe pain
        return s;
    }

    /** Human-readable reasons behind a score (shown in the live demo). */
    public static String explain(Patient p) {
        List<String> r = new ArrayList<>();
        r.add("ESI-" + p.esi + " base " + ESI_BASE[p.esi]);
        if (p.spo2 < 90) r.add("SpO2 " + p.spo2 + "% +15");
        if (p.systolicBP < 90) r.add("SBP " + p.systolicBP + " +15");
        if (p.heartRate > 130 || p.heartRate < 40) r.add("HR " + p.heartRate + " +10");
        if (p.respRate > 30 || p.respRate < 8) r.add("RR " + p.respRate + " +10");
        if (p.tempC > 39.5 || p.tempC < 35.0) r.add(String.format("Temp %.1fC +5", p.tempC));
        if (p.age >= 65 || p.age < 5) r.add("age " + p.age + " +5");
        if (p.pain >= 8) r.add("pain " + p.pain + " +3");
        return String.join(", ", r);
    }

    /** A waiting patient gets worse: oxygen drops, heart races, pressure falls. */
    public static void deteriorate(Patient p) {
        p.spo2 = Math.max(70, p.spo2 - 8);
        p.heartRate = Math.min(200, p.heartRate + 30);
        p.systolicBP = Math.max(60, p.systolicBP - 25);
        p.respRate = Math.min(50, p.respRate + 8);
    }
}
