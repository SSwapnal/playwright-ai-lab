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
