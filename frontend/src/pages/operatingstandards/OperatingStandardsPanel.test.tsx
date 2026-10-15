import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { OperatingStandardsPanel } from "./OperatingStandardsPanel";
import { saveOperatingStandard } from "./api";
import { useNavigation } from "../shared/navigation";
import { RequestError } from "../../api/client";

const current = {
  oprt_std_id: "VAT_RATE",
  aply_bgn_ym: "2000-01",
  oprt_std_nm: "부가세율",
  num_val: 10,
  txt_val: null,
  unit_nm: "%",
  use_plc_desc: "세액 계산",
};
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
function failure(field: string | null = null) {
  return new Response(
    JSON.stringify({
      success: false,
      code: "400",
      message: "올바른 값을 입력하세요.",
      field,
      requestId: "server-id",
      meta: { requestId: "server-id" },
    }),
    { status: 400, headers: { "X-Request-Id": "server-id" } },
  );
}
function done() {
  return new Response(null, {
    status: 204,
    headers: { "X-Request-Id": "server-id" },
  });
}
beforeEach(() => {
  useNavigation.setState({
    dirty: {},
    active: "UI-27",
    tabs: ["UI-16", "UI-27"],
    busy: false,
  });
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(
          respond({ items: [current], last_closed_ym: "2026-09" }),
        ),
      ),
  );
});
afterEach(() => vi.unstubAllGlobals());

async function openEdit() {
  render(<OperatingStandardsPanel />);
  fireEvent.click(await screen.findByRole("button", { name: "값 변경" }));
  return screen.getByRole("dialog");
}
function enter(value = "11", month = "2026-11") {
  fireEvent.change(screen.getByLabelText("새 값"), { target: { value } });
  fireEvent.change(screen.getByLabelText("적용 시작월"), {
    target: { value: month },
  });
}
it("API 현재값·시작월·사용처를 표시하고 기준값 변경 경로를 제공한다", async () => {
  render(<OperatingStandardsPanel />);
  expect(await screen.findByText("부가세율")).toBeVisible();
  expect(screen.getByText("10 %")).toBeVisible();
  expect(screen.getByText("2000-01")).toBeVisible();
  expect(screen.getByText("세액 계산")).toBeVisible();
  expect(fetch).toHaveBeenCalledWith(
    "/api/admin/operating-standards",
    expect.anything(),
  );
  expect(screen.getByRole("button", { name: "값 변경" })).toBeEnabled();
});
it("로딩을 표시하고 빈 결과를 데이터로 대체하지 않는다", async () => {
  vi.mocked(fetch).mockResolvedValueOnce(
    respond({ items: [], last_closed_ym: null }),
  );
  render(<OperatingStandardsPanel />);
  expect(screen.getByRole("status", { name: "불러오는 중" })).toBeVisible();
  expect(await screen.findByText("조건에 맞는 결과가 없습니다")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "값 변경" }),
  ).not.toBeInTheDocument();
});
it("조회 오류를 빈 목록으로 보이지 않고 다시 시도한다", async () => {
  vi.mocked(fetch).mockResolvedValueOnce(failure());
  render(<OperatingStandardsPanel />);
  expect(await screen.findByRole("alert")).toHaveTextContent("server-id");
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("부가세율")).toBeVisible();
});
it("서버 마지막 마감월 다음달을 하한으로 쓰고 마감월은 저장하지 않는다", async () => {
  await openEdit();
  expect(screen.getByLabelText("적용 시작월")).toHaveAttribute(
    "min",
    "2026-10",
  );
  expect(screen.getByLabelText("적용 시작월")).toHaveValue("");
  enter("11", "2026-09");
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("마감월 다음 달");
  expect(fetch).toHaveBeenCalledTimes(1);
});
it("204를 JSON parse 없이 처리하고 실제 선택키와 값만 저장한 후 현재값을 재조회한다", async () => {
  await openEdit();
  vi.mocked(fetch).mockResolvedValueOnce(done());
  enter();
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findByText("기준값을 저장했습니다.")).toBeVisible();
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
  const [url, init] = vi.mocked(fetch).mock.calls[1];
  expect(url).toBe("/api/admin/operating-standards/VAT_RATE");
  expect(init?.method).toBe("PUT");
  expect(JSON.parse(String(init?.body))).toEqual({
    aply_bgn_ym: "2026-11",
    num_val: 11,
  });
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByText("10 %")).toBeVisible();
  expect(useNavigation.getState().dirty["UI-27"]).toBe(false);
});
it("저장 성공 뒤 GET 실패 시 재시도는 GET만 수행한다", async () => {
  await openEdit();
  vi.mocked(fetch)
    .mockResolvedValueOnce(done())
    .mockResolvedValueOnce(failure());
  enter();
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByRole("alert");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByText("부가세율");
  expect(
    vi.mocked(fetch).mock.calls.filter(([, init]) => init?.method === "PUT"),
  ).toHaveLength(1);
});
it("저장 오류는 초안·선택 키·필드 오류를 보존한다", async () => {
  await openEdit();
  vi.mocked(fetch).mockResolvedValueOnce(failure("num_val"));
  enter();
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByRole("alert");
  expect(screen.getByLabelText("새 값")).toHaveValue("11");
  expect(screen.getByLabelText("새 값")).toHaveAccessibleDescription(
    "올바른 값을 입력하세요.",
  );
  expect(screen.getByLabelText("적용 시작월")).toHaveValue("2026-11");
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
});
it("취소는 저장하지 않고 dirty를 해제하며 다시 열면 빈 초안이다", async () => {
  await openEdit();
  enter();
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  fireEvent.click(screen.getByRole("button", { name: "값 변경" }));
  expect(screen.getByLabelText("새 값")).toHaveValue("");
  expect(fetch).toHaveBeenCalledTimes(1);
});
it("ESC는 작성창을 닫지 않으며 공통 이동 취소는 초안을 유지한다", async () => {
  const dialog = await openEdit();
  enter();
  fireEvent.keyDown(dialog, { key: "Escape" });
  expect(dialog).toBeVisible();
  const confirm = vi.fn().mockResolvedValue(false);
  expect(await useNavigation.getState().open("UI-16", confirm)).toBe(false);
  expect(confirm).toHaveBeenCalledWith({ kind: "dirty", screenId: "UI-27" });
  expect(useNavigation.getState().active).toBe("UI-27");
  expect(screen.getByLabelText("새 값")).toHaveValue("11");
});
it("미래 포함 전체 이력을 표시하고 저장 버튼 없이 닫는다", async () => {
  render(<OperatingStandardsPanel />);
  await screen.findByText("부가세율");
  vi.mocked(fetch).mockResolvedValueOnce(
    respond([{ ...current, aply_bgn_ym: "2026-11", num_val: 11 }, current]),
  );
  fireEvent.click(screen.getByRole("button", { name: "이력 보기" }));
  const dialog = screen.getByRole("dialog");
  expect(await within(dialog).findByText("2026-11")).toBeVisible();
  expect(within(dialog).getByText("2000-01")).toBeVisible();
  expect(
    within(dialog).queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(vi.mocked(fetch).mock.calls[1][0]).toBe(
    "/api/admin/operating-standards/VAT_RATE/history",
  );
  fireEvent.click(within(dialog).getByRole("button", { name: "닫기" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
});
it("이력 실패는 이력 GET만 재시도하고 빈 이력을 구분한다", async () => {
  render(<OperatingStandardsPanel />);
  await screen.findByText("부가세율");
  vi.mocked(fetch)
    .mockResolvedValueOnce(failure())
    .mockResolvedValueOnce(respond([]));
  fireEvent.click(screen.getByRole("button", { name: "이력 보기" }));
  await screen.findByRole("alert");
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("조건에 맞는 결과가 없습니다")).toBeVisible();
  expect(
    vi
      .mocked(fetch)
      .mock.calls.slice(1)
      .every(([url]) => String(url).endsWith("/history")),
  ).toBe(true);
});
it("204 추적 헤더가 없으면 성공으로 오인하지 않는다", async () => {
  vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
  await expect(
    saveOperatingStandard("VAT_RATE", { aply_bgn_ym: "2026-11", num_val: 11 }),
  ).rejects.toBeInstanceOf(RequestError);
});
it("18,4 새 값은 JavaScript 숫자로 반올림하지 않고 JSON number로 전송한다", async () => {
  vi.mocked(fetch).mockResolvedValueOnce(done());
  await saveOperatingStandard("AMOUNT", {
    aply_bgn_ym: "2026-11",
    num_val: "99999999999999.9999",
  });
  expect(vi.mocked(fetch).mock.calls[0][1]?.body).toBe(
    '{"aply_bgn_ym":"2026-11","num_val":99999999999999.9999}',
  );
});

it("문자값은 숫자로 바꾸거나 메타 필드를 추가하지 않고 전송한다", async () => {
  vi.mocked(fetch).mockResolvedValueOnce(
    respond({
      items: [
        {
          ...current,
          oprt_std_id: "LABEL",
          num_val: null,
          txt_val: "이전",
          unit_nm: null,
        },
      ],
      last_closed_ym: null,
    }),
  );
  await openEdit();
  expect(screen.getByLabelText("적용 시작월")).not.toHaveAttribute("min");
  vi.mocked(fetch).mockResolvedValueOnce(done());
  enter("새 문자");
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("기준값을 저장했습니다.");
  expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1][1]?.body))).toEqual({
    aply_bgn_ym: "2026-11",
    txt_val: "새 문자",
  });
});
