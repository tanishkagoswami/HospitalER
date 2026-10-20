import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import step01_model.Department;
import step01_model.Patient;
import step04_data.CsvLoader;
import step04_data.DataGenerator;
import step04_data.MimicEdAdapter;
import step05_simulation.EDSimulator;
import step05_simulation.Strategy;
import step06_analysis.Benchmark;
import step06_analysis.SelfTest;
import step06_analysis.Stats;

/**
 * FILE 22 — Main  (entry point)
 *
 * Intelligent Hospital Emergency Management System Using Priority Queues
 *
 * Run from the project root (the HospitalER folder) so the data/ and
 * results/ paths resolve. In VS Code: open Main.java and press Run,
 * or pass an option number as an argument, e.g.  java -cp out Main 3
 */
public class Main {

    static final String DATASET = "data/ed_patients_5000.csv";
    static final String MIMIC_FOLDER = "data/mimic-iv-ed-demo";
    static final int[] DOCTOR_COUNTS = {7, 8, 10};   // total doctors, split across the 6 panels

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            runOption(args[0].trim());
            return;
        }
        try (Scanner in = new Scanner(System.in)) {
            while (true) {
            System.out.println("""

                    ==========================================================
                     Intelligent Hospital Emergency Management System
                     (Priority Queues)
                    ==========================================================
                     1. Self-test the data structures
                     2. Live demo — patients routed to disease-wise panels, hourly panel boards
                     3. Compare strategies on the synthetic dataset (5,000 patients)
                     4. Benchmark: heap vs PriorityQueue vs sorted/unsorted arrays
                     5. Regenerate the synthetic dataset
                     6. Run on the REAL MIMIC-IV-ED demo dataset
                     0. Exit
                    ----------------------------------------------------------""");
            System.out.print("Choose an option: ");
            if (!in.hasNextLine()) return;
            String choice = in.nextLine().trim();
            if (choice.equals("0")) return;
                runOption(choice);
            }
        }
    }

    static void runOption(String choice) throws IOException {
        switch (choice) {
            case "1" -> SelfTest.run();
            case "2" -> liveDemo();
            case "3" -> compareStrategies(loadOrCreateDataset(), "results/strategy_comparison.csv");
            case "4" -> Benchmark.run("results/benchmark.csv");
            case "5" -> generateDataset();
            case "6" -> runMimic();
            default -> System.out.println("Unknown option: " + choice);
        }
    }

    // ---------------- Option 2 ----------------
    static void liveDemo() {
        // a busy evening: 40 patients at ~14/hour, one doctor per panel, so queues build up
        List<Patient> small = DataGenerator.generate(40, 2051, 14.0);   // seed chosen so one patient worsens while waiting
        int[] onePerPanel = new int[Department.values().length];
        java.util.Arrays.fill(onePerPanel, 1);
        System.out.println("\nLive demo: 40 patients, 6 disease-wise panels with 1 doctor each, strategy = PRIORITY_AGING\n");
        EDSimulator sim = new EDSimulator(small, onePerPanel, Strategy.PRIORITY_AGING, true);
        List<EDSimulator.Treated> treated = sim.run();
        Stats.print(sim, treated);
    }

    // ---------------- Option 3 / 6 ----------------
    static void compareStrategies(List<Patient> patients, String csvOut) throws IOException {
        new File(csvOut).delete();   // fresh results each run
        for (int doctors : DOCTOR_COUNTS) {
            int[] alloc = EDSimulator.allocateDoctors(patients, doctors);
            List<String> parts = new ArrayList<>();
            for (Department d : Department.values()) parts.add(d.label + " " + alloc[d.ordinal()]);
            System.out.printf("%n##### %d doctors, split by workload (max-heap): %s #####%n", doctors, String.join(", ", parts));
            for (Strategy s : Strategy.values()) {
                EDSimulator sim = new EDSimulator(patients, alloc, s, false);
                List<EDSimulator.Treated> treated = sim.run();
                Stats.print(sim, treated);
                Stats.appendCsv(csvOut, s.name(), doctors, treated);
            }
        }
        System.out.println("\nSaved: " + csvOut);
    }

    static List<Patient> loadOrCreateDataset() throws IOException {
        if (!new File(DATASET).exists()) generateDataset();
        List<Patient> patients = CsvLoader.load(DATASET);
        System.out.printf("%nLoaded %d patients from %s (%d bad rows skipped)%n",
                patients.size(), DATASET, CsvLoader.lastSkipped);
        printMix(patients);
        return patients;
    }

    // ---------------- Option 5 ----------------
    static void generateDataset() throws IOException {
        List<Patient> list = DataGenerator.generate(5_000, 42, 6.5);
        DataGenerator.writeCsv(list, DATASET);
        System.out.printf("Generated %d patients over %.1f days -> %s%n",
                list.size(), list.get(list.size() - 1).arrivalMin / 1440.0, DATASET);
    }

    static void runMimic() throws IOException {
        File folder = new File(MIMIC_FOLDER);
        if (!folder.exists()) {
            System.out.println("""

                    MIMIC-IV-ED demo not found. It is free and needs no login:
                      1. Open https://physionet.org/content/mimic-iv-ed-demo/
                      2. Download the files (ZIP at the bottom of the page)
                      3. Unzip it and copy edstays.csv.gz and triage.csv.gz
                         (they may sit inside an ed/ subfolder)
                         into:  HospitalER/data/mimic-iv-ed-demo/
                      4. Run option 6 again.""");
            return;
        }
        // The demo is small (100 patients), so arrivals are re-spaced at 5.5/hour so a realistic queue forms.
        List<Patient> real = MimicEdAdapter.load(MIMIC_FOLDER, 5.5, 7);
        System.out.printf("%nLoaded %d real ED visits from MIMIC-IV-ED (%d rows skipped: no acuity / no stay)%n",
                real.size(), MimicEdAdapter.lastSkipped);
        printMix(real);
        compareStrategies(real, "results/mimic_strategy_comparison.csv");
    }

    static void printMix(List<Patient> patients) {
        double n = Math.max(1, patients.size());
        int[] esi = new int[6];
        int[] dept = new int[Department.values().length];
        for (Patient p : patients) {
            esi[p.esi]++;
            dept[p.department.ordinal()]++;
        }
        List<String> parts = new ArrayList<>();
        for (int e = 1; e <= 5; e++) parts.add(String.format("ESI-%d %d (%.1f%%)", e, esi[e], 100 * esi[e] / n));
        System.out.println("ESI mix:   " + String.join(" | ", parts));
        parts.clear();
        for (Department d : Department.values())
            parts.add(String.format("%s %d (%.1f%%)", d.label, dept[d.ordinal()], 100 * dept[d.ordinal()] / n));
        System.out.println("Panel mix: " + String.join(" | ", parts));
    }
}
