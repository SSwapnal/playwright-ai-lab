The flash element contains the message plus surrounding whitespace and a "×" close button, so an exact `toHaveText` can never match. The Java test used `String.contains`, which maps to `toContainText`. That was a translation error on my side, not a problem with the page. The page object is unchanged.

```ts
// file: pages/LoginPage.ts
import { type Locator, type Page } from '@playwright/test';

export class LoginPage {
  readonly username: Locator;
  readonly password: Locator;
  readonly loginButton: Locator;
  readonly flash: Locator;

  constructor(private readonly page: Page) {
    this.username = page.locator('#username');
    this.password = page.locator('#password');
    this.loginButton = page.locator("button[type='submit']");
    this.flash = page.locator('#flash');
  }

  async open(): Promise<this> {
    await this.page.goto('/login');
    return this;
  }

  async loginAs(user: string, pass: string): Promise<void> {
    await this.username.fill(user);
    await this.password.fill(pass);
    await this.loginButton.click();
  }
}
```

```ts
// file: tests/login.spec.ts
import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage';

test.describe('LoginTest', () => {
  test('valid login shows secure area', async ({ page }) => {
    const loginPage = await new LoginPage(page).open();
    await loginPage.loginAs('tomsmith', 'SuperSecretPassword!');

    await expect(loginPage.flash).toContainText('You logged into a secure area!');
    await expect(page).toHaveURL(/\/secure$/);
  });

  test('invalid password shows error', async ({ page }) => {
    const loginPage = await new LoginPage(page).open();
    await loginPage.loginAs('tomsmith', 'wrong-password');

    await expect(loginPage.flash).toContainText('Your password is invalid!');
  });
});
```

Notes:
- Corrected the flash assertions from `toHaveText` (exact match) to `toContainText`, matching the original `contains(...)` checks.
