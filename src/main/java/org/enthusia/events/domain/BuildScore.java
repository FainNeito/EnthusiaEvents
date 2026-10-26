package org.enthusia.events.domain;

import java.util.Map;

/** Scores canonical states in a finite plot. Equal numerators have equal rank. */
public record BuildScore(int target, int matched, int missing, int wrong, int extra) {
    public int points() { return Math.max(0, matched - extra); }
    public static <K> BuildScore compare(Map<K, String> template, Map<K, String> built) {
        if (template.isEmpty()) throw new IllegalArgumentException("Empty template");
        int matched = 0, missing = 0, wrong = 0;
        for (var entry : template.entrySet()) {
            String actual = built.get(entry.getKey());
            if (actual == null) missing++; else if (entry.getValue().equals(actual)) matched++; else wrong++;
        }
        int extra = (int) built.keySet().stream().filter(key -> !template.containsKey(key)).count();
        return new BuildScore(template.size(), matched, missing, wrong, extra);
    }
}
