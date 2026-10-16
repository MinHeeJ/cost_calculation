import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: ["./tests/setup.ts"],
    include: [
      "tests/**/*.test.{ts,tsx}",
      "src/pages/shared/**/*.test.{ts,tsx}",
      "src/pages/codegroups/**/*.test.{ts,tsx}",
      "src/pages/commoncode/**/*.test.{ts,tsx}",
    ],
    restoreMocks: true,
  },
});
