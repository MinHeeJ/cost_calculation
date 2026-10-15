import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import typography from "../../src/styles/typography.module.css";
import { readFileSync } from "node:fs";
const css = readFileSync("src/styles/tokens.css", "utf8");
const typeCss = readFileSync("src/styles/typography.module.css", "utf8");

it("title/section/body/table/label/caption 크기·줄높이·굵기를 적용한다", () => {
  for (const [name, size, line, weight] of [
    ["title", 20, 28, 700],
    ["section", 16, 24, 600],
    ["body", 14, 20, 400],
    ["table", 13, 20, 400],
    ["label", 13, 18, 500],
    ["caption", 12, 16, 400],
  ]) {
    expect(css).toContain(`--${name}-size: ${size}px`);
    expect(css).toContain(`--${name}-line: ${line}px`);
    expect(css).toContain(`--${name}-weight: ${weight}`);
  }
  render(<h1 className={typography.title}>제목</h1>);
  expect(screen.getByRole("heading")).toHaveClass(typography.title);
});
it("숫자 tabular-nums·오른쪽, 글자 왼쪽, 코드·상태·날짜 가운데 규칙을 제공한다", () => {
  expect(typeCss).toContain("font-variant-numeric: tabular-nums");
  expect(typeCss).toContain("text-align: right");
  expect(typeCss).toContain("text-align: left");
  expect(typeCss).toMatch(
    /\.code,\s*\.status,\s*\.date\s*\{\s*text-align:\s*center;\s*\}/,
  );
});
