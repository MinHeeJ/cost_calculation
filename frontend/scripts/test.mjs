import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const args = process.argv.slice(2);
const isE2E = args.some((argument) =>
  argument.replaceAll("\\", "/").includes("tests/e2e/"),
);
const runner = isE2E ? "@playwright/test/cli" : "vitest/vitest.mjs";
const runnerArgs = isE2E
  ? ["test", ...args.filter((argument) => argument !== "--run")]
  : args;
const result = spawnSync(
  process.execPath,
  [fileURLToPath(import.meta.resolve(runner)), ...runnerArgs],
  {
    stdio: "inherit",
  },
);

if (result.error) {
  console.error(result.error);
}
process.exit(result.status ?? 1);
