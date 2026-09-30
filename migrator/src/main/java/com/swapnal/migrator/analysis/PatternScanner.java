package com.swapnal.migrator.analysis;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/** Compares Selenium idioms in the legacy source with what ended up in the generated TypeScript. */
public class PatternScanner {

    public Set<SeleniumPattern> detect(String javaSource) {
        Set<SeleniumPattern> found = EnumSet.noneOf(SeleniumPattern.class);
        for (SeleniumPattern p : SeleniumPattern.values()) {
            if (p.foundInJava(javaSource)) {
                found.add(p);
            }
        }
        return found;
    }

    /**
     * @param detected   patterns found in the Java source
     * @param typescript all generated TypeScript for the unit, concatenated
     */
    public List<PatternFinding> verify(Set<SeleniumPattern> detected, String typescript) {
        List<PatternFinding> findings = new ArrayList<>();
        for (SeleniumPattern p : detected) {
            String leftover = p.leftoverDetector()
                    .map(regex -> regex.matcher(typescript))
                    .filter(Matcher::find)
                    .map(m -> lineAround(typescript, m.start()))
                    .orElse(null);
            findings.add(leftover == null ? PatternFinding.removed(p) : PatternFinding.retained(p, leftover));
        }
        return findings;
    }

    private static String lineAround(String text, int index) {
        int start = text.lastIndexOf('\n', index) + 1;
        int end = text.indexOf('\n', index);
        return text.substring(start, end < 0 ? text.length() : end).trim();
    }
}
