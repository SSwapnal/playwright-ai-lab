package com.swapnal.legacy.tests;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicLoadingTest extends BaseTest {

    @Test
    void hiddenElementAppearsAfterLoading() {
        driver.get(BASE_URL + "/dynamic_loading/1");
        driver.findElement(By.cssSelector("#start button")).click();

        // Classic Selenium explicit wait - Playwright auto-waits, so this should disappear after migration
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#finish h4")));

        assertEquals("Hello World!", driver.findElement(By.cssSelector("#finish h4")).getText());
    }
}
