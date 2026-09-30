package com.swapnal.migrator.analysis;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Selenium idioms that should not survive a migration to Playwright.
 *
 * <p>Each pattern has a detector for the Java source and, where one makes sense, a detector for the
 * Playwright equivalent of the anti-pattern (e.g. a hard-coded {@code waitForTimeout} in place of a
 * {@code WebDriverWait}, or {@code expect(await locator.textContent())} in place of a web-first assertion).
 */
public enum SeleniumPattern {

    IMPLICIT_WAIT("Implicit wait",
            "implicitlyWait\\s*\\(",
            "Dropped: Playwright auto-waits for elements to be actionable",
            "waitForTimeout\\s*\\(|setTimeout\\s*\\("),
    EXPLICIT_WAIT("WebDriverWait / ExpectedConditions",
            "\\b(WebDriverWait|FluentWait|ExpectedConditions)\\b",
            "Replaced by web-first assertions (expect(locator).toBeVisible / toHaveText)",
            "waitForTimeout\\s*\\(|waitForSelector\\s*\\(|setTimeout\\s*\\("),
    THREAD_SLEEP("Thread.sleep",
            "Thread\\.sleep\\s*\\(",
            "Dropped: fixed sleeps are replaced by auto-waiting",
            "waitForTimeout\\s*\\(|setTimeout\\s*\\("),
    SELECT("Select helper",
            "new\\s+Select\\s*\\(",
            "Replaced by locator.selectOption()",
            null),
    FIND_ELEMENTS("findElements + size()/get(i)",
            "\\.findElements\\s*\\(",
            "Replaced by a single locator with nth()/toHaveCount()",
            "expect\\s*\\(\\s*await\\s+[^;]*\\.count\\s*\\(\\s*\\)|\\$\\$\\s*\\("),
    GET_TEXT_ASSERTION("Assertions on getText()",
            "\\.getText\\s*\\(\\s*\\)",
            "Replaced by expect(locator).toHaveText / toContainText",
            "expect\\s*\\(\\s*await\\s+[^;]*\\.(textContent|innerText)\\s*\\("),
    IS_SELECTED("isSelected() checks",
            "\\.isSelected\\s*\\(\\s*\\)",
            "Replaced by check()/uncheck() and expect(locator).toBeChecked()",
            "expect\\s*\\(\\s*await\\s+[^;]*\\.isChecked\\s*\\("),
    CURRENT_URL("getCurrentUrl() checks",
            "getCurrentUrl\\s*\\(\\s*\\)",
            "Replaced by expect(page).toHaveURL()",
            "expect\\s*\\(\\s*page\\.url\\s*\\(\\s*\\)"),
    XPATH("XPath locators",
            "By\\.xpath\\s*\\(",
            "Replaced by role- or CSS-based locators where possible",
            "xpath=|locator\\s*\\(\\s*['\"`]//");

    private final String displayName;
    private final Pattern javaDetector;
    private final String replacement;
    private final Pattern leftoverDetector;

    SeleniumPattern(String displayName, String javaRegex, String replacement, String leftoverRegex) {
        this.displayName = displayName;
        this.javaDetector = Pattern.compile(javaRegex);
        this.replacement = replacement;
        this.leftoverDetector = leftoverRegex == null ? null : Pattern.compile(leftoverRegex);
    }

    public String displayName() {
        return displayName;
    }

    public String replacement() {
        return replacement;
    }

    public boolean foundInJava(String source) {
        return javaDetector.matcher(source).find();
    }

    public Optional<Pattern> leftoverDetector() {
        return Optional.ofNullable(leftoverDetector);
    }
}
