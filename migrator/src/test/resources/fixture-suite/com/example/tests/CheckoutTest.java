package com.example.tests;

import com.example.pages.CartPage;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.Select;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutTest extends BaseTest {

    @Test
    void checkoutShowsTotal() {
        // mentions DashboardPage only in a comment and "HelperPage" in a string
        CartPage cart = new CartPage(driver);
        new Select(driver.findElement(By.id("qty"))).selectByVisibleText("2");
        assertEquals("Total: 2", cart.checkout().total());
    }
}
