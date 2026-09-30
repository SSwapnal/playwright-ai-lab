```ts
// file: tests/checkout.spec.ts
import { test, expect } from '@playwright/test';

test('checkout shows total', async ({ page }) => {
  await page.waitForTimeout(1000);
  await expect(page.locator('#total')).toHaveText('Total: 3');
});
```

```ts
// file: playwright.config.ts
export default {};
```

Notes:
- First draft.
