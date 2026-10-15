import { expect, it, vi } from "vitest";
import {
  readPreference,
  writePreference,
  preferenceKeys,
  usePreferences,
} from "../../src/stores/preferences";

it("정확히 네 종류의 si. 설정을 저장하고 복원한다", () => {
  expect(Object.values(preferenceKeys)).toEqual([
    "si.favorites",
    "si.recentScreens",
    "si.menuCollapsed",
    "si.pageSize",
  ]);
  writePreference("favorites", [{ screenId: "UI-16", name: "대시보드" }]);
  writePreference("recentScreens", [{ screenId: "UI-01", name: "프로젝트" }]);
  writePreference("menuCollapsed", true);
  usePreferences.getState().setPageSize(50);
  expect(JSON.parse(localStorage.getItem("si.pageSize")!)).toBe(50);
  expect(readPreference("menuCollapsed", false)).toBe(true);
  expect(readPreference("favorites", [])).toEqual([
    { screenId: "UI-16", name: "대시보드" },
  ]);
  expect(readPreference("recentScreens", [])).toEqual([
    { screenId: "UI-01", name: "프로젝트" },
  ]);
});
it("차단된 localStorage는 메모리로 복구한다", () => {
  vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
    throw new Error("denied");
  });
  vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
    throw new Error("denied");
  });
  writePreference("pageSize", 100);
  expect(readPreference("pageSize", 20)).toBe(100);
});
it("손상된 JSON은 예외를 전파하지 않는다", () => {
  localStorage.setItem("si.menuCollapsed", "{");
  expect(() => readPreference("menuCollapsed", false)).not.toThrow();
});
