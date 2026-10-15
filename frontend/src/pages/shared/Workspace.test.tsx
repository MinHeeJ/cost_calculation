import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { SharedWorkspace } from "./SharedWorkspace";
import {
  UnsavedChangesGuard,
  useNavigationConfirmation,
} from "./UnsavedChangesGuard";
import { useNavigation } from "./navigation";
import { useShortcuts } from "./preferences";
import { usePeriods } from "./periods";
import type { MenuNode } from "../../api/types";

const menus: MenuNode[] = [
  {
    menuId: 1,
    name: "대시보드",
    icon: "grid",
    screenId: null,
    mobile: true,
    children: [
      {
        menuId: 11,
        name: "대시보드",
        icon: null,
        screenId: "UI-16",
        mobile: true,
        children: [],
      },
    ],
  },
  {
    menuId: 2,
    name: "원가·수익",
    icon: "chart",
    screenId: null,
    mobile: true,
    children: [
      {
        menuId: 12,
        name: "개인 원가·수익표",
        icon: null,
        screenId: "UI-03",
        mobile: true,
        children: [],
      },
    ],
  },
];
function ok(data: unknown) {
  return new Response(
    JSON.stringify({ success: true, data, meta: { requestId: "request-123" } }),
    { status: 200, headers: { "X-Request-Id": "request-123" } },
  );
}
function Location() {
  return <output aria-label="주소">{useLocation().pathname}</output>;
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
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation(() => Promise.resolve(ok(menus))),
  );
});
afterEach(() => vi.unstubAllGlobals());
it("루트는 대시보드로 이동하며 API 메뉴와 최근 바로가기로 실제 제목 및 주소에 도달한다", async () => {
  render(
    <MemoryRouter initialEntries={["/"]}>
      <SharedWorkspace />
      <Location />
    </MemoryRouter>,
  );
  expect(
    await screen.findByRole("heading", { name: "대시보드", level: 1 }),
  ).toBeVisible();
  expect(screen.getByLabelText("주소")).toHaveTextContent("/UI-16");
  fireEvent.click(screen.getByRole("button", { name: "원가·수익" }));
  fireEvent.click(
    await screen.findByRole("link", { name: "개인 원가·수익표" }),
  );
  expect(
    await screen.findByRole("heading", { name: "개인 원가·수익표", level: 1 }),
  ).toBeVisible();
  expect(screen.getByLabelText("주소")).toHaveTextContent("/UI-03");
  expect(screen.getByText("이 화면은 다음 단계에서 만듭니다.")).toBeVisible();
  expect(useShortcuts.getState().recent[0]).toBe("UI-03");
  fireEvent.click(screen.getByRole("button", { name: "대시보드" }));
  await waitFor(() =>
    expect(screen.getByLabelText("주소")).toHaveTextContent("/UI-16"),
  );
  const recent = screen.getByRole("region", { name: "최근 본 화면" });
  const shortcut = within(recent).getByRole("link", {
    name: "개인 원가·수익표",
  });
  expect(shortcut).toHaveAttribute("href", "/UI-03");
  fireEvent.click(shortcut);
  expect(
    await screen.findByRole("heading", { name: "개인 원가·수익표", level: 1 }),
  ).toBeVisible();
  expect(screen.getByLabelText("주소")).toHaveTextContent("/UI-03");
  expect(
    useShortcuts.getState().recent.filter((id) => id === "UI-03"),
  ).toHaveLength(1);
  expect(fetch).toHaveBeenCalledTimes(1);
});
it("메뉴 오류는 서버 문의번호와 수동 재시도만 제공하고 처리 중 중복 요청을 막는다", async () => {
  const fetcher = vi.mocked(fetch);
  fetcher.mockRejectedValueOnce(new Error("offline"));
  render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <SharedWorkspace />
    </MemoryRouter>,
  );
  const retry = (
    await screen.findAllByRole("button", { name: "다시 시도" })
  )[0];
  expect(screen.getAllByText(/클라이언트 문의/).length).toBeGreaterThan(0);
  expect(fetcher).toHaveBeenCalledTimes(1);
  let resolve!: (response: Response) => void;
  fetcher.mockImplementationOnce(
    () =>
      new Promise((answer) => {
        resolve = answer;
      }),
  );
  const messages = screen
    .getAllByRole("alert")
    .map((alert) => alert.textContent);
  fireEvent.click(retry);
  fireEvent.click(retry);
  expect(fetcher).toHaveBeenCalledTimes(2);
  expect(
    screen.getAllByRole("alert").map((alert) => alert.textContent),
  ).toEqual(messages);
  for (const button of screen.getAllByRole("button", { name: "다시 시도" })) {
    expect(button).toBeDisabled();
  }
  resolve(ok(menus));
  expect(
    await screen.findByRole("heading", { name: "대시보드", level: 1 }),
  ).toBeVisible();
});
it("dirty 취소 시 주소와 입력 상태 유지, 승인 시에만 이탈한다", async () => {
  render(
    <MemoryRouter initialEntries={["/UI-03"]}>
      <SharedWorkspace />
      <Location />
    </MemoryRouter>,
  );
  expect(
    await screen.findByRole("heading", { name: "개인 원가·수익표", level: 1 }),
  ).toBeVisible();
  useNavigation.getState().setDirty("UI-03", true);
  useNavigation.getState().setScreenState("UI-03", { value: "작성 내용" });
  fireEvent.click(screen.getByRole("button", { name: "대시보드" }));
  expect(await screen.findByRole("dialog")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(screen.getByLabelText("주소")).toHaveTextContent("/UI-03");
  expect(useNavigation.getState().screenState["UI-03"]).toEqual({
    value: "작성 내용",
  });
  fireEvent.click(screen.getByRole("link", { name: "프로젝트·원가 관리" }));
  expect(await screen.findByRole("dialog")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "나가기" }));
  await waitFor(() =>
    expect(screen.getByLabelText("주소")).toHaveTextContent("/UI-16"),
  );
  expect(useNavigation.getState().dirty["UI-03"]).toBeUndefined();
});
it("beforeunload는 dirty 입력이 있는 동안만 브라우저 이탈 경고를 요청한다", () => {
  useNavigation.setState({ dirty: { "UI-03": true } });
  const { unmount } = render(<UnsavedChangesGuard />);
  const event = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(event);
  expect(event.defaultPrevented).toBe(true);
  unmount();
  const cleanEvent = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(cleanEvent);
  expect(cleanEvent.defaultPrevented).toBe(false);
});
it("실제 확인 창은 9번째 화면 요청에서 열리고 취소 시 8개 탭이 유지된다", async () => {
  function Harness() {
    const { confirm, dialog } = useNavigationConfirmation();
    return (
      <>
        <button
          onClick={() => void useNavigation.getState().open("UI-33", confirm)}
        >
          새 화면
        </button>
        {dialog}
      </>
    );
  }
  const ids = [
    "UI-16",
    "UI-03",
    "UI-19",
    "UI-01",
    "UI-02",
    "UI-15",
    "UI-31",
    "UI-12",
  ];
  useNavigation.setState({ tabs: ids, visits: ids, active: "UI-12" });
  render(<Harness />);
  fireEvent.click(screen.getByRole("button", { name: "새 화면" }));
  expect(await screen.findByRole("dialog")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  await waitFor(() => expect(useNavigation.getState().busy).toBe(false));
  expect(useNavigation.getState().tabs).toEqual(ids);
});
