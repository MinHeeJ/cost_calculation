import { chromium } from "@playwright/test";

export default async function setup() {
  // Headless shell is provisioned by the environment; never download during tests.
  const browser = await chromium.launch({ headless: true });
  await browser.close();
}
