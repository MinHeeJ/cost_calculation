import { create } from "zustand";

export interface Period {
  year: number;
  month: number;
}
export function seoulToday(now = new Date()): Period {
  const parts = new Intl.DateTimeFormat("en", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
  }).formatToParts(now);
  return {
    year: Number(parts.find((part) => part.type === "year")!.value),
    month: Number(parts.find((part) => part.type === "month")!.value),
  };
}
export function monthKey(period: Period): string {
  return `${String(period.year).padStart(4, "0")}-${String(period.month).padStart(2, "0")}`;
}
export function shiftPeriod(period: Period, months: number): Period {
  const value = period.year * 12 + period.month - 1 + months;
  return {
    year: Math.floor(value / 12),
    month: (((value % 12) + 12) % 12) + 1,
  };
}
export const usePeriods = create<{
  values: Record<string, Period>;
  setPeriod: (screenId: string, value: Period) => void;
}>((set) => ({
  values: {},
  setPeriod: (id, value) => {
    if (
      value.year >= 0 &&
      value.year <= 9999 &&
      value.month >= 1 &&
      value.month <= 12
    ) {
      set((state) => ({ values: { ...state.values, [id]: value } }));
    }
  },
}));
