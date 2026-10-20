package step01_model;

/**
 * FILE 4 — Department  (the disease-wise panels)
 *
 * Every patient is routed to ONE panel based on their chief complaint.
 * Each panel has its OWN priority queue of waiting patients and its OWN
 * doctors, so a cardiac patient is never stuck behind a sprained ankle,
 * and inside each panel patients are still served strictly by priority.
 *
 *   Panel                 Example complaints
 *   CARDIOLOGY            cardiac arrest, chest pain, palpitations
 *   NEUROLOGY             stroke symptoms, unresponsive, head injury, seizure
 *   TRAUMA_ORTHO          major trauma, fracture, laceration, sprain, burn
 *   RESPIRATORY           respiratory distress, asthma, shortness of breath
 *   GASTRO_ABDOMINAL      abdominal pain, GI bleed, vomiting, kidney stone, urinary
 *   GENERAL_MEDICINE      fever, sepsis, overdose, rash, sore throat, refills ... (everything else)
 *
 * Routing uses keyword matching, checked in the order the panels are listed,
 * so it also works on the free-text complaints in the real MIMIC-IV-ED data.
 */
public enum Department {

    CARDIOLOGY("Cardiology",
            "cardiac", "chest", "palpitation", "heart", "syncope", "hypertension"),
    NEUROLOGY("Neurology",
            "stroke", "unresponsive", "head", "seizure", "altered", "confusion", "weakness",
            "numb", "dizz", "headache", "neuro"),
    TRAUMA_ORTHO("Trauma & Ortho",
            "trauma", "fracture", "lacer", "sprain", "burn", "fall", "injury", "wound",
            "mvc", "assault", "back pain", "fx", "knee", "ankle", "wrist", "hip", "shoulder",
            "leg pain", "arm pain", "hand", "foot", "finger", "neck pain"),
    RESPIRATORY("Respiratory",
            "respiratory", "asthma", "dyspnea", "breath", "sob", "cough", "wheez", "copd", "hypoxia"),
    GASTRO_ABDOMINAL("Gastro & Abdominal",
            "abd", "gi bleed", "vomit", "nausea", "n/v", "diarrhea", "kidney", "flank",
            "urinary", "dysuria", "hematuria", "rectal", "melena"),
    GENERAL_MEDICINE("General Medicine");   // fallback panel: no keywords needed

    public final String label;
    private final String[] keywords;

    Department(String label, String... keywords) {
        this.label = label;
        this.keywords = keywords;
    }

    /** Picks the panel for a chief complaint (case-insensitive keyword match). */
    public static Department route(String complaint) {
        String c = complaint == null ? "" : complaint.toLowerCase();
        for (Department d : values())
            for (String k : d.keywords)
                if (c.contains(k)) return d;
        return GENERAL_MEDICINE;
    }

    /** Parses a name such as "CARDIOLOGY"; returns null if it is not a valid panel. */
    public static Department parse(String s) {
        for (Department d : values()) if (d.name().equalsIgnoreCase(s.trim())) return d;
        return null;
    }
}
