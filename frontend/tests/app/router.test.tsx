import { StrictMode } from "react";
import {
  act,
  configure,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { applicationRoutes } from "../../src/app/router";
import { useNavigation } from "../../src/pages/shared/navigation";
import { useShortcuts } from "../../src/pages/shared/preferences";
import { usePeriods } from "../../src/pages/shared/periods";
import type { MenuNode } from "../../src/api/types";

configure({
  getElementError: (message) =>
    new Error(message ?? "요소를 찾을 수 없습니다."),
});

const ids = [
  "UI-16",
  "UI-15",
  "UI-02",
  "UI-01",
  "UI-31",
  "UI-12",
  "UI-05",
  "UI-11",
  "UI-28",
  "UI-17",
  "UI-29",
  "UI-33",
  "UI-13",
  "UI-03",
  "UI-04",
  "UI-19",
  "UI-32",
  "UI-10",
  "UI-07",
  "UI-08",
  "UI-09",
  "UI-18",
  "UI-20",
  "UI-21",
  "UI-14",
  "UI-26",
  "UI-30",
  "UI-23",
  "UI-24",
  "UI-25",
  "UI-27",
];
const desktopOnly = [
  "UI-10",
  "UI-20",
  "UI-14",
  "UI-23",
  "UI-24",
  "UI-25",
  "UI-27",
];
const menus: MenuNode[] = [
  {
    menuId: 100,
    name: "대시보드",
    screenId: null,
    icon: null,
    mobile: true,
    children: [
      {
        menuId: 101,
        name: "화면 UI-16",
        screenId: "UI-16",
        icon: null,
        mobile: true,
        children: [],
      },
    ],
  },
  {
    menuId: 1,
    name: "업무",
    screenId: null,
    icon: null,
    mobile: true,
    children: ids
      .filter((id) => id !== "UI-16")
      .map((id, index) => ({
        menuId: index + 2,
        name: `화면 ${id}`,
        screenId: id,
        icon: null,
        mobile: !desktopOnly.includes(id),
        children: [],
      })),
  },
];
const routers: ReturnType<typeof createMemoryRouter>[] = [];
function mount(path: string, history = [path], index = history.length - 1) {
  const router = createMemoryRouter(applicationRoutes, {
    initialEntries: history,
    initialIndex: index,
  });
  routers.push(router);
  render(
    <StrictMode>
      <RouterProvider router={router} />
    </StrictMode>,
  );
  return router;
}
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
  usePeriods.setState({ values: {} });
  vi.stubGlobal("innerWidth", 1440);
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            success: true,
            data: menus,
            meta: { requestId: "route-request" },
          }),
          { headers: { "X-Request-Id": "route-request" } },
        ),
      ),
    ),
  );
});
afterEach(() => {
  routers.splice(0).forEach((router) => router.dispose());
  vi.unstubAllGlobals();
});

it.each(ids)(
  "무인증 직접 진입 %s는 공유 제목과 자리 화면으로 연결한다",
  async (id) => {
    const router = mount(`/${id}`);
    expect(
      await screen.findByRole("heading", { name: `화면 ${id}`, level: 1 }),
    ).toBeVisible();
    expect(screen.getByText("이 화면은 다음 단계에서 만듭니다.")).toBeVisible();
    expect(router.state.location.pathname).toBe(`/${id}`);
    expect(
      screen.getAllByRole("link", { name: "프로젝트·원가 관리" }),
    ).toHaveLength(1);
  },
);
it("루트는 대시보드로 연결하고 API 메뉴로 같은 공유 shell 안에서 이동한다", async () => {
  const router = mount("/");
  expect(
    await screen.findByRole("heading", { name: "화면 UI-16", level: 1 }),
  ).toBeVisible();
  expect(router.state.location.pathname).toBe("/UI-16");
  fireEvent.click(screen.getByRole("button", { name: "업무" }));
  fireEvent.click(await screen.findByRole("link", { name: "화면 UI-03" }));
  expect(
    await screen.findByRole("heading", { name: "화면 UI-03", level: 1 }),
  ).toBeVisible();
  expect(router.state.location.pathname).toBe("/UI-03");
  expect(useShortcuts.getState().recent[0]).toBe("UI-03");
});
it.each([
  "/UI-06",
  "/UI-22",
  "/SCR-DASHBOARD",
  "/SCR-COMMON",
  "/SCR-PERSON",
  "/SCR-MONTH",
])("추가 업무 및 설계 별칭 %s는 화면으로 등록하지 않는다", async (path) => {
  mount(path);
  expect(
    await screen.findByRole("heading", { name: "화면을 찾을 수 없습니다." }),
  ).toBeVisible();
  expect(
    screen.queryByText("이 화면은 다음 단계에서 만듭니다."),
  ).not.toBeInTheDocument();
});
it.each(desktopOnly)(
  "모바일 직접 주소 %s는 PC 안내와 대시보드 이동을 제공한다",
  async (id) => {
    vi.stubGlobal("innerWidth", 390);
    mount(`/${id}`);
    expect(await screen.findByText("PC에서 이용해 주세요")).toBeVisible();
    expect(screen.getByRole("button", { name: "대시보드로" })).toBeVisible();
    expect(
      within(screen.getByRole("navigation", { name: "화면 메뉴" })).queryByRole(
        "link",
        { name: `화면 ${id}` },
      ),
    ).not.toBeInTheDocument();
  },
);
it("뒤로 이동 취소는 URL·입력·탭·최근 순서를 보존하고 승인 후에만 이동한다", async () => {
  const router = mount("/UI-16");
  await screen.findByRole("heading", { name: "화면 UI-16", level: 1 });
  fireEvent.click(screen.getByRole("button", { name: "업무" }));
  fireEvent.click(await screen.findByRole("link", { name: "화면 UI-03" }));
  await screen.findByRole("heading", { name: "화면 UI-03", level: 1 });
  act(() => {
    useNavigation.getState().setDirty("UI-03", true);
    useNavigation.getState().setScreenState("UI-03", { value: "보존할 입력" });
  });
  const before = useNavigation.getState();
  const recent = useShortcuts.getState().recent;
  await act(async () => {
    await router.navigate(-1);
  });
  expect(
    [...router.state.blockers.values()].map((value) => value.state),
  ).toContain("blocked");
  expect(useNavigation.getState().active).toBe("UI-03");
  expect(useNavigation.getState().dirty["UI-03"]).toBe(true);
  expect(await screen.findByRole("dialog")).toBeVisible();
  expect(router.state.location.pathname).toBe("/UI-03");
  fireEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(router.state.location.pathname).toBe("/UI-03");
  expect(useNavigation.getState().screenState).toEqual(before.screenState);
  expect(useNavigation.getState().tabs).toEqual(before.tabs);
  expect(useNavigation.getState().visits).toEqual(before.visits);
  expect(useShortcuts.getState().recent).toEqual(recent);
  await act(async () => {
    await router.navigate(-1);
  });
  fireEvent.click(await screen.findByRole("button", { name: "나가기" }));
  await waitFor(() => expect(router.state.location.pathname).toBe("/UI-16"));
  expect(useNavigation.getState().active).toBe("UI-16");
  expect(useNavigation.getState().screenState["UI-03"]).toBeUndefined();
  await act(async () => {
    await router.navigate(1);
  });
  await waitFor(() => expect(router.state.location.pathname).toBe("/UI-03"));
  expect(
    await screen.findByRole("heading", { name: "화면 UI-03", level: 1 }),
  ).toBeVisible();
});
