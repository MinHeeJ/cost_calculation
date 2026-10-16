import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { PartnerSalesPartScreen } from "../../src/screens/partners/PartnerSalesPartScreen";
import { SharedWorkspace } from "../../src/pages/shared/SharedWorkspace";
import { useNavigation } from "../../src/pages/shared/navigation";
import { useShortcuts } from "../../src/pages/shared/preferences";
import {
  partnersApi,
  type PartnerDetail,
  type PartnerListItem,
} from "../../src/pages/partners/partnersApi";

const customer: PartnerDetail = {
  id: 42,
  no: "C-042",
  ptnrSeCd: "CUST",
  ptnrNm: "가합성",
  bizRegNo: "123-45-67890",
  useYn: "Y",
  ceoNm: null,
  indtyCd: null,
  chrgNm: null,
  chrgTelNo: null,
  kindName: "고객사",
  industryName: null,
  txinvEmailAddr: "tax@example.test",
};
const vendor: PartnerDetail = {
  ...customer,
  id: 43,
  no: "C-043",
  ptnrSeCd: "VEND",
  ptnrNm: "나협력",
  kindName: "협력사",
  txinvEmailAddr: undefined,
  bankCd: "KB",
  bankName: "국민은행",
  acntNo: "1234-5678-9012",
  dpstrNm: "합성예금주",
};
let details: PartnerDetail[];
let listFailure = false;
let mutationFailure = false;
let blockedIds: number[] = [];
const listRow = (row: PartnerDetail): PartnerListItem => {
  const { bankCd, acntNo, dpstrNm, bankName, ...basic } = row;
  return {
    ...basic,
    txinvEmailAddr: row.txinvEmailAddr ?? null,
    paymentAccountSummary: acntNo ? `${bankName} …${acntNo.slice(-4)}` : null,
    ongoingProjectCount: 2,
    assignedDeveloperCount: 5,
  };
};
function ok(data: unknown, status = 200) {
  return new Response(
    JSON.stringify({ success: true, data, meta: { requestId: "request-123" } }),
    {
      status,
      headers: { "X-Request-Id": "request-123" },
    },
  );
}
function failure(field: string | null = null) {
  return new Response(
    JSON.stringify({
      success: false,
      code: "400",
      field,
      message: "입력을 확인하세요.",
      requestId: "request-123",
      meta: { requestId: "request-123" },
    }),
    {
      status: 400,
      headers: { "X-Request-Id": "request-123" },
    },
  );
}
const menus = [
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
    name: "기준정보",
    icon: "grid",
    screenId: null,
    mobile: true,
    children: [
      {
        menuId: 3,
        name: "거래처·영업 파트",
        icon: null,
        screenId: "UI-15",
        mobile: true,
        children: [],
      },
    ],
  },
];
beforeEach(() => {
  details = structuredClone([
    customer,
    vendor,
    { ...vendor, id: 44, ptnrSeCd: "BOTH", ptnrNm: "다둘다" },
    { ...customer, id: 45, useYn: "N" },
  ]);
  listFailure = false;
  mutationFailure = false;
  blockedIds = [];
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 1280,
  });
  useNavigation.setState({
    active: "UI-16",
    tabs: ["UI-16"],
    visits: ["UI-16"],
    dirty: {},
    screenState: {},
    busy: false,
  });
  useShortcuts.setState({ favorites: [], recent: [] });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (path: string, init?: RequestInit) => {
      if (path === "/api/menus") return ok(menus);
      if (path.startsWith("/api/codes/")) {
        return ok(
          path.endsWith("PARTNER_KIND")
            ? [
                { code: "CUST", name: "고객사" },
                { code: "VEND", name: "협력사" },
                { code: "BOTH", name: "둘 다" },
              ]
            : path.endsWith("BANK")
              ? [{ code: "KB", name: "국민은행" }]
              : [],
        );
      }
      if (path === "/api/partners/disable" && init?.method === "POST") {
        const ids: number[] = JSON.parse(String(init.body)).ids;
        const excluded = details
          .filter((row) => ids.includes(row.id) && blockedIds.includes(row.id))
          .map((row) => ({
            id: row.id,
            name: row.ptnrNm,
            reason: "진행 프로젝트 2건",
            ongoingProjectCount: 2,
            affiliatedDeveloperCount: 0,
          }));
        if (ids.length === 1 && excluded.length) {
          return new Response(
            JSON.stringify({
              success: false,
              code: "PARTNER_IN_USE",
              message: "거래가 남아 있습니다.",
              field: null,
              requestId: "request-123",
              meta: { requestId: "request-123" },
              excluded,
            }),
            { status: 409, headers: { "X-Request-Id": "request-123" } },
          );
        }
        const disabledIds = ids.filter((id) => !blockedIds.includes(id));
        details.forEach((row) => {
          if (disabledIds.includes(row.id)) row.useYn = "N";
        });
        return ok({
          disabledIds,
          disabledCount: disabledIds.length,
          inUseCount: excluded.length,
          alreadyDisabledCount: 0,
          excluded,
        });
      }
      if (path.endsWith("/enable") && init?.method === "POST") {
        const row = details.find(
          (item) => item.id === Number(path.split("/")[3]),
        );
        if (!row) throw new Error(`Missing test partner: ${path}`);
        row.useYn = "Y";
        return ok(row);
      }
      if (init?.method === "POST") {
        if (mutationFailure) return failure("bizRegNo");
        const body = JSON.parse(String(init.body));
        const row = { ...customer, ...body, id: 99, no: "C-099", useYn: "Y" };
        details.push(row);
        return ok(row, 201);
      }
      if (init?.method === "PUT") {
        if (mutationFailure) return failure("acntNo");
        const id = Number(path.split("/").pop());
        const index = details.findIndex((row) => row.id === id);
        details[index] = {
          ...details[index],
          ...JSON.parse(String(init.body)),
        };
        return ok(details[index]);
      }
      if (path.includes("?")) {
        if (listFailure) return failure();
        const params = new URLSearchParams(path.split("?")[1]);
        const kind = params.get("kind");
        const q = params.get("q") ?? "";
        const items = details
          .filter(
            (row) =>
              (params.get("useYn") === "ALL" ||
                row.useYn === (params.get("useYn") ?? "Y")) &&
              (!kind ||
                row.ptnrSeCd === kind ||
                (kind !== "BOTH" && row.ptnrSeCd === "BOTH")) &&
              (!q || row.ptnrNm.includes(q) || row.bizRegNo.includes(q)),
          )
          .map(listRow);
        return ok({
          items,
          total: items.length,
          page: Number(params.get("page")),
          size: 50,
          sort: "ptnr_nm,asc",
        });
      }
      if (path.endsWith("/assignments"))
        return ok([
          {
            projectName: "합성프로젝트",
            developerName: "합성개발자",
            affiliationName: "나협력",
            jobName: "백엔드",
            assignmentRate: 1,
            endDate: "2026-10-31",
          },
        ]);
      if (path.endsWith("/history")) return ok([]);
      const id = Number(path.split("/").pop());
      return ok(details.find((row) => row.id === id));
    }),
  );
});
afterEach(() => vi.unstubAllGlobals());
const putCalls = () =>
  vi.mocked(fetch).mock.calls.filter(([, init]) => init?.method === "PUT");

it("기존 메뉴 진입은 /UI-15 실제 거래처 화면과 API 목록을 연다", async () => {
  render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <SharedWorkspace />
    </MemoryRouter>,
  );
  await screen.findByRole("heading", { name: "대시보드", level: 1 });
  fireEvent.click(await screen.findByRole("button", { name: "기준정보" }));
  fireEvent.click(
    await screen.findByRole("link", { name: "거래처·영업 파트" }),
  );
  expect(await screen.findByTestId("partner-sales-part-screen")).toBeVisible();
  expect(await screen.findByText("총 3건")).toBeVisible();
  expect(useNavigation.getState().active).toBe("UI-15");
});

it("사용 3건과 마스킹 목록을 읽고 협력사 조건에는 VEND와 BOTH만 표시한다", async () => {
  render(<PartnerSalesPartScreen />);
  expect(await screen.findByText("총 3건")).toBeVisible();
  expect(screen.queryByText("1234-5678-9012")).not.toBeInTheDocument();
  expect(screen.getAllByText("국민은행 …9012")).toHaveLength(2);
  fireEvent.change(screen.getByTestId("partner-filter-kind"), {
    target: { value: "VEND" },
  });
  await waitFor(() => expect(screen.getByText("총 2건")).toBeVisible());
  expect(screen.queryByTestId("PTNR-ROW-42")).not.toBeInTheDocument();
  expect(screen.getByTestId("PTNR-ROW-43")).toBeVisible();
});

it("빈 결과는 조건 초기화로 복구하며 실패는 문의번호와 수동 재시도로 복구한다", async () => {
  render(<PartnerSalesPartScreen />);
  await screen.findByText("총 3건");
  fireEvent.change(screen.getByTestId("partner-filter-query"), {
    target: { value: "없는상호" },
  });
  fireEvent.click(screen.getByTestId("PTNR-QUERY"));
  expect(await screen.findByText("조건에 맞는 결과가 없습니다.")).toBeVisible();
  fireEvent.click(screen.getByTestId("PTNR-RESET"));
  await screen.findByText("총 3건");
  listFailure = true;
  fireEvent.click(screen.getByTestId("PTNR-QUERY"));
  expect(await screen.findByRole("alert")).toHaveTextContent("request-123");
  listFailure = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await waitFor(() =>
    expect(screen.queryByRole("alert")).not.toBeInTheDocument(),
  );
});

it("등록은 구분 직접 선택과 자동 하이픈, 조건부 필드, 성공 강조를 제공한다", async () => {
  render(<PartnerSalesPartScreen />);
  fireEvent.click(await screen.findByTestId("PTNR-CREATE-OPEN"));
  await waitFor(() =>
    expect(screen.getByTestId("PTNR-KIND")).not.toBeDisabled(),
  );
  expect(screen.getByTestId("PTNR-KIND")).toHaveValue("");
  fireEvent.change(screen.getByTestId("PTNR-KIND"), {
    target: { value: "CUST" },
  });
  fireEvent.change(screen.getByLabelText("상호 *"), {
    target: { value: "새 합성상호" },
  });
  fireEvent.change(screen.getByLabelText("사업자번호 *"), {
    target: { value: "9876543210" },
  });
  expect(screen.getByLabelText("사업자번호 *")).toHaveValue("987-65-43210");
  fireEvent.change(screen.getByLabelText("세금계산서 이메일 *"), {
    target: { value: "tax@example.test" },
  });
  expect(screen.queryByLabelText("계좌번호 *")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("PTNR-CREATE-SAVE"));
  expect(await screen.findByText("저장했습니다.")).toBeVisible();
  expect(await screen.findByTestId("PTNR-ROW-99")).toHaveClass(/highlight/);
});

it("검증 첫 오류 focus 및 dirty 취소는 요청 없이 입력을 보존한다", async () => {
  render(<PartnerSalesPartScreen />);
  fireEvent.click(await screen.findByTestId("PTNR-CREATE-OPEN"));
  await waitFor(() =>
    expect(screen.getByTestId("PTNR-KIND")).not.toBeDisabled(),
  );
  fireEvent.click(screen.getByTestId("PTNR-CREATE-SAVE"));
  expect(screen.getByTestId("PTNR-KIND")).toHaveFocus();
  fireEvent.change(screen.getByLabelText("상호 *"), {
    target: { value: "보존 draft" },
  });
  fireEvent.click(screen.getByTestId("PTNR-CREATE-CANCEL"));
  fireEvent.click(await screen.findByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("상호 *")).toHaveValue("보존 draft");
  expect(
    vi.mocked(fetch).mock.calls.filter(([, init]) => init?.method),
  ).toHaveLength(0);
});

it("상세는 중지 띠와 해당 원문 계좌만 표시하며 건수는 투입 탭으로 연결한다", async () => {
  details[1].useYn = "N";
  render(<PartnerSalesPartScreen />);
  await screen.findByText("총 2건");
  fireEvent.change(screen.getByTestId("partner-filter-use"), {
    target: { value: "N" },
  });
  fireEvent.click(await screen.findByTestId("PTNR-ROW-43"));
  expect(await screen.findByText("사용 중지된 거래처입니다.")).toBeVisible();
  expect(await screen.findByText("1234-5678-9012")).toBeVisible();
  expect(
    within(screen.getByTestId("partner-detail-panel")).queryByText(
      "세금계산서 이메일",
    ),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("PTNR-ASSIGNMENTS"));
  expect(await screen.findByText("합성프로젝트")).toBeVisible();
  fireEvent.click(screen.getByTestId("PTNR-HISTORY"));
  expect(await screen.findByText("아직 바꾼 기록이 없습니다.")).toBeVisible();
});

it("계좌 확인 취소는 PUT 없이 draft 유지, 확인만 저장하고 번호를 payload에서 제외한다", async () => {
  render(<PartnerSalesPartScreen />);
  fireEvent.click(await screen.findByTestId("PTNR-ROW-43"));
  fireEvent.click(await screen.findByTestId("PTNR-EDIT-OPEN"));
  await waitFor(() =>
    expect(screen.getByTestId("PTNR-KIND")).not.toBeDisabled(),
  );
  expect(screen.getByLabelText("사업자번호")).toHaveAttribute("readonly");
  fireEvent.change(screen.getByLabelText("계좌번호 *"), {
    target: { value: "9876-5432-1098" },
  });
  fireEvent.click(screen.getByTestId("PTNR-EDIT-SAVE"));
  const question = await screen.findByRole("dialog", {
    name: "지급 계좌를 바꿀까요?",
  });
  fireEvent.click(within(question).getByRole("button", { name: "취소" }));
  expect(putCalls()).toHaveLength(0);
  expect(screen.getByLabelText("계좌번호 *")).toHaveValue("9876-5432-1098");
  fireEvent.click(screen.getByTestId("PTNR-EDIT-SAVE"));
  fireEvent.click(await screen.findByRole("button", { name: "바꾸기" }));
  await waitFor(() => expect(putCalls()).toHaveLength(1));
  expect(JSON.parse(String(putCalls()[0][1]?.body))).not.toHaveProperty(
    "bizRegNo",
  );
  expect(await screen.findByText("9876-5432-1098")).toBeVisible();
});

it("동일 수정은 HTTP 없이 안내하며 767px은 조회만, 768px은 등록을 제공한다", async () => {
  render(<PartnerSalesPartScreen />);
  fireEvent.click(await screen.findByTestId("PTNR-ROW-42"));
  fireEvent.click(await screen.findByTestId("PTNR-EDIT-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-EDIT-SAVE"));
  expect(await screen.findByText("바뀐 내용이 없습니다.")).toBeVisible();
  expect(putCalls()).toHaveLength(0);
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 767,
  });
  fireEvent(window, new Event("resize"));
  expect(screen.queryByTestId("PTNR-CREATE-OPEN")).not.toBeInTheDocument();
  expect(screen.queryByTestId("PTNR-EDIT-SAVE")).not.toBeInTheDocument();
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 768,
  });
  fireEvent(window, new Event("resize"));
  expect(screen.getByTestId("PTNR-CREATE-OPEN")).toBeVisible();
});

it("M1 탭 상한 취소는 기존 8개 탭과 목적지를 보존한다", async () => {
  const tabs = [
    "UI-16",
    "UI-01",
    "UI-02",
    "UI-03",
    "UI-04",
    "UI-05",
    "UI-07",
    "UI-08",
  ];
  useNavigation.setState({ tabs, visits: tabs });
  render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <SharedWorkspace />
    </MemoryRouter>,
  );
  await screen.findByRole("heading", { name: "대시보드", level: 1 });
  fireEvent.click(await screen.findByRole("button", { name: "기준정보" }));
  fireEvent.click(
    await screen.findByRole("link", { name: "거래처·영업 파트" }),
  );
  const modal = await screen.findByRole("dialog", { name: "열린 화면 한도" });
  fireEvent.click(within(modal).getByRole("button", { name: "취소" }));
  expect(useNavigation.getState().tabs).toEqual(tabs);
  expect(useNavigation.getState().active).toBe("UI-16");
});

it("목록 선택 취소는 요청 없이 유지되며 부분 중지는 같은 필터를 갱신한다", async () => {
  blockedIds = [43];
  render(<PartnerSalesPartScreen />);
  await screen.findByText("총 3건");
  fireEvent.change(screen.getByTestId("partner-filter-kind"), {
    target: { value: "VEND" },
  });
  await screen.findByText("총 2건");
  fireEvent.click(screen.getByTestId("PTNR-SELECT-PAGE"));
  expect(screen.getByTestId("PTNR-SELECT-43")).toBeChecked();
  expect(screen.queryByTestId("partner-detail-drawer")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }),
  );
  expect(
    vi.mocked(fetch).mock.calls.filter(([, init]) => init?.method === "POST"),
  ).toHaveLength(0);
  expect(screen.getByTestId("PTNR-SELECT-43")).toBeChecked();
  await waitFor(() =>
    expect(screen.getByTestId("PTNR-DISABLE-OPEN")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-CONFIRM"));
  expect(
    await screen.findByText(
      "1건의 사용을 중지했습니다. (1건은 거래가 남아 제외)",
    ),
  ).toBeVisible();
  await waitFor(() =>
    expect(screen.queryByTestId("PTNR-ROW-44")).not.toBeInTheDocument(),
  );
  expect(screen.getByTestId("PTNR-ROW-43")).toBeVisible();
  expect(screen.getByTestId("partner-filter-kind")).toHaveValue("VEND");
  const calls = vi.mocked(fetch).mock.calls;
  expect(
    JSON.parse(
      String(calls.find(([url]) => url === "/api/partners/disable")?.[1]?.body),
    ),
  ).toEqual({ ids: [43, 44] });
  expect(String(calls[calls.length - 1][0])).toContain("kind=VEND");
});

it("상세 중지와 다시 사용은 현재 ID로 요청하고 상세 및 동일 조건 목록을 재조회한다", async () => {
  render(<PartnerSalesPartScreen />);
  fireEvent.change(await screen.findByTestId("partner-filter-use"), {
    target: { value: "ALL" },
  });
  fireEvent.click(await screen.findByTestId("PTNR-ROW-43"));
  await screen.findByText("1234-5678-9012");
  fireEvent.click(screen.getByTestId("PTNR-MORE"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-CONFIRM"));
  expect(await screen.findByText("사용 중지된 거래처입니다.")).toBeVisible();
  fireEvent.click(screen.getByTestId("PTNR-MORE"));
  fireEvent.click(screen.getByTestId("PTNR-ENABLE"));
  await waitFor(() =>
    expect(
      screen.queryByText("사용 중지된 거래처입니다."),
    ).not.toBeInTheDocument(),
  );
  expect(screen.getByTestId("partner-filter-use")).toHaveValue("ALL");
  fireEvent.click(screen.getByTestId("PTNR-HISTORY"));
  expect(await screen.findByText("아직 바꾼 기록이 없습니다.")).toBeVisible();
  const writes = vi
    .mocked(fetch)
    .mock.calls.filter(([, init]) => init?.method === "POST");
  expect(writes.map(([url]) => url)).toEqual([
    "/api/partners/disable",
    "/api/partners/43/enable",
  ]);
  expect(JSON.parse(String(writes[0][1]?.body))).toEqual({ ids: [43] });
  expect(writes[1][1]?.body).toBeUndefined();
  expect(
    vi.mocked(fetch).mock.calls.filter(([url]) => url === "/api/partners/43")
      .length,
  ).toBeGreaterThanOrEqual(3);
});

it("상세 단건 409는 서버 제외 사유를 보여주고 닫아도 재요청하지 않는다", async () => {
  blockedIds = [42];
  render(<PartnerSalesPartScreen />);
  fireEvent.click(await screen.findByTestId("PTNR-ROW-42"));
  fireEvent.click(await screen.findByTestId("PTNR-MORE"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-OPEN"));
  fireEvent.click(screen.getByTestId("PTNR-DISABLE-CONFIRM"));
  expect(await screen.findByTestId("partner-blocked-dialog")).toHaveTextContent(
    "가합성: 진행 프로젝트 2건",
  );
  fireEvent.click(
    within(
      screen
        .getAllByRole("dialog")
        .find((node) =>
          node.textContent?.includes("사용 중지할 수 없습니다."),
        )!,
    ).getByRole("button", { name: "확인" }),
  );
  expect(details[0].useYn).toBe("Y");
  expect(screen.getByTestId("partner-detail-drawer")).toBeVisible();
  expect(
    vi.mocked(fetch).mock.calls.filter(([, init]) => init?.method === "POST"),
  ).toHaveLength(1);
});

it("767px에서는 통합된 선택 및 상태 변경을 숨기고 768px에서 복원한다", async () => {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 767,
  });
  render(<PartnerSalesPartScreen />);
  await screen.findByText("총 3건");
  expect(screen.queryByTestId("PTNR-SELECT-PAGE")).not.toBeInTheDocument();
  expect(screen.queryByTestId("PTNR-DISABLE-OPEN")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("PTNR-ROW-42"));
  await within(screen.getByTestId("partner-detail-panel")).findByText(
    "tax@example.test",
  );
  expect(screen.queryByTestId("PTNR-MORE")).not.toBeInTheDocument();
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 768,
  });
  fireEvent(window, new Event("resize"));
  expect(screen.getByTestId("PTNR-MORE")).toBeVisible();
});

it("drawer의 투입 탭은 통합 panel로 선택된 읽기만 실행한다", async () => {
  render(<PartnerSalesPartScreen />);
  fireEvent.click(await screen.findByTestId("PTNR-PEOPLE-COUNT-43"));
  expect(await screen.findByText("합성프로젝트")).toBeVisible();
  expect(screen.getByTestId("partner-assignment-row-0")).toBeVisible();
  expect(
    vi
      .mocked(fetch)
      .mock.calls.some(([url]) => url === "/api/partners/43/history"),
  ).toBe(false);
});
