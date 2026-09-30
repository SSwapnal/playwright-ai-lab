package com.swapnal.legacy.tests;

import com.swapnal.legacy.pages.LoginPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginTest extends BaseTest {

    @Test
    void validLoginShowsSecureArea() {
        LoginPage page = new LoginPage(driver).open(BASE_URL);
        page.loginAs("tomsmith", "SuperSecretPassword!");

        assertTrue(page.flashMessage().contains("You logged into a secure area!"));
        assertTrue(driver.getCurrentUrl().endsWith("/secure"));
    }

    @Test
    void invalidPasswordShowsError() {
        LoginPage page = new LoginPage(driver).open(BASE_URL);
        page.loginAs("tomsmith", "wrong-password");

        assertTrue(page.flashMessage().contains("Your password is invalid!"));
    }
}
