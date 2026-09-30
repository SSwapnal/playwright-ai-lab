package com.swapnal.migrator.analysis;

/**
 * A Selenium pattern found in the legacy source, and whether an equivalent anti-pattern is still
 * present in the generated TypeScript.
 */
public record PatternFinding(String pattern, String replacement, boolean removed, String leftover) {

    public static PatternFinding removed(SeleniumPattern p) {
        return new PatternFinding(p.displayName(), p.replacement(), true, null);
    }

    public static PatternFinding retained(SeleniumPattern p, String leftover) {
        return new PatternFinding(p.displayName(), p.replacement(), false, leftover);
    }
}
