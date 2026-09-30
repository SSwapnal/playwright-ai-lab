import { test, expect } from '@playwright/test';

test.describe('DynamicLoadingTest', () => {
  test('hidden element appears after loading', async ({ page }) => {
    await page.goto('/dynamic_loading/1');
    await page.locator('#start button').click();

    // No explicit wait: the web-first assertion retries until the element is visible with the text.
    await expect(page.locator('#finish h4')).toHaveText('Hello World!');
  });
});
