import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { AmountText } from "../../src/components/AmountText";
import typography from "../../src/styles/typography.module.css";
it("음수는 Unicode 부호와 위험 색 클래스, 숫자 정렬로 표시한다", () => {
  render(<AmountText value={-1320000} />);
  expect(screen.getByText("−1,320,000")).toHaveClass(
    typography.negative,
    typography.number,
  );
});
