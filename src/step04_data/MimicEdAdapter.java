package step04_data;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import step01_model.Patient;
import step02_datastructures.MinHeap;

/**
 * FILE 15 — MimicEdAdapter  (REAL hospital data)
 *
 * Converts the MIMIC-IV-ED dataset (real, de-identified emergency
 * department visits from Beth Israel Deaconess Medical Center, Boston,
 * published on PhysioNet) into our Patient objects.
 *
 * Two versions exist:
 *   - MIMIC-IV-ED DEMO: ~100 patients, OPEN ACCESS, no login needed.
 *       https://physionet.org/content/mimic-iv-ed-demo/
 *   - Full MIMIC-IV-ED: ~400k visits, needs PhysioNet credentialing + CITI training.
 * Both use identical table layouts, so this adapter reads either one.
 *
 * Files used (plain .csv or .csv.gz):
 *   edstays.csv  ->  stay_id, intime, gender
 *   triage.csv   ->  stay_id, temperature (F), heartrate, resprate, o2sat, sbp, pain, acuity, chiefcomplaint
 *
 * The two tables are JOINED on stay_id using a HashMap (O(n) join).
 *
 * Design choices (explain these in your report):
 *  - "acuity" in MIMIC IS the ESI level (1-5).
 *  - The free-text chief complaint is routed to a disease-wise panel by
 *    keyword (Department.route), e.g. "Chest pain, Dyspnea" -> Cardiology.
 *  - Missing vitals are replaced by normal values; rows with no acuity are skipped.
 *  - MIMIC dates are shifted years apart for privacy, so the real arrival
 *    gaps are meaningless for a queue. We keep the ORDER of arrival but
 *    re-space arrivals with a Poisson process at a chosen rate.
 *  - MIMIC has no "treatment time", so it is drawn from the same
 *    severity-based ranges as the synthetic generator.
 *  - Age lives in a different MIMIC module (hosp/patients), so it defaults to 45.
 */
public final class MimicEdAdapter {

    private MimicEdAdapter() {}

    public static int lastSkipped = 0;

    public static List<Patient> load(String folder, double arrivalsPerHour, long seed) throws IOException {
        String staysPath = find(folder, "edstays");
        String triagePath = find(folder, "triage");
        lastSkipped = 0;

        // ---- 1. Read edstays into a HashMap: stay_id -> {intime, gender}
        Map<String, String[]> stays = new HashMap<>();
        try (BufferedReader r = CsvUtil.open(staysPath)) {
            Map<String, Integer> idx = CsvUtil.headerIndex(r.readLine());
            String line;
            while ((line = r.readLine()) != null) {
                String[] c = CsvUtil.split(line);
                stays.put(CsvUtil.get(c, idx, "stay_id"),
                        new String[]{CsvUtil.get(c, idx, "intime"), CsvUtil.get(c, idx, "gender")});
            }
        }

        // ---- 2. Read triage and join with edstays on stay_id
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Object[]> joined = new ArrayList<>();   // {LocalDateTime intime, String[] row, Map idx, gender}
        Map<String, Integer> tIdx;
        try (BufferedReader r = CsvUtil.open(triagePath)) {
            tIdx = CsvUtil.headerIndex(r.readLine());
            String line;
            while ((line = r.readLine()) != null) {
                String[] c = CsvUtil.split(line);
                String stayId = CsvUtil.get(c, tIdx, "stay_id");
                int esi = (int) CsvUtil.num(CsvUtil.get(c, tIdx, "acuity"), -1);
                String[] stay = stays.get(stayId);
                if (stay == null || esi < 1 || esi > 5) {
                    lastSkipped++;
                    continue;
                }
                LocalDateTime in;
                try {
                    in = LocalDateTime.parse(stay[0], fmt);
                } catch (Exception e) {
                    lastSkipped++;
                    continue;
                }
                joined.add(new Object[]{in, c, stay[1], stayId});
            }
        }

        // ---- 3. Keep real arrival ORDER (a min-heap by arrival time), re-space with Poisson gaps
        MinHeap<Object[]> byArrival = new MinHeap<>((a, b) -> ((LocalDateTime) a[0]).compareTo((LocalDateTime) b[0]));
        for (Object[] j : joined) byArrival.insert(j);
        Random rnd = new Random(seed);
        List<Patient> list = new ArrayList<>();
        double t = 0;
        while (!byArrival.isEmpty()) {
            Object[] j = byArrival.extractMin();
            String[] c = (String[]) j[1];
            t += -Math.log(1 - rnd.nextDouble()) / (arrivalsPerHour / 60.0);
            int esi = (int) CsvUtil.num(CsvUtil.get(c, tIdx, "acuity"), 3);

            double tempF = CsvUtil.num(CsvUtil.get(c, tIdx, "temperature"), 98.6);
            double tempC = tempF > 45 ? (tempF - 32) * 5 / 9 : tempF;   // a few rows are already in C
            tempC = Math.round(tempC * 10) / 10.0;
            String complaint = CsvUtil.get(c, tIdx, "chiefcomplaint").replace(",", ";").toLowerCase();

            int deteriorate = (esi == 2 || esi == 3) && rnd.nextDouble() < 0.06 ? 10 + rnd.nextInt(50) : -1;
            list.add(new Patient(
                    "M" + j[3], 45, (String) j[2], esi, complaint.isEmpty() ? "unknown" : complaint,
                    CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, tIdx, "heartrate"), 80), 20, 250),
                    CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, tIdx, "sbp"), 120), 40, 260),
                    CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, tIdx, "o2sat"), 98), 50, 100),
                    CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, tIdx, "resprate"), 16), 4, 60),
                    tempC,
                    CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, tIdx, "pain"), 0), 0, 10),
                    (long) t, DataGenerator.treatmentFor(esi, rnd), deteriorate));
        }
        return list;
    }

    /** Finds name.csv or name.csv.gz inside the folder. */
    private static String find(String folder, String name) throws IOException {
        for (String ext : new String[]{".csv", ".csv.gz"}) {
            File f = new File(folder, name + ext);
            if (f.exists()) return f.getPath();
        }
        throw new IOException("Could not find " + name + ".csv or " + name + ".csv.gz in " + folder);
    }
}
