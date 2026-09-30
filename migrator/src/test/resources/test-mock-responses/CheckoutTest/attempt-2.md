```ts
// file: tests/checkout.spec.ts
import { test, expect } from '@playwright/test';
import { CartPage } from '../pages/CartPage';

test('checkout shows total', async ({ page }) => {
  await new CartPage(page).open();
  await expect(page.locator('#total')).toHaveText('Total: 2');
});
```

```ts
// file: pages/CartPage.ts
import { type Page } from '@playwright/test';

export class CartPage {
  constructor(private readonly page: Page) {}

  async open(): Promise<void> {
    await this.page.goto('/cart');
  }
}
```

Notes:
- Fixed the expected total.
