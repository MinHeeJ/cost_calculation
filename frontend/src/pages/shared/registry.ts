export type PeriodType = "NONE" | "YEAR" | "MONTH" | "YEAR_CUM";
export interface ScreenDefinition {
  screenId: string;
  periodType: PeriodType;
  mobile: boolean;
}

/** Route/period metadata is a UI contract, not a second copy of database menus. */
export const screenRegistry: ScreenDefinition[] = [
  { screenId: "UI-16", periodType: "NONE", mobile: true },
  { screenId: "UI-15", periodType: "NONE", mobile: true },
  { screenId: "UI-02", periodType: "NONE", mobile: true },
  { screenId: "UI-01", periodType: "YEAR", mobile: true },
  { screenId: "UI-31", periodType: "NONE", mobile: true },
  { screenId: "UI-12", periodType: "YEAR", mobile: true },
  { screenId: "UI-05", periodType: "YEAR", mobile: true },
  { screenId: "UI-11", periodType: "YEAR", mobile: true },
  { screenId: "UI-28", periodType: "YEAR", mobile: true },
  { screenId: "UI-17", periodType: "YEAR", mobile: true },
  { screenId: "UI-29", periodType: "NONE", mobile: true },
  { screenId: "UI-33", periodType: "NONE", mobile: true },
  { screenId: "UI-13", periodType: "YEAR", mobile: true },
  { screenId: "UI-03", periodType: "YEAR", mobile: true },
  { screenId: "UI-04", periodType: "YEAR", mobile: true },
  { screenId: "UI-19", periodType: "MONTH", mobile: true },
  { screenId: "UI-32", periodType: "NONE", mobile: true },
  { screenId: "UI-10", periodType: "YEAR", mobile: false },
  { screenId: "UI-07", periodType: "YEAR_CUM", mobile: true },
  { screenId: "UI-08", periodType: "YEAR_CUM", mobile: true },
  { screenId: "UI-09", periodType: "YEAR_CUM", mobile: true },
  { screenId: "UI-18", periodType: "YEAR_CUM", mobile: true },
  { screenId: "UI-20", periodType: "MONTH", mobile: false },
  { screenId: "UI-21", periodType: "YEAR", mobile: true },
  { screenId: "UI-14", periodType: "MONTH", mobile: false },
  { screenId: "UI-26", periodType: "NONE", mobile: true },
  { screenId: "UI-30", periodType: "NONE", mobile: true },
  { screenId: "UI-23", periodType: "NONE", mobile: false },
  { screenId: "UI-24", periodType: "NONE", mobile: false },
  { screenId: "UI-25", periodType: "NONE", mobile: false },
  { screenId: "UI-27", periodType: "NONE", mobile: false },
];
export const screenById = (id: string) =>
  screenRegistry.find((screen) => screen.screenId === id);
export const sharedRoutes = screenRegistry.map(
  (screen) => `/${screen.screenId}`,
);
