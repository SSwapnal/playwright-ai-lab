package com.swapnal.legacy.tests;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormControlsTest extends BaseTest {

    @Test
    void checkboxesCanBeToggled() {
        driver.get(BASE_URL + "/checkboxes");
        List<WebElement> boxes = driver.findElements(By.cssSelector("#checkboxes input"));

        if (!boxes.get(0).isSelected()) {
            boxes.get(0).click();
        }
        if (boxes.get(1).isSelected()) {
            boxes.get(1).click();
        }

        assertTrue(boxes.get(0).isSelected());
        assertFalse(boxes.get(1).isSelected());
    }

    @Test
    void dropdownSelectsOptionByVisibleText() {
        driver.get(BASE_URL + "/dropdown");
        Select dropdown = new Select(driver.findElement(By.id("dropdown")));
        dropdown.selectByVisibleText("Option 2");

        assertEquals("Option 2", dropdown.getFirstSelectedOption().getText());
    }

    @Test
    void addAndRemoveElements() {
        driver.get(BASE_URL + "/add_remove_elements/");
        WebElement addButton = driver.findElement(By.xpath("//button[text()='Add Element']"));
        addButton.click();
        addButton.click();

        assertEquals(2, driver.findElements(By.cssSelector("#elements .added-manually")).size());

        driver.findElement(By.cssSelector("#elements .added-manually")).click();
        assertEquals(1, driver.findElements(By.cssSelector("#elements .added-manually")).size());
    }
}
