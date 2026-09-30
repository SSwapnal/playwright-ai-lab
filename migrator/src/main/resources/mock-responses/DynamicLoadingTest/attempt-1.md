```ts
// file: tests/dynamic-loading.spec.ts
import { test, expect } from '@playwright/test';

test.describe('DynamicLoadingTest', () => {
  test('hidden element appears after loading', async ({ page }) => {
    await page.goto('/dynamic_loading/1');
    await page.locator('#start button').click();

    // No explicit wait: the web-first assertion retries until the element is visible with the text.
    await expect(page.locator('#finish h4')).toHaveText('Hello World!');
  });
});
```

Notes:
- The `WebDriverWait` for visibility was removed. `toHaveText` only matches visible text and retries until the expect timeout.
- The loading indicator takes about 5 seconds; the project's `expect.timeout` (10s) covers that without a hard-coded wait.
