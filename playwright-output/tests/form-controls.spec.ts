import { test, expect } from '@playwright/test';

test.describe('FormControlsTest', () => {
  // Java: checkboxesCanBeToggled
  test('checkboxes can be toggled', async ({ page }) => {
    await page.goto('/checkboxes');
    const boxes = page.locator('#checkboxes input');
    const firstBox = boxes.nth(0);
    const secondBox = boxes.nth(1);

    // Original logic: select the first box only if unselected, deselect the second only if selected.
    // check()/uncheck() are idempotent and preserve that behavior.
    await firstBox.check();
    await secondBox.uncheck();

    await expect(firstBox).toBeChecked();
    await expect(secondBox).not.toBeChecked();
  });

  // Java: dropdownSelectsOptionByVisibleText
  test('dropdown selects option by visible text', async ({ page }) => {
    await page.goto('/dropdown');
    const dropdown = page.locator('#dropdown');

    await dropdown.selectOption({ label: 'Option 2' });

    await expect(dropdown.locator('option:checked')).toHaveText('Option 2');
  });

  // Java: addAndRemoveElements
  test('add and remove elements', async ({ page }) => {
    await page.goto('/add_remove_elements/');
    const addButton = page.getByRole('button', { name: 'Add Element', exact: true });
    const addedElements = page.locator('#elements .added-manually');

    await addButton.click();
    await addButton.click();

    await expect(addedElements).toHaveCount(2);

    await addedElements.first().click();

    await expect(addedElements).toHaveCount(1);
  });
});
