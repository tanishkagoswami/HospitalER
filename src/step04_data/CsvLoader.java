package step04_data;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import step01_model.Department;
import step01_model.Patient;

/**
 * FILE 14 — CsvLoader
 *
 * Reads the project's standard patient CSV (the format DataGenerator writes
 * and MimicEdAdapter converts into). Columns are looked up by NAME, so a
 * file with reordered columns still loads.
 *
 * The "department" column (disease-wise panel) is optional: if it is
 * missing or invalid, the panel is routed from the chief complaint.
 *
 * Validation: rows with a missing id or an ESI outside 1..5 are skipped and
 * counted, so one bad row never crashes the whole run.
 */
public final class CsvLoader {

    private CsvLoader() {}

    public static int lastSkipped = 0;

    public static List<Patient> load(String path) throws IOException {
        List<Patient> list = new ArrayList<>();
        lastSkipped = 0;
        try (BufferedReader r = CsvUtil.open(path)) {
            String header = r.readLine();
            if (header == null) return list;
            Map<String, Integer> idx = CsvUtil.headerIndex(header);
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] c = CsvUtil.split(line);
                String id = CsvUtil.get(c, idx, "id");
                int esi = (int) CsvUtil.num(CsvUtil.get(c, idx, "esi"), -1);
                if (id.isEmpty() || esi < 1 || esi > 5) {
                    lastSkipped++;
                    continue;
                }
                Patient p = new Patient(
                        id,
                        (int) CsvUtil.num(CsvUtil.get(c, idx, "age"), 40),
                        CsvUtil.get(c, idx, "gender"),
                        esi,
                        CsvUtil.get(c, idx, "chief_complaint"),
                        CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, idx, "heart_rate"), 80), 20, 250),
                        CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, idx, "systolic_bp"), 120), 40, 260),
                        CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, idx, "spo2"), 98), 50, 100),
                        CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, idx, "resp_rate"), 16), 4, 60),
                        CsvUtil.num(CsvUtil.get(c, idx, "temp_c"), 37.0),
                        CsvUtil.clamp(CsvUtil.num(CsvUtil.get(c, idx, "pain"), 0), 0, 10),
                        (long) CsvUtil.num(CsvUtil.get(c, idx, "arrival_min"), 0),
                        (int) CsvUtil.num(CsvUtil.get(c, idx, "treatment_min"), 30),
                        (int) CsvUtil.num(CsvUtil.get(c, idx, "deteriorate_after_min"), -1));
                // use the file's department column if valid, else keep the panel routed from the complaint
                Department d = Department.parse(CsvUtil.get(c, idx, "department"));
                if (d != null) p.department = d;
                list.add(p);
            }
        }
        // No sorting needed: the simulator's event min-heap puts arrivals in time order.
        return list;
    }
}
