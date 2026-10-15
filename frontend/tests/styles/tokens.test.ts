import { readFileSync } from "node:fs";
import { expect, it } from "vitest";
const css = readFileSync("src/styles/tokens.css", "utf8");
const controls = readFileSync("src/components/Common.module.css", "utf8");
const shell = readFileSync("src/shell/Shell.module.css", "utf8");

it("승인 색과 다섯 상태 팔레트를 CSS 변수로 제공한다", () => {
  for (const color of [
    "#1D4ED8",
    "#1E40AF",
    "#EFF6FF",
    "#111827",
    "#4B5563",
    "#9CA3AF",
    "#D1D5DB",
    "#E5E7EB",
    "#F9FAFB",
    "#FFFFFF",
    "#F3F4F6",
    "#15803D",
    "#F0FDF4",
    "#B45309",
    "#FFFBEB",
    "#B91C1C",
    "#FEF2F2",
    "#0369A1",
    "#F0F9FF",
  ])
    expect(css.toLowerCase()).toContain(color.toLowerCase());
  for (const status of ["success", "warning", "danger", "info", "locked"]) {
    expect(css).toContain(`--${status}:`);
    expect(css).toContain(`--${status}-bg:`);
  }
  expect(controls).not.toMatch(/#[0-9a-f]{3,8}\b/i);
});
it("간격, 모서리, 그림자, 글자 크기 하한을 유지한다", () => {
  expect(
    [...css.matchAll(/--space-(\d+):/g)].map((match) => Number(match[1])),
  ).toEqual([4, 8, 12, 16, 24, 32]);
  expect(css).toContain("--radius-control: 4px");
  expect(css).toContain("--radius-card: 8px");
  expect(css).toMatch(
    /--shadow-dropdown:\s*0 2px 4px rgba\(0,\s*0,\s*0,\s*0?\.08\)/,
  );
  expect(css).toMatch(
    /--shadow-overlay:\s*0 8px 24px rgba\(0,\s*0,\s*0,\s*0?\.16\)/,
  );
  expect(
    [...css.matchAll(/--\w+-size: (\d+)px/g)].every(
      (match) => Number(match[1]) >= 12,
    ),
  ).toBe(true);
});
it("폰트는 실제 WOFF2를 자체 제공하고 외부 CDN stylesheet를 사용하지 않는다", () => {
  expect(css).toMatch(/url\(["']\/fonts\/PretendardVariable\.woff2["']\)/);
  expect(css).toMatch(/Pretendard,\s*["']Malgun Gothic["'],\s*sans-serif/);
  const bytes = readFileSync("public/fonts/PretendardVariable.woff2");
  expect(bytes.subarray(0, 4).toString()).toBe("wOF2");
  expect(bytes.length).toBeGreaterThan(10000);
  expect(css).not.toContain("https://");
});
it("반응형 계약과 좁은 창 크기를 CSS 규칙으로 선언한다 (레이아웃 실측은 E2E)", () => {
  expect(shell).toContain("(max-width: 1279px)");
  expect(shell).toContain("(max-width: 767px)");
  expect(shell).toContain("--sidebar-width");
  expect(shell).toContain("--sidebar-collapsed");
  expect(css).toContain("--touch-height: 44px");
  expect(controls).toContain(
    "width: min(400px, calc(100vw - var(--space-32)))",
  );
  expect(controls).toContain(
    "width: min(640px, calc(100vw - var(--space-32)))",
  );
  expect(controls).toContain("position: sticky");
  expect(controls).toContain("margin-top: var(--space-12)");
});
