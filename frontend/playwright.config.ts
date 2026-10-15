import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e",
  globalSetup: "./scripts/playwright-setup.mjs",
  use: { baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:3000" },
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "npm run dev -- --host 127.0.0.1 --port 3000 --strictPort",
        url: "http://127.0.0.1:3000",
        reuseExistingServer: false,
      },
});
