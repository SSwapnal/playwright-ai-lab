package com.swapnal.migrator.analysis;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternScannerTest {

    private final PatternScanner scanner = new PatternScanner();

    @Test
    void detectsSeleniumIdioms() {
        String java = """
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
                new WebDriverWait(driver, Duration.ofSeconds(10));
                new Select(driver.findElement(By.id("dropdown")));
                driver.findElements(By.cssSelector("li")).size();
                driver.findElement(By.xpath("//button")).getText();
                """;

        Set<SeleniumPattern> found = scanner.detect(java);

        assertEquals(EnumSet.of(SeleniumPattern.IMPLICIT_WAIT, SeleniumPattern.EXPLICIT_WAIT,
                SeleniumPattern.SELECT, SeleniumPattern.FIND_ELEMENTS, SeleniumPattern.GET_TEXT_ASSERTION,
                SeleniumPattern.XPATH), found);
    }

    @Test
    void cleanPlaywrightCodeMarksPatternsRemoved() {
        String ts = """
                await expect(page.locator('#finish h4')).toHaveText('Hello World!');
                await expect(page.locator('li')).toHaveCount(2);
                """;

        List<PatternFinding> findings = scanner.verify(
                EnumSet.of(SeleniumPattern.EXPLICIT_WAIT, SeleniumPattern.FIND_ELEMENTS), ts);

        assertTrue(findings.stream().allMatch(PatternFinding::removed));
    }

    @Test
    void hardWaitsAndNonWebFirstAssertionsAreFlagged() {
        String ts = """
                await page.waitForTimeout(5000);
                expect(await page.locator('li').count()).toBe(2);
                """;

        List<PatternFinding> findings = scanner.verify(
                EnumSet.of(SeleniumPattern.EXPLICIT_WAIT, SeleniumPattern.FIND_ELEMENTS), ts);

        assertEquals(2, findings.size());
        assertFalse(findings.get(0).removed());
        assertEquals("await page.waitForTimeout(5000);", findings.get(0).leftover());
        assertFalse(findings.get(1).removed());
    }
}
