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
