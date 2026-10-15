import { afterEach, expect, it, vi } from "vitest";
afterEach(() => vi.restoreAllMocks());
it("파손된 설정이나 잘못된 타입은 빈 목록으로 복구한다", async () => {
  localStorage.setItem("si.favorites", "{invalid");
  localStorage.setItem("si.recentScreens", '{"wrong":true}');
  vi.resetModules();
  const { useShortcuts } = await import("./preferences");
  expect(useShortcuts.getState().favorites).toEqual([]);
  expect(useShortcuts.getState().recent).toEqual([]);
});
it("저장소 차단 중에도 메모리 즐겨찾기와 최근 목록은 사용할 수 있다", async () => {
  vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
    throw new Error("denied");
  });
  vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
    throw new Error("denied");
  });
  vi.resetModules();
  const { useShortcuts } = await import("./preferences");
  useShortcuts.setState({ favorites: [], recent: [] });
  expect(useShortcuts.getState().toggleFavorite("UI-03")).toBe(true);
  useShortcuts.getState().visit("UI-03");
  expect(useShortcuts.getState().favorites).toEqual(["UI-03"]);
  expect(useShortcuts.getState().recent).toEqual(["UI-03"]);
});
