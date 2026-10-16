import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { PartnerActivityScreen } from "../../src/pages/partnersops/PartnerActivityScreen";
import { PartnerStatusActions } from "../../src/pages/partnersops/PartnerStatusActions";
import {
  getPartnerOptions,
  partnerExclusions,
  type PartnerDisableResult,
} from "../../src/pages/partnersops/api";
import { RequestError } from "../../src/api/client";

function response(data: unknown) {
  return new Response(
    JSON.stringify({
      success: true,
      data,
      meta: { requestId: "partner-request" },
    }),
    {
      status: 200,
      headers: { "X-Request-Id": "partner-request" },
    },
  );
}
function failure(status: number, additions: Record<string, unknown> = {}) {
  return new Response(
    JSON.stringify({
      success: false,
      code: `${status}`,
      message: "요청에 실패했습니다.",
      field: null,
      requestId: "partner-request",
      meta: { requestId: "partner-request" },
      ...additions,
    }),
    { status, headers: { "X-Request-Id": "partner-request" } },
  );
}
const assignment = {
  projectName: "고객 프로젝트",
  developerName: "김개발",
  affiliationName: "협력사 B",
  jobName: "백엔드",
  assignmentRate: 0.5,
  endDate: "2026-10-16",
};
const history = {
  chgAt: "2026-10-16T10:00:00",
  dataChgTyCd: "UPDATE",
  clmnNm: "acnt_no",
  bfrVal: "1234",
  aftVal: "5678",
  chgUserId: null,
};
const selected = [
  { id: 21, ptnrNm: "가 거래처" },
  { id: 22, ptnrNm: "나 거래처" },
  { id: 23, ptnrNm: "다 거래처" },
];
const excluded = [
  {
    id: 23,
    name: "다 거래처",
    reason: "진행 프로젝트 2건",
    ongoingProjectCount: 2,
    affiliatedDeveloperCount: 0,
  },
];
const partial: PartnerDisableResult = {
  disabledIds: [21, 22],
  disabledCount: 2,
  inUseCount: 1,
  alreadyDisabledCount: 0,
  excluded,
};

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => response([])),
  );
});
afterEach(() => vi.unstubAllGlobals());

it("shows actual assignment rows, keeps source units, and loads only the selected region", async () => {
  vi.mocked(fetch).mockResolvedValue(
    response([assignment, { ...assignment, developerName: "이개발" }]),
  );
  render(
    <MemoryRouter>
      <PartnerActivityScreen partnerId={71} />
    </MemoryRouter>,
  );
  expect(await screen.findByText("김개발")).toBeInTheDocument();
  expect(screen.getAllByText("협력사 B")).toHaveLength(2);
  expect(screen.getAllByText("0.5")).toHaveLength(2);
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
    "/api/partners/71/assignments",
  );
  expect(screen.getByRole("tab", { name: "투입 내역" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(
    screen.queryByRole("tabpanel", { name: "변경 이력" }),
  ).not.toBeInTheDocument();
});

it("history tab renders newest server order, raw column name, masked values and default actor", async () => {
  vi.mocked(fetch).mockImplementation(async (url) =>
    response(
      String(url).endsWith("/history")
        ? [
            history,
            {
              ...history,
              dataChgTyCd: "INSERT",
              clmnNm: null,
              bfrVal: null,
              aftVal: null,
            },
          ]
        : [assignment],
    ),
  );
  render(<PartnerActivityScreen partnerId={72} />);
  await screen.findByText("김개발");
  fireEvent.click(screen.getByRole("tab", { name: "변경 이력" }));
  expect(await screen.findByText("acnt_no")).toBeInTheDocument();
  const first = screen.getByTestId("partner-history-row-0");
  expect(within(first).getByText("1234")).toBeInTheDocument();
  expect(within(first).getByText("5678")).toBeInTheDocument();
  expect(within(first).getByText("기본 사용자")).toBeInTheDocument();
  expect(screen.getByTestId("partner-history-row-1")).toHaveTextContent(
    "INSERT",
  );
  expect(screen.queryByText("김개발")).not.toBeInTheDocument();
  expect(vi.mocked(fetch).mock.calls[1][0]).toBe("/api/partners/72/history");
});

it("empty assignments and history have distinct approved messages", async () => {
  render(<PartnerActivityScreen partnerId={73} />);
  expect(
    await screen.findByText("지금 투입 중인 내역이 없습니다."),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByRole("tab", { name: "변경 이력" }));
  expect(
    await screen.findByText("아직 바꾼 기록이 없습니다."),
  ).toBeInTheDocument();
});

it("loading is not an empty result and manual read retry preserves request tracking", async () => {
  let finish: (value: Response) => void = () => undefined;
  vi.mocked(fetch).mockImplementationOnce(
    () =>
      new Promise<Response>((resolve) => {
        finish = resolve;
      }),
  );
  render(<PartnerActivityScreen partnerId={74} />);
  expect(screen.getByLabelText("불러오는 중")).toBeInTheDocument();
  expect(
    screen.queryByText("지금 투입 중인 내역이 없습니다."),
  ).not.toBeInTheDocument();
  finish(failure(500));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "문의 번호: partner-request",
  );
  expect(fetch).toHaveBeenCalledTimes(1);
  vi.mocked(fetch).mockResolvedValue(response([assignment]));
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("김개발")).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledTimes(2);
});

it("a new selected identity cannot be overwritten by a stale assignment response", async () => {
  let finish: (value: Response) => void = () => undefined;
  vi.mocked(fetch).mockImplementationOnce(
    () =>
      new Promise<Response>((resolve) => {
        finish = resolve;
      }),
  );
  const view = render(<PartnerActivityScreen partnerId={75} />);
  vi.mocked(fetch).mockResolvedValue(
    response([{ ...assignment, developerName: "새 거래처" }]),
  );
  view.rerender(<PartnerActivityScreen partnerId={76} />);
  await screen.findByText("새 거래처");
  finish(response([{ ...assignment, developerName: "이전 거래처" }]));
  await waitFor(() =>
    expect(screen.queryByText("이전 거래처")).not.toBeInTheDocument(),
  );
});

it("disable confirmation cancel never submits and retains selected names", async () => {
  const changed = vi.fn();
  render(<PartnerStatusActions selected={selected} onDisabled={changed} />);
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  const dialog = screen.getByRole("dialog");
  expect(dialog).toHaveTextContent("3건을 선택했습니다.");
  expect(dialog).toHaveTextContent("가 거래처");
  expect(dialog).toHaveTextContent("기록은 그대로 남습니다.");
  fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(fetch).not.toHaveBeenCalled();
  expect(changed).not.toHaveBeenCalled();
  await waitFor(() =>
    expect(screen.getByTestId("PTNR-DISABLE-OPEN")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  expect(screen.getByRole("dialog")).toHaveTextContent("3건을 선택했습니다.");
});

it("partial disable sends selected IDs and reports real counts to the host refresh callback", async () => {
  vi.mocked(fetch).mockResolvedValue(response(partial));
  const changed = vi.fn();
  render(<PartnerStatusActions selected={selected} onDisabled={changed} />);
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-CONFIRM"));
  expect(await screen.findByTestId("partner-status-message")).toHaveTextContent(
    "2건의 사용을 중지했습니다. (1건은 거래가 남아 제외)",
  );
  expect(changed).toHaveBeenCalledWith(partial);
  const [path, init] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/partners/disable");
  expect(JSON.parse(String(init?.body))).toEqual({ ids: [21, 22, 23] });
  expect(init?.method).toBe("POST");
});

it("single in-use 409 preserves structured exclusions through the common client and closes without retry", async () => {
  vi.mocked(fetch).mockResolvedValue(
    failure(409, { code: "PARTNER_IN_USE", excluded }),
  );
  const changed = vi.fn();
  render(
    <PartnerStatusActions selected={[selected[2]]} onDisabled={changed} />,
  );
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-CONFIRM"));
  expect(await screen.findByTestId("partner-blocked-dialog")).toHaveTextContent(
    "다 거래처: 진행 프로젝트 2건",
  );
  const modal = screen.getByRole("dialog");
  expect(within(modal).getAllByRole("button")).toHaveLength(1);
  fireEvent.click(within(modal).getByRole("button", { name: "확인" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(changed).not.toHaveBeenCalled();
});

it("multiple all-in-use 200 opens reasons, while already-disabled 200 is a no-change notice", async () => {
  vi.mocked(fetch).mockResolvedValueOnce(
    response({ ...partial, disabledIds: [], disabledCount: 0, inUseCount: 3 }),
  );
  render(<PartnerStatusActions selected={selected} onDisabled={vi.fn()} />);
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-CONFIRM"));
  await screen.findByTestId("partner-blocked-dialog");
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "확인" }),
  );
  vi.mocked(fetch).mockResolvedValueOnce(
    response({
      ...partial,
      disabledIds: [],
      disabledCount: 0,
      inUseCount: 0,
      alreadyDisabledCount: 3,
    }),
  );
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-CONFIRM"));
  expect(
    await screen.findByText("이미 사용 중지된 거래처입니다."),
  ).toBeInTheDocument();
});

it("invalid exclusion details cannot be displayed as trusted business reasons", async () => {
  const error = new RequestError(
    "거부",
    "id",
    "server",
    409,
    null,
    "PARTNER_IN_USE",
    {
      excluded: [{ ...excluded[0], affiliatedDeveloperCount: "wrong" }],
    },
  );
  expect(partnerExclusions(error)).toBeNull();
  expect(
    new RequestError("old constructor", "id", "client", null).details,
  ).toBeNull();
  vi.mocked(fetch).mockResolvedValue(
    failure(409, { code: "PARTNER_IN_USE", excluded: [null] }),
  );
  render(<PartnerStatusActions selected={selected} onDisabled={vi.fn()} />);
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-CONFIRM"));
  expect(await screen.findByTestId("partner-mutation-error")).toHaveTextContent(
    "문의 번호: partner-request",
  );
  expect(
    screen.queryByTestId("partner-blocked-dialog"),
  ).not.toBeInTheDocument();
});

it("technical disable failure keeps selection and confirmation, with no automatic mutation retry", async () => {
  vi.mocked(fetch).mockResolvedValue(failure(500));
  render(<PartnerStatusActions selected={selected} onDisabled={vi.fn()} />);
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-CONFIRM"));
  expect(await screen.findByTestId("partner-mutation-error")).toHaveTextContent(
    "문의 번호: partner-request",
  );
  expect(screen.getByRole("dialog")).toHaveTextContent("가 거래처");
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(
    screen.queryByRole("button", { name: "다시 시도" }),
  ).not.toBeInTheDocument();
});

it("duplicate submit is blocked while waiting for disable response", async () => {
  let finish: (value: Response) => void = () => undefined;
  vi.mocked(fetch).mockImplementationOnce(
    () =>
      new Promise<Response>((resolve) => {
        finish = resolve;
      }),
  );
  render(<PartnerStatusActions selected={selected} onDisabled={vi.fn()} />);
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  const confirm = screen.getByTestId("PTNR-DISABLE-CONFIRM");
  fireEvent.click(confirm);
  fireEvent.click(confirm);
  expect(confirm).toBeDisabled();
  expect(fetch).toHaveBeenCalledTimes(1);
  finish(response(partial));
  await screen.findByTestId("partner-status-message");
});

it("reactivation exists only on inactive detail, posts real ID without body and refreshes the host", async () => {
  const enabled = { id: 87, ptnrNm: "재사용 거래처", useYn: "Y" };
  vi.mocked(fetch).mockResolvedValue(response(enabled));
  const changed = vi.fn();
  const view = render(
    <PartnerStatusActions
      partner={{ id: 87, ptnrNm: "재사용 거래처", useYn: "N" }}
      onDisabled={vi.fn()}
      onEnabled={changed}
    />,
  );
  fireEvent.click(screen.getByTestId("PTNR-MORE"));
  fireEvent.click(screen.getByTestId("PTNR-ENABLE"));
  await screen.findByText("다시 사용하도록 변경했습니다.");
  expect(changed).toHaveBeenCalledWith(enabled);
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe("/api/partners/87/enable");
  expect(vi.mocked(fetch).mock.calls[0][1]?.body).toBeUndefined();
  view.rerender(
    <PartnerStatusActions
      partner={{ id: 87, ptnrNm: "재사용 거래처", useYn: "Y" }}
      onDisabled={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByTestId("PTNR-MORE"));
  expect(screen.queryByTestId("PTNR-ENABLE")).not.toBeInTheDocument();
  expect(screen.getByTestId("PTNR-DISABLE-OPEN")).toBeInTheDocument();
});

it("mobile remains read-only while no selection disables the desktop bulk action", () => {
  const view = render(
    <PartnerStatusActions selected={selected} mobile onDisabled={vi.fn()} />,
  );
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
  view.rerender(<PartnerStatusActions selected={[]} onDisabled={vi.fn()} />);
  expect(screen.getByTestId("PTNR-DISABLE-OPEN")).toBeDisabled();
});

it("options helper uses the required kind with no invented consumer screen or paging", async () => {
  const choices = [{ id: 88, no: "C-088", name: "고객" }];
  vi.mocked(fetch).mockResolvedValue(response(choices));
  expect(await getPartnerOptions("CUST")).toEqual(choices);
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
    "/api/partners/options?kind=CUST",
  );
});
