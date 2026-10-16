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
const codeGroups = [
  {
    cd_grp_id: "JOB",
    cd_grp_nm: "직무",
    cd_grp_se_cd: "BIZ",
    attr1_nm: "기본 단가(원)",
    attr2_nm: null,
    attr3_nm: null,
    sort_seq: 1,
    use_yn: "Y",
    code_count: 1,
  },
];
const codeRows = [
  {
    cd_grp_id: "JOB",
    dtl_cd: "BE",
    cd_nm: "백엔드",
    attr1_val: "200000",
    attr2_val: null,
    attr3_val: null,
    sort_seq: 1,
    use_yn: "Y",
    aply_bgn_dt: null,
  },
];
function ok(data: unknown) {
  return new Response(
    JSON.stringify({
      success: true,
      data,
      meta: { requestId: "route-request" },
    }),
    {
      headers: { "X-Request-Id": "route-request" },
    },
  );
}
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
    vi.fn().mockImplementation((path: string) => {
      if (path === "/api/menus") return Promise.resolve(ok(menus));
      if (path === "/api/admin/code-groups")
        return Promise.resolve(ok(codeGroups));
      if (path === "/api/admin/code-groups/JOB/codes")
        return Promise.resolve(ok(codeRows));
      if (path === "/api/admin/excel-maps") return Promise.resolve(ok([]));
      if (path === "/api/admin/operating-standards") {
        return Promise.resolve(ok({ items: [], last_closed_ym: "2026-09" }));
      }
      if (path.startsWith("/api/admin/code-history?")) {
        return Promise.resolve(ok({ items: [], total: 0, page: 1, size: 50 }));
      }
      return Promise.reject(new Error(`Unexpected request: ${path}`));
    }),
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
    if (id === "UI-27") {
      expect(await screen.findByDisplayValue("백엔드")).toBeVisible();
      expect(
        within(
          screen.getByRole("tablist", { name: "공통코드 관리" }),
        ).getAllByRole("tab"),
      ).toHaveLength(4);
      expect(
        screen.queryByText("이 화면은 다음 단계에서 만듭니다."),
      ).not.toBeInTheDocument();
    } else {
      expect(
        screen.getByText("이 화면은 다음 단계에서 만듭니다."),
      ).toBeVisible();
    }
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

it("기존 메뉴로 공통코드에 진입해 네 탭의 실제 API를 사용한다", async () => {
  const router = mount("/UI-16");
  await screen.findByRole("heading", { name: "화면 UI-16", level: 1 });
  fireEvent.click(screen.getByRole("button", { name: "업무" }));
  fireEvent.click(screen.getByRole("link", { name: "화면 UI-27" }));
  expect(await screen.findByDisplayValue("백엔드")).toBeVisible();
  expect(router.state.location.pathname).toBe("/UI-27");
  expect(
    screen.getByRole("columnheader", { name: "기본 단가(원)" }),
  ).toBeVisible();
  expect(screen.getByRole("tab", { name: "코드" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(useShortcuts.getState().recent[0]).toBe("UI-27");
  for (const [tab, path] of [
    ["엑셀 표기 대응", "/api/admin/excel-maps"],
    ["기준값", "/api/admin/operating-standards"],
    ["변경 이력", "/api/admin/code-history?"],
  ]) {
    fireEvent.click(screen.getByRole("tab", { name: tab }));
    await waitFor(() =>
      expect(
        vi
          .mocked(fetch)
          .mock.calls.some(([url]) => String(url).startsWith(path)),
      ).toBe(true),
    );
    expect(screen.getByRole("tab", { name: tab })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(router.state.location.pathname).toBe("/UI-27");
    expect(
      await screen.findByText("조건에 맞는 결과가 없습니다"),
    ).toBeVisible();
  }
  expect(vi.mocked(fetch).mock.calls.every(([, init]) => !init?.method)).toBe(
    true,
  );
});

it("공통코드 초안은 메뉴와 브라우저 이동 취소 후 유지되고 승인 후에만 버린다", async () => {
  const router = mount("/UI-27");
  await screen.findByDisplayValue("백엔드");
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "저장 전 이름" },
  });
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "대시보드" }));
  expect(await screen.findByRole("dialog")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(router.state.location.pathname).toBe("/UI-27");
  expect(screen.getByDisplayValue("저장 전 이름")).toBeVisible();
  await act(async () => {
    void router.navigate("/UI-16");
  });
  expect(await screen.findByRole("dialog")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(router.state.location.pathname).toBe("/UI-27");
  expect(screen.getByDisplayValue("저장 전 이름")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "대시보드" }));
  fireEvent.click(await screen.findByRole("button", { name: "나가기" }));
  await waitFor(() => expect(router.state.location.pathname).toBe("/UI-16"));
  expect(useNavigation.getState().dirty["UI-27"]).not.toBe(true);
  expect(screen.queryByTestId("common-code-screen")).not.toBeInTheDocument();
  expect(vi.mocked(fetch).mock.calls.every(([, init]) => !init?.method)).toBe(
    true,
  );
});
