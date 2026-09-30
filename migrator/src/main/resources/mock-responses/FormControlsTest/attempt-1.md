```ts
// file: tests/form-controls.spec.ts
import { test, expect } from '@playwright/test';

test.describe('FormControlsTest', () => {
  test('checkboxes can be toggled', async ({ page }) => {
    await page.goto('/checkboxes');
    const boxes = page.locator('#checkboxes input');

    await boxes.nth(0).check();
    await boxes.nth(1).uncheck();

    await expect(boxes.nth(0)).toBeChecked();
    await expect(boxes.nth(1)).not.toBeChecked();
  });

  test('dropdown selects option by visible text', async ({ page }) => {
    await page.goto('/dropdown');
    const dropdown = page.locator('#dropdown');
    await dropdown.selectOption({ label: 'Option 2' });

    await expect(dropdown.locator('option:checked')).toHaveText('Option 2');
  });

  test('add and remove elements', async ({ page }) => {
    await page.goto('/add_remove_elements/');
    const addButton = page.getByRole('button', { name: 'Add Element' });
    await addButton.click();
    await addButton.click();

    const added = page.locator('#elements .added-manually');
    await expect(added).toHaveCount(2);

    await added.first().click();
    await expect(added).toHaveCount(1);
  });
});
```

Notes:
- The "click only if (not) selected" logic became `check()` / `uncheck()`, which are idempotent.
- `new Select(...).selectByVisibleText` became `selectOption({ label })`; the selected option is asserted via `option:checked`.
- `findElements(...).size()` became `toHaveCount()`, which retries while elements are added or removed.
- The XPath `//button[text()='Add Element']` was replaced with `getByRole('button', { name: 'Add Element' })`.
