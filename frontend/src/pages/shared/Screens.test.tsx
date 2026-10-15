import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { PlaceholderScreen } from "./PlaceholderScreen";
import { PeriodBar } from "./PeriodBar";
import { Sidebar } from "./Sidebar";
import { Favorites } from "./Favorites";
import { ScreenTabs } from "./ScreenTabs";
import { screenRegistry, screenById, sharedRoutes } from "./registry";
import { useNavigation } from "./navigation";
import { useShortcuts } from "./preferences";
import { seoulToday, usePeriods } from "./periods";
import type { MenuNode } from "../../api/types";

const fixture: MenuNode[] = [
  {
    menuId: 1,
    name: "원가·수익",
    screenId: null,
    icon: "chart",
    mobile: true,
    children: [
      {
        menuId: 2,
        name: "개인 원가·수익표",
        screenId: "UI-03",
        icon: null,
        mobile: true,
        children: [],
      },
      {
        menuId: 3,
        name: "지급 관리",
        screenId: "UI-20",
        icon: null,
        mobile: false,
        children: [],
      },
    ],
  },
];
function respond(data: unknown) {
  return new Response(
    JSON.stringify({ success: true, data, meta: { requestId: "server-id" } }),
    {
      status: 200,
      headers: {
        "X-Request-Id": "server-id",
        "Content-Type": "application/json",
      },
    },
  );
}
beforeEach(() => {
  usePeriods.setState({ values: {} });
  useShortcuts.setState({ favorites: [], recent: [] });
  useNavigation.setState({
    active: "UI-16",
    tabs: ["UI-16"],
    visits: ["UI-16"],
    dirty: {},
    busy: false,
  });
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(respond([{ month: "2026-09", status: "CLOSED" }])),
  );
});
afterEach(() => vi.unstubAllGlobals());
it.each(screenRegistry)(
  "$screenId는 하나의 자리 화면을 사용하고 업무 명령을 만들지 않는다",
  (definition) => {
    render(
      <PlaceholderScreen
        screen={definition}
        name={definition.screenId}
        onDashboard={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("heading", { name: definition.screenId }),
    ).toBeVisible();
    expect(screen.getByText("이 화면은 다음 단계에서 만듭니다.")).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /저장|삭제|등록/ }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "조회 기간" }) !== null).toBe(
      definition.periodType !== "NONE",
    );
  },
);
it("route는 정확한 31개 UI 경로이고 SCR 및 모달 별칭을 추가하지 않는다", () => {
  expect(sharedRoutes).toHaveLength(31);
  expect(new Set(sharedRoutes).size).toBe(31);
  expect(sharedRoutes).not.toContain("/UI-06");
  expect(sharedRoutes).not.toContain("/UI-22");
  expect(sharedRoutes.every((value) => /^\/UI-[0-9]{2}$/.test(value))).toBe(
    true,
  );
});
it.each(screenRegistry.filter((value) => !value.mobile))(
  "$screenId 모바일 직접 주소는 PC 안내와 대시보드만 제공한다",
  (definition) => {
    const back = vi.fn();
    render(
      <PlaceholderScreen
        screen={definition}
        name="화면"
        mobile
        onDashboard={back}
      />,
    );
    expect(
      screen.getByRole("heading", { name: "PC에서 이용해 주세요" }),
    ).toBeVisible();
    expect(
      screen.queryByText("이 화면은 다음 단계에서 만듭니다."),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText("선택 연월")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "대시보드로" }));
    expect(back).toHaveBeenCalledTimes(1);
  },
);
it("모바일 메뉴는 N 화면을 숨기고 Y 화면의 선택 ID를 전달한다", () => {
  const open = vi.fn();
  render(<Sidebar menus={fixture} active="UI-03" mobile onOpen={open} />);
  expect(
    screen.queryByRole("link", { name: "지급 관리" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("link", { name: "개인 원가·수익표" }));
  expect(open).toHaveBeenCalledWith("UI-03");
});
it("그룹은 한 번에 하나만 펼치고 접힌 메뉴는 hover와 keyboard focus로 옆 메뉴를 연다", () => {
  const second: MenuNode = {
    menuId: 9,
    name: "기준정보",
    screenId: null,
    icon: "db",
    mobile: true,
    children: [
      {
        menuId: 10,
        name: "개발자 풀",
        screenId: "UI-02",
        icon: null,
        mobile: true,
        children: [],
      },
    ],
  };
  const { rerender } = render(
    <Sidebar menus={[...fixture, second]} active="UI-03" onOpen={vi.fn()} />,
  );
  fireEvent.click(screen.getByRole("button", { name: "기준정보" }));
  expect(
    screen.queryByRole("link", { name: "개인 원가·수익표" }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "개발자 풀" })).toBeVisible();
  rerender(
    <Sidebar menus={fixture} active="UI-03" collapsed onOpen={vi.fn()} />,
  );
  fireEvent.focus(screen.getByRole("button", { name: "원가·수익" }));
  expect(screen.getByRole("link", { name: "개인 원가·수익표" })).toBeVisible();
});
it("제목의 별은 등록과 해제를 저장하며 11번째 항목에서는 제거 안내를 표시한다", async () => {
  render(
    <PlaceholderScreen
      screen={screenById("UI-03")!}
      name="개인 원가·수익표"
      onDashboard={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "즐겨찾기 추가" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "즐겨찾기 해제" })).toBeEnabled(),
  );
  expect(JSON.parse(localStorage.getItem("si.favorites")!)).toEqual(["UI-03"]);
  fireEvent.click(screen.getByRole("button", { name: "즐겨찾기 해제" }));
  await waitFor(() => expect(useShortcuts.getState().favorites).toEqual([]));
});
it("즐겨찾기 순서 변경은 drag와 키보드용 버튼을 지원한다", () => {
  useShortcuts.setState({ favorites: ["UI-03", "UI-20"] });
  render(
    <Favorites menus={fixture[0].children} active="UI-03" onOpen={vi.fn()} />,
  );
  fireEvent.click(screen.getByRole("button", { name: "지급 관리 위로" }));
  expect(useShortcuts.getState().favorites).toEqual(["UI-20", "UI-03"]);
  const region = screen.getByRole("region", { name: "즐겨찾기" });
  const source = within(region).getByRole("link", {
    name: "지급 관리",
  }).parentElement!;
  const target = within(region).getByRole("link", {
    name: "개인 원가·수익표",
  }).parentElement!;
  fireEvent.dragStart(source);
  fireEvent.drop(target);
  expect(useShortcuts.getState().favorites).toEqual(["UI-03", "UI-20"]);
});
it("화면 탭은 dirty 표시와 닫기를 제공하지만 대시보드는 닫기 버튼이 없다", () => {
  useNavigation.setState({
    tabs: ["UI-16", "UI-03"],
    active: "UI-03",
    dirty: { "UI-03": true },
  });
  const close = vi.fn();
  render(
    <ScreenTabs menus={fixture[0].children} onOpen={vi.fn()} onClose={close} />,
  );
  expect(
    screen.getByRole("link", { name: "● 개인 원가·수익표" }),
  ).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "UI-16 탭 닫기" }),
  ).not.toBeInTheDocument();
  fireEvent.click(
    screen.getByRole("button", { name: "개인 원가·수익표 탭 닫기" }),
  );
  expect(close).toHaveBeenCalledWith("UI-03");
});
it("기간은 서울 오늘로 시작하고 화면별 연도와 누계 종료월을 독립적으로 기억한다", async () => {
  expect(seoulToday(new Date("2026-09-30T16:00:00Z"))).toEqual({
    year: 2026,
    month: 10,
  });
  usePeriods.setState({
    values: {
      "UI-03": { year: 2026, month: 10 },
      "UI-07": { year: 2025, month: 9 },
    },
  });
  const { rerender } = render(<PeriodBar screen={screenById("UI-03")!} />);
  fireEvent.click(screen.getByRole("button", { name: "다음 연도" }));
  await waitFor(() =>
    expect(screen.getByLabelText("선택 연도")).toHaveValue(2027),
  );
  rerender(<PeriodBar screen={screenById("UI-07")!} />);
  fireEvent.change(screen.getByLabelText("누계 종료월"), {
    target: { value: "8" },
  });
  expect(screen.getByText("2025년 1월 ~ 8월 누계")).toBeVisible();
  rerender(<PeriodBar screen={screenById("UI-03")!} />);
  expect(screen.getByLabelText("선택 연도")).toHaveValue(2027);
  expect(localStorage.getItem("si.periods")).toBeNull();
});
it("즐겨찾기 드래그 취소 뒤의 드롭은 이전 항목을 이동하지 않는다", () => {
  useShortcuts.setState({ favorites: ["UI-03", "UI-20"] });
  render(
    <Favorites menus={fixture[0].children} active="UI-03" onOpen={vi.fn()} />,
  );
  const region = screen.getByRole("region", { name: "즐겨찾기" });
  const source = within(region).getByRole("link", {
    name: "개인 원가·수익표",
  }).parentElement!;
  const target = within(region).getByRole("link", {
    name: "지급 관리",
  }).parentElement!;
  fireEvent.dragStart(source);
  fireEvent.dragEnd(source);
  fireEvent.drop(target);
  expect(useShortcuts.getState().favorites).toEqual(["UI-03", "UI-20"]);
});

it.each(["from", "to"])(
  "월 조회 %s 오류는 선택 연월에 연결하고 성공 후 제거한다",
  async (field) => {
    usePeriods.setState({ values: { "UI-19": { year: 2026, month: 9 } } });
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: false,
          code: "VALIDATION_ERROR",
          message: "연월 입력을 확인해 주세요.",
          field,
          requestId: "month-error",
          meta: { requestId: "month-error" },
        }),
        { status: 400, headers: { "X-Request-Id": "month-error" } },
      ),
    );
    render(<PeriodBar screen={screenById("UI-19")!} />);
    await screen.findByRole("alert");
    const input = screen.getByLabelText("선택 연월");
    expect(input).toHaveAccessibleDescription("연월 입력을 확인해 주세요.");
    expect(input).toHaveValue("2026-09");
    vi.mocked(fetch).mockResolvedValueOnce(
      respond([{ month: "2026-09", status: "OPEN" }]),
    );
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByText("작업중")).toBeVisible();
    expect(input).not.toHaveAccessibleDescription();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  },
);

it("월 상태는 실제 선택 월로 요청하고 CLOSED 안내를 OPEN 이동 시 제거한다", async () => {
  usePeriods.setState({ values: { "UI-19": { year: 2026, month: 9 } } });
  const fetcher = vi.mocked(fetch);
  fetcher
    .mockResolvedValueOnce(respond([{ month: "2026-09", status: "CLOSED" }]))
    .mockResolvedValueOnce(respond([{ month: "2026-10", status: "OPEN" }]));
  render(<PeriodBar screen={screenById("UI-19")!} />);
  expect(await screen.findByText("마감")).toBeVisible();
  expect(
    screen.getByText(
      "2026-09는 마감되었습니다. 고칠 내용은 다음 달 전월 조정으로 입력하세요.",
    ),
  ).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "다음 월" }));
  expect(await screen.findByText("작업중")).toBeVisible();
  expect(screen.queryByText("마감")).not.toBeInTheDocument();
  expect(fetcher.mock.calls[1][0]).toBe(
    "/api/month-close?from=2026-10&to=2026-10",
  );
});
