import { describe, expect, it } from "vitest";
import {
  formatAmount,
  formatRate,
  formatDate,
  formatMonth,
  formatDateTime,
} from "../../src/lib/format";

describe("표시 함수", () => {
  it("원 단위 쉼표, Unicode 음수, 0, 값 없음을 구별한다", () => {
    expect(formatAmount(-1320000)).toBe("−1,320,000");
    expect(formatAmount(1500000)).toBe("1,500,000");
    expect(formatAmount(0)).toBe("0");
    expect(formatAmount(null)).toBe("–");
  });
  it("비율은 퍼센트 수치 그대로 소수 한 자리로 반올림한다", () => {
    expect(formatRate(0.1)).toBe("0.1%");
    expect(formatRate(12.36)).toBe("12.4%");
    expect(formatRate(null)).toBe("–");
  });
  it("지역 TIMESTAMP는 유지하고 zone 시각은 서울로 변환한다", () => {
    expect(formatDate("2026-09-30")).toBe("2026-09-30");
    expect(formatMonth("2026-09")).toBe("2026-09");
    expect(formatDateTime("2026-10-05T00:00:00")).toBe("2026-10-05 00:00");
    expect(formatDateTime("2026-09-30T15:00:00Z")).toBe("2026-10-01 00:00");
    expect(formatMonth("2026-09-30T15:00:00Z")).toBe("2026-10");
    expect(formatDate(null)).toBe("–");
    expect(formatDateTime(null)).toBe("–");
  });
});
