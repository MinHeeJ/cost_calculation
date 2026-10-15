import { beforeEach, expect, it, vi } from "vitest";
import { useNavigation } from "./navigation";
import { screenRegistry } from "./registry";
import { useShortcuts } from "./preferences";

const yes = async () => true;
beforeEach(() => {
  useNavigation.setState({
    active: "UI-16",
    tabs: ["UI-16"],
    visits: ["UI-16"],
    dirty: {},
    screenState: {},
    busy: false,
  });
  useShortcuts.setState({ favorites: [], recent: [] });
});
it("기존 탭은 중복 없이 활성화하고 일반 화면의 보존 상태는 왕복 후 유지한다", async () => {
  await useNavigation.getState().open("UI-03", yes);
  useNavigation.getState().setScreenState("UI-03", { query: "보존할 입력" });
  await useNavigation.getState().open("UI-19", yes);
  await useNavigation.getState().open("UI-03", yes);
  expect(useNavigation.getState().tabs).toEqual(["UI-16", "UI-03", "UI-19"]);
  expect(useNavigation.getState().screenState["UI-03"]).toEqual({
    query: "보존할 입력",
  });
});
it("대시보드를 포함한 8개 한도에서 LRU 확인 후 dirty 확인하며 취소는 모든 상태를 보존한다", async () => {
  const ids = screenRegistry.slice(1, 9).map((value) => value.screenId);
  for (const id of ids.slice(0, 7))
    await useNavigation.getState().open(id, yes);
  const state = useNavigation.getState();
  state.setDirty(ids[0], true);
  state.setDirty(state.active, true);
  state.setScreenState(state.active, { text: "작성 중" });
  const before = useNavigation.getState();
  const confirm = vi
    .fn()
    .mockResolvedValueOnce(true)
    .mockResolvedValueOnce(false);
  expect(await state.open(ids[7], confirm)).toBe(false);
  expect(confirm.mock.calls[0][0]).toEqual({ kind: "limit", screenId: ids[0] });
  expect(confirm.mock.calls[1][0].kind).toBe("dirty");
  const after = useNavigation.getState();
  expect(after.tabs).toEqual(before.tabs);
  expect(after.visits).toEqual(before.visits);
  expect(after.active).toBe(before.active);
  expect(after.screenState).toEqual(before.screenState);
  expect(after.dirty).toEqual(before.dirty);
});
it("모든 확인을 마친 뒤 한 번에 교체하며 대시보드는 퇴출하지 않는다", async () => {
  const ids = screenRegistry.slice(1, 9).map((value) => value.screenId);
  for (const id of ids.slice(0, 7))
    await useNavigation.getState().open(id, yes);
  useNavigation.getState().setDirty(ids[0], true);
  const confirm = vi.fn().mockResolvedValue(true);
  expect(await useNavigation.getState().open(ids[7], confirm)).toBe(true);
  expect(useNavigation.getState().tabs).toHaveLength(8);
  expect(useNavigation.getState().tabs).not.toContain(ids[0]);
  expect(useNavigation.getState().tabs).toContain("UI-16");
  expect(confirm.mock.calls.map(([q]) => q.kind)).toEqual(["limit", "dirty"]);
  expect(await useNavigation.getState().close("UI-16", yes)).toBe(false);
});
it("비활성 닫기는 현재 위치 유지, 활성 닫기는 남은 최근 탭으로 이동한다", async () => {
  for (const id of ["UI-03", "UI-19", "UI-01"])
    await useNavigation.getState().open(id, yes);
  await useNavigation.getState().close("UI-03", yes);
  expect(useNavigation.getState().active).toBe("UI-01");
  await useNavigation.getState().close("UI-01", yes);
  expect(useNavigation.getState().active).toBe("UI-19");
  await useNavigation.getState().close("UI-19", yes);
  expect(useNavigation.getState().active).toBe("UI-16");
});
it("확인 대기 중에는 추가 이탈을 거부하고 탭 상태를 변경하지 않는다", async () => {
  await useNavigation.getState().open("UI-03", yes);
  useNavigation.getState().setDirty("UI-03", true);
  let answer!: (value: boolean) => void;
  const pending = useNavigation.getState().open(
    "UI-19",
    () =>
      new Promise((resolve) => {
        answer = resolve;
      }),
  );
  expect(useNavigation.getState().active).toBe("UI-03");
  expect(await useNavigation.getState().open("UI-01", yes)).toBe(false);
  answer(false);
  await pending;
  expect(useNavigation.getState().tabs).toEqual(["UI-16", "UI-03"]);
});
it("즐겨찾기 11번째는 기존 순서를 유지하고 최근 화면은 중복 없는 최신 5개다", () => {
  const ids = screenRegistry.slice(0, 11).map((value) => value.screenId);
  for (const id of ids.slice(0, 10))
    expect(useShortcuts.getState().toggleFavorite(id)).toBe(true);
  expect(useShortcuts.getState().toggleFavorite(ids[10])).toBe(false);
  expect(useShortcuts.getState().favorites).toEqual(ids.slice(0, 10));
  useShortcuts.getState().reorderFavorite(ids[0], ids[1]);
  expect(JSON.parse(localStorage.getItem("si.favorites")!)[0]).toBe(ids[1]);
  for (const id of ids) useShortcuts.getState().visit(id);
  useShortcuts.getState().visit(ids[8]);
  expect(useShortcuts.getState().recent).toEqual([
    ids[8],
    ids[10],
    ids[9],
    ids[7],
    ids[6],
  ]);
  expect(JSON.parse(localStorage.getItem("si.recentScreens")!)).toEqual(
    useShortcuts.getState().recent,
  );
});
