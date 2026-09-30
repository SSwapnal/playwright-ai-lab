package com.example.pages;

import org.openqa.selenium.WebDriver;

public class CartPage {
    private final WebDriver driver;

    public CartPage(WebDriver driver) {
        this.driver = driver;
    }

    public SummaryPage checkout() {
        return new SummaryPage(driver);
    }
}
