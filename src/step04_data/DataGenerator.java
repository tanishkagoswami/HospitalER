package step04_data;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import step01_model.Patient;

/**
 * FILE 13 — DataGenerator  (synthetic dataset)
 *
 * Generates a realistic ED patient dataset with a fixed random seed, so
 * every run (and every classmate's run) produces the same file.
 *
 * How realism is built in:
 *  1. ARRIVALS follow a Poisson process whose rate changes by hour of day
 *     (quiet at 4 am, busiest around 4 pm). Generated with "thinning":
 *     draw gaps at the peak rate, keep each arrival with probability
 *     rate(t) / peakRate.
 *  2. ESI MIX approximates a general ED: about 1.5% / 12% / 43% / 30% / 13.5%
 *     for levels 1..5.
 *  3. VITALS are drawn from normal distributions whose means depend on the
 *     ESI level (critical patients have low SpO2, high heart rate, ...).
 *  4. TREATMENT TIME grows with severity.
 *  5. About 6% of ESI-2/3 patients DETERIORATE if left waiting too long.
 *  6. Each complaint maps to a disease-wise panel (Department.route), written
 *     to the "department" column.
 */
public final class DataGenerator {

    private DataGenerator() {}

    public static final String HEADER =
            "id,age,gender,esi,heart_rate,systolic_bp,spo2,resp_rate,temp_c,pain,"
            + "chief_complaint,department,arrival_min,treatment_min,deteriorate_after_min";

    private static final double[] ESI_CUMULATIVE = {0.015, 0.135, 0.565, 0.865, 1.0};

    // per ESI level 1..5: {heart rate, systolic BP, SpO2, resp rate, temp C} means and std devs
    private static final double[][] MEAN = {
            {}, {125, 88, 86, 28, 37.6}, {105, 122, 93, 22, 37.8},
            {90, 132, 97, 18, 37.2}, {82, 130, 98, 16, 36.9}, {78, 126, 98.5, 15, 36.8}};
    private static final double[][] SD = {
            {}, {25, 22, 6, 6, 1.3}, {20, 25, 4, 4, 1.0},
            {15, 20, 2, 3, 0.7}, {12, 16, 1.5, 2, 0.5}, {10, 14, 1, 2, 0.4}};

    // complaints per ESI level; the panel each one routes to is shown in brackets
    private static final String[][] COMPLAINTS = {
            {},
            {"cardiac arrest", "major trauma", "unresponsive",            // [Cardio] [Trauma] [Neuro]
             "severe respiratory distress", "anaphylaxis"},               // [Resp] [General]
            {"chest pain", "stroke symptoms", "overdose", "severe asthma", // [Cardio] [Neuro] [General] [Resp]
             "sepsis suspected", "GI bleed"},                              // [General] [Gastro]
            {"abdominal pain", "fracture", "high fever", "head injury",   // [Gastro] [Trauma] [General] [Neuro]
             "kidney stone", "vomiting", "palpitations",                  // [Gastro] [Gastro] [Cardio]
             "shortness of breath"},                                      // [Resp]
            {"laceration", "sprained ankle", "ear pain", "urinary symptoms", // [Trauma] [Trauma] [General] [Gastro]
             "minor burn", "cough", "headache"},                          // [Trauma] [Resp] [Neuro]
            {"prescription refill", "rash", "sore throat", "dressing change", "cold symptoms"}}; // all [General]

    public static List<Patient> generate(int n, long seed, double avgArrivalsPerHour) {
        Random rnd = new Random(seed);
        List<Patient> list = new ArrayList<>(n);
        double peakPerMin = avgArrivalsPerHour * 1.45 / 60.0;
        double t = 0;
        while (list.size() < n) {
            t += -Math.log(1 - rnd.nextDouble()) / peakPerMin;      // exponential gap at peak rate
            double hour = (t / 60.0) % 24;
            double ratePerMin = avgArrivalsPerHour * (1 + 0.45 * Math.sin(2 * Math.PI * (hour - 10) / 24)) / 60.0;
            if (rnd.nextDouble() > ratePerMin / peakPerMin) continue; // thinning: reject this arrival
            list.add(makePatient(list.size() + 1, (long) t, rnd));
        }
        return list;
    }

    private static Patient makePatient(int num, long arrival, Random rnd) {
        double u = rnd.nextDouble();
        int esi = 1;
        while (u > ESI_CUMULATIVE[esi - 1]) esi++;

        double[] m = MEAN[esi], s = SD[esi];
        int hr = CsvUtil.clamp(m[0] + s[0] * rnd.nextGaussian(), 30, 220);
        int sbp = CsvUtil.clamp(m[1] + s[1] * rnd.nextGaussian(), 50, 230);
        int spo2 = CsvUtil.clamp(m[2] + s[2] * rnd.nextGaussian(), 60, 100);
        int rr = CsvUtil.clamp(m[3] + s[3] * rnd.nextGaussian(), 6, 50);
        double temp = Math.round((m[4] + s[4] * rnd.nextGaussian()) * 10) / 10.0;
        int age = CsvUtil.clamp((esi <= 2 ? 55 : 38) + 20 * rnd.nextGaussian(), 1, 99);
        int pain = CsvUtil.clamp((esi <= 3 ? 6 : 3) + 2.5 * rnd.nextGaussian(), 0, 10);
        String gender = rnd.nextBoolean() ? "F" : "M";
        String complaint = COMPLAINTS[esi][rnd.nextInt(COMPLAINTS[esi].length)];
        int deteriorate = (esi == 2 || esi == 3) && rnd.nextDouble() < 0.06 ? 10 + rnd.nextInt(50) : -1;

        return new Patient(String.format("P%05d", num), age, gender, esi, complaint,
                hr, sbp, spo2, rr, temp, pain, arrival, treatmentFor(esi, rnd), deteriorate);
    }

    /** Treatment minutes by severity (uniform within a range). Also used by the MIMIC adapter. */
    public static int treatmentFor(int esi, Random rnd) {
        int[][] range = {{}, {60, 120}, {40, 80}, {30, 60}, {15, 30}, {5, 15}};
        return range[esi][0] + rnd.nextInt(range[esi][1] - range[esi][0] + 1);
    }

    public static void writeCsv(List<Patient> list, String path) throws IOException {
        Path out = Path.of(path);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println(HEADER);
            for (Patient p : list) {
                w.printf(Locale.US, "%s,%d,%s,%d,%d,%d,%d,%d,%.1f,%d,%s,%s,%d,%d,%d%n",
                        p.id, p.age, p.gender, p.esi, p.heartRate, p.systolicBP, p.spo2, p.respRate,
                        p.tempC, p.pain, p.complaint, p.department.name(), p.arrivalMin, p.treatmentMin, p.deteriorateAfterMin);
            }
        }
    }
}
