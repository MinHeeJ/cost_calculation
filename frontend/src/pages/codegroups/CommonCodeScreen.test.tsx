import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { CommonCodeScreen } from "./CommonCodeScreen";
import { CommonCodePage } from "./CommonCodePage";
import { useNavigation } from "../shared/navigation";
import { request, RequestError } from "../../api/client";
import type { AdminCodeView, CodeGroupView } from "./api";

const groups: CodeGroupView[] = [
  {
    cd_grp_id: "JOB",
    cd_grp_nm: "직무",
    cd_grp_se_cd: "BIZ",
    attr1_nm: "기본 단가(원)",
    attr2_nm: null,
    attr3_nm: null,
    sort_seq: 1,
    use_yn: "Y",
    code_count: 2,
  },
  {
    cd_grp_id: "CONTRACT_TYPE",
    cd_grp_nm: "계약유형",
    cd_grp_se_cd: "SYSTEM",
    attr1_nm: null,
    attr2_nm: null,
    attr3_nm: null,
    sort_seq: 2,
    use_yn: "Y",
    code_count: 1,
  },
];
let rows: AdminCodeView[];
let writes: { path: string; body: unknown }[];
let failReadAfterSave: boolean;
let failWrite: boolean;
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
  rows = [
    {
      cd_grp_id: "JOB",
      dtl_cd: "BE",
      cd_nm: "개발",
      attr1_val: "200000",
      attr2_val: null,
      attr3_val: null,
      sort_seq: 1,
      use_yn: "Y",
      aply_bgn_dt: null,
    },
    {
      cd_grp_id: "JOB",
      dtl_cd: "OPS",
      cd_nm: "운영",
      attr1_val: null,
      attr2_val: null,
      attr3_val: null,
      sort_seq: 2,
      use_yn: "N",
      aply_bgn_dt: null,
    },
  ];
  writes = [];
  failReadAfterSave = false;
  failWrite = false;
  useNavigation.setState({ dirty: {}, screenState: {}, active: "UI-27" });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (path: string, init?: RequestInit) => {
      if (init?.method === "PUT" || init?.method === "POST") {
        writes.push({ path, body: JSON.parse(String(init.body)) });
        if (failWrite)
          return new Response(
            JSON.stringify({
              success: false,
              code: "DUPLICATE_CODE",
              message: "이미 있는 코드입니다.",
              field: "dtl_cd",
              requestId: "server-id",
              meta: { requestId: "server-id" },
            }),
            { status: 409, headers: { "X-Request-Id": "server-id" } },
          );
        if (init.method === "PUT")
          rows = JSON.parse(String(init.body)).codes.map((row: object) => ({
            ...row,
            cd_grp_id: "JOB",
          }));
        return new Response(null, {
          status: 204,
          headers: { "X-Request-Id": "server-id" },
        });
      }
      if (path === "/api/admin/code-groups") return respond(groups);
      if (path.includes("/codes")) {
        if (failReadAfterSave && writes.length) throw new Error("offline");
        if (path.includes("CONTRACT_TYPE"))
          return respond([
            { ...rows[0], cd_grp_id: "CONTRACT_TYPE", dtl_cd: "TK" },
          ]);
        return respond(rows);
      }
      if (path.includes("code-history"))
        return respond({
          items: [
            {
              cd_hist_id: 1,
              cd_grp_id: "POSITION",
              cd_grp_nm: "직급",
              dtl_cd: "TL",
              cd_chg_ty_cd: "UPDATE",
              item_nm: "attr1_val",
              bfr_val: "200000",
              aft_val: "250000",
              chg_user_id: null,
              chg_at: "2026-10-16T12:00:00",
            },
          ],
          total: 51,
          page: 1,
          size: 50,
        });
      throw new Error(`Unexpected ${path}`);
    }),
  );
});
afterEach(() => vi.unstubAllGlobals());

it("공개 최초 진입은 실제 그룹/열 제목/중지 행을 조회하고 기존 코드 식별자는 잠근다", async () => {
  render(<CommonCodeScreen />);
  expect(await screen.findByDisplayValue("개발")).toBeVisible();
  expect(
    screen.getByRole("columnheader", { name: "기본 단가(원)" }),
  ).toBeVisible();
  expect(screen.getByLabelText("사용 2")).not.toBeChecked();
  expect(screen.getByLabelText("코드 1")).toHaveAttribute("readonly");
  expect(screen.getByTestId("code-panel").className).toContain("panes");
  expect(writes).toEqual([]);
});

it("SYSTEM은 이름과 순서만 편집하고 신규/사용/적용일은 잠근다", async () => {
  render(<CommonCodeScreen />);
  await screen.findByDisplayValue("개발");
  fireEvent.click(screen.getByRole("button", { name: /계약유형/ }));
  await screen.findByDisplayValue("TK");
  expect(screen.getByText(/계산 규칙에 쓰는 시스템 코드/)).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "+ 코드 추가" }),
  ).not.toBeInTheDocument();
  expect(screen.getByLabelText("사용 1")).toBeDisabled();
  expect(screen.getByLabelText("적용 시작일 1")).toHaveAttribute("readonly");
  expect(screen.getByLabelText("이름 1")).not.toHaveAttribute("readonly");
});

it("코드 추가는 최대+1/Y이며 전체집합 저장 후204를 수용하고 재조회한다", async () => {
  render(<CommonCodeScreen />);
  await screen.findByDisplayValue("개발");
  fireEvent.click(screen.getByRole("button", { name: "+ 코드 추가" }));
  fireEvent.change(screen.getByLabelText("코드 3"), {
    target: { value: "TEST1" },
  });
  fireEvent.change(screen.getByLabelText("이름 3"), {
    target: { value: "테스트" },
  });
  expect(screen.getByLabelText("순서 3")).toHaveValue(3);
  expect(screen.getByLabelText("사용 3")).toBeChecked();
  fireEvent.click(screen.getByTestId("code-save"));
  await screen.findByText("공통코드를 저장했습니다.");
  await waitFor(() => expect(screen.getByTestId("code-save")).toBeDisabled());
  expect(writes).toHaveLength(1);
  expect(writes[0].path).toBe("/api/admin/code-groups/JOB/codes");
  expect((writes[0].body as { codes: object[] }).codes).toHaveLength(3);
  expect(useNavigation.getState().dirty["UI-27"]).toBe(false);
});

it("취소는 서버 snapshot으로 복원하며 저장 실패는 입력과 field 오류를 유지한다", async () => {
  render(<CommonCodeScreen />);
  await screen.findByDisplayValue("개발");
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "편집" },
  });
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
  fireEvent.click(screen.getByTestId("code-cancel"));
  expect(screen.getByLabelText("이름 1")).toHaveValue("개발");
  expect(useNavigation.getState().dirty["UI-27"]).toBe(false);
  failWrite = true;
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "다시 편집" },
  });
  fireEvent.click(screen.getByTestId("code-save"));
  await screen.findByText("dtl_cd: 이미 있는 코드입니다.");
  expect(screen.getByLabelText("이름 1")).toHaveValue("다시 편집");
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
});

it("dirty 그룹/하위탭 전환의 계속 작성은 선택과 입력을 보존한다", async () => {
  render(<CommonCodeScreen />);
  await screen.findByDisplayValue("개발");
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "편집 중" },
  });
  fireEvent.click(screen.getByRole("button", { name: /계약유형/ }));
  fireEvent.click(await screen.findByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("이름 1")).toHaveValue("편집 중");
  fireEvent.click(screen.getByRole("tab", { name: "변경 이력" }));
  fireEvent.click(await screen.findByRole("button", { name: "계속 작성" }));
  expect(screen.getByRole("tab", { name: "코드" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(writes).toHaveLength(0);
});

it("성공 후 재조회 실패 시 다시 시도는 GET만 실행하고 PUT을 반복하지 않는다", async () => {
  render(<CommonCodeScreen />);
  await screen.findByDisplayValue("개발");
  failReadAfterSave = true;
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "저장된 값" },
  });
  fireEvent.click(screen.getByTestId("code-save"));
  await screen.findByRole("button", { name: "다시 시도" });
  expect(screen.getByTestId("code-save")).toBeDisabled();
  failReadAfterSave = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await waitFor(() =>
    expect(
      screen.queryByRole("button", { name: "다시 시도" }),
    ).not.toBeInTheDocument(),
  );
  expect(writes).toHaveLength(1);
  expect(screen.getByLabelText("이름 1")).toHaveValue("저장된 값");
});

it("그룹 추가는 BIZ 유형이나 처리자 없이 선택 필드만 POST한다", async () => {
  render(<CommonCodeScreen />);
  await screen.findByDisplayValue("개발");
  fireEvent.click(screen.getByTestId("group-add"));
  const modal = screen.getByRole("dialog", { name: "그룹 추가" });
  fireEvent.change(within(modal).getByLabelText("그룹 코드"), {
    target: { value: "TEST_GROUP" },
  });
  fireEvent.change(within(modal).getByLabelText("그룹명"), {
    target: { value: "테스트 그룹" },
  });
  fireEvent.click(within(modal).getByRole("button", { name: "저장" }));
  await screen.findByText("그룹을 추가했습니다.");
  expect(writes[0].body).toMatchObject({
    cd_grp_id: "TEST_GROUP",
    cd_grp_nm: "테스트 그룹",
  });
  expect(writes[0].body).not.toHaveProperty("cd_grp_se_cd");
});

it("네 탭은 독립 본문이며 이력은 필터/기본50/페이지를 API로 보낸다", async () => {
  render(
    <CommonCodeScreen
      excelMaps={<p>대응 서버 본문</p>}
      operatingStandards={<p>기준값 서버 본문</p>}
    />,
  );
  await screen.findByDisplayValue("개발");
  fireEvent.click(screen.getByRole("tab", { name: "엑셀 표기 대응" }));
  expect(await screen.findByText("대응 서버 본문")).toBeVisible();
  fireEvent.click(screen.getByRole("tab", { name: "기준값" }));
  expect(await screen.findByText("기준값 서버 본문")).toBeVisible();
  fireEvent.click(screen.getByRole("tab", { name: "변경 이력" }));
  expect(await screen.findByText("기본 사용자")).toBeVisible();
  fireEvent.change(screen.getByLabelText("그룹"), {
    target: { value: "PARAM" },
  });
  await waitFor(() =>
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([path]) => String(path).includes("cd_grp_id=PARAM")),
    ).toBe(true),
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "다음 페이지" }),
    ).not.toBeDisabled(),
  );
  fireEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([path]) => String(path).includes("page=2")),
    ).toBe(true),
  );
  expect(writes).toHaveLength(0);
});

it("204 헤더 검증은 JSON 파싱 없이 void를 반환하며 오류 code를 보존한다", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(null, {
        status: 204,
        headers: { "X-Request-Id": "server-id" },
      }),
    ),
  );
  await expect(
    request<void>("/api/admin/code-groups", { method: "POST" }),
  ).resolves.toBeUndefined();
  vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
  await expect(request<void>("/api/admin/code-groups")).rejects.toBeInstanceOf(
    RequestError,
  );
  vi.mocked(fetch).mockResolvedValueOnce(
    new Response(
      JSON.stringify({
        success: false,
        code: "SYSTEM_CODE_LOCKED",
        message: "시스템 코드",
        field: null,
        requestId: "server-id",
        meta: { requestId: "server-id" },
      }),
      { status: 409, headers: { "X-Request-Id": "server-id" } },
    ),
  );
  await expect(request("/api/admin/code-groups")).rejects.toMatchObject({
    code: "SYSTEM_CODE_LOCKED",
    status: 409,
  });
});

it("UI-27 목적지는 기존 제목/즐겨찾기를 재사용하고 업무를 조회한다", async () => {
  render(
    <CommonCodePage name="공통코드" groupName="시스템" onDashboard={vi.fn()} />,
  );
  expect(screen.getByRole("heading", { name: "공통코드" })).toBeVisible();
  expect(screen.getByText("시스템 › 공통코드")).toBeVisible();
  expect(screen.getByRole("button", { name: /즐겨찾기/ })).toBeVisible();
  expect(await screen.findByDisplayValue("개발")).toBeVisible();
});

it("휴대폰 직접 진입은 PC 안내만 표시하며 API를 호출하지 않는다", () => {
  const dashboard = vi.fn();
  render(<CommonCodePage name="공통코드" mobile onDashboard={dashboard} />);
  expect(
    screen.getByRole("heading", { name: "PC에서 이용해 주세요" }),
  ).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "대시보드로" }));
  expect(dashboard).toHaveBeenCalledOnce();
  expect(fetch).not.toHaveBeenCalled();
});
