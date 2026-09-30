package com.example.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class SummaryPage {
    private final WebDriver driver;

    public SummaryPage(WebDriver driver) {
        this.driver = driver;
    }

    public String total() {
        return driver.findElement(By.id("total")).getText();
    }
}
