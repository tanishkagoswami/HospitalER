package step04_data;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * FILE 12 — CsvUtil
 *
 * Small helpers for reading CSV files without any external library.
 * Handles quoted fields with commas inside ("pain, chest") and .csv.gz files
 * (the MIMIC-IV-ED demo ships its tables gzipped).
 */
public final class CsvUtil {

    private CsvUtil() {}

    /** Split one CSV line, respecting double quotes. */
    public static String[] split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');   // escaped quote ""
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }

    /** Opens a .csv or .csv.gz file for reading. */
    public static BufferedReader open(String path) throws IOException {
        InputStream in = new FileInputStream(path);
        if (path.endsWith(".gz")) in = new GZIPInputStream(in);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** Maps header names to column indexes, so columns can be found by name in any order. */
    public static Map<String, Integer> headerIndex(String headerLine) {
        Map<String, Integer> idx = new HashMap<>();
        String[] h = split(headerLine);
        for (int i = 0; i < h.length; i++) idx.put(h[i].toLowerCase().replace("\"", ""), i);
        return idx;
    }

    public static String get(String[] row, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col);
        return (i == null || i >= row.length) ? "" : row[i];
    }

    /** Parses a number, returning the default for blanks or junk (real data has gaps). */
    public static double num(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int clamp(double v, int lo, int hi) {
        return (int) Math.max(lo, Math.min(hi, Math.round(v)));
    }
}
