package com.example.tests;

import org.openqa.selenium.WebDriver;
import java.time.Duration;

public abstract class BaseTest {
    protected WebDriver driver;

    void setUp() {
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
    }
}
