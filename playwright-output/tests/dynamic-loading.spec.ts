import { test, expect } from '@playwright/test';

test.describe('DynamicLoadingTest', () => {
  // Java: hiddenElementAppearsAfterLoading()
  test('hidden element appears after loading', async ({ page }) => {
    // Resolve on DOMContentLoaded instead of the full "load" event, so slow or blocked
    // third-party resources cannot stall navigation. click() below auto-waits for the button.
    await page.goto('/dynamic_loading/1', { waitUntil: 'domcontentloaded' });
    await page.locator('#start button').click();

    const finishHeading = page.locator('#finish h4');

    // Replaces WebDriverWait + visibilityOfElementLocated; keeps the original 10-second limit
    await expect(finishHeading).toBeVisible({ timeout: 10_000 });

    // Replaces assertEquals (exact match)
    await expect(finishHeading).toHaveText('Hello World!');
  });
});
