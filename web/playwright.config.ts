import { defineConfig, devices } from '@playwright/test';

const emCi = !!process.env['CI'];

export default defineConfig({
  testDir: './e2e',
  workers: 1,
  retries: 0,
  forbidOnly: emCi,
  reporter: emCi ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      testIgnore: /celular\.spec\.ts$/,
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'celular',
      testIgnore: /desktop\.spec\.ts$/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 812 } },
    },
  ],
  webServer: process.env['E2E_URL'] ? undefined : {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
