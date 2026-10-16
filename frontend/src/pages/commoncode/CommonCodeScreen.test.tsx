import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { CommonCodeScreen } from "./CommonCodeScreen";
import {
  commonCodeApi,
  write,
  type CodeGroup,
  type CodeRow,
} from "./commonCode";
import { RequestError, request } from "../../api/client";
import { SharedWorkspace } from "../shared/SharedWorkspace";
import { useNavigation } from "../shared/navigation";

const groups: CodeGroup[] = [
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
    cd_grp_nm: "계약 유형",
    cd_grp_se_cd: "SYSTEM",
    attr1_nm: null,
    attr2_nm: null,
    attr3_nm: null,
    sort_seq: 2,
    use_yn: "Y",
    code_count: 1,
  },
];
const codes: CodeRow[] = [
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
const standard = {
  oprt_std_id: "VAT_RATE",
  oprt_std_nm: "부가세율",
  num_val: 0,
  txt_val: null,
  unit_nm: "%",
  use_plc_desc: "세금 계산",
  aply_bgn_ym: "2000-01",
};
function ok(data: unknown) {
  return new Response(
    JSON.stringify({ success: true, data, meta: { requestId: "request-123" } }),
    { status: 200, headers: { "X-Request-Id": "request-123" } },
  );
}
function failure(
  status = 409,
  field: string | null = "codes[0].cd_nm",
  code = "DUPLICATE_CODE",
) {
  return new Response(
    JSON.stringify({
      success: false,
      code,
      field,
      message: "입력을 확인하세요.",
      requestId: "request-123",
      meta: { requestId: "request-123" },
    }),
    { status, headers: { "X-Request-Id": "request-123" } },
  );
}
let savedCodes = structuredClone(codes);
beforeEach(() => {
  savedCodes = structuredClone(codes);
  useNavigation.setState({
    active: "UI-16",
    tabs: ["UI-16"],
    visits: ["UI-16"],
    dirty: {},
    screenState: {},
    busy: false,
  });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (path: string, init?: RequestInit) => {
      if (init?.method) {
        if (init.method === "PUT" && path.endsWith("/codes"))
          savedCodes = JSON.parse(String(init.body)).codes;
        return new Response(null, {
          status: 204,
          headers: { "X-Request-Id": "request-123" },
        });
      }
      if (path === "/api/admin/code-groups") return ok(groups);
      if (path.includes("CONTRACT_TYPE/codes"))
        return ok([{ ...codes[0], dtl_cd: "TK", cd_nm: "턴키" }]);
      if (path.endsWith("/codes")) return ok(savedCodes);
      if (path.startsWith("/api/codes/"))
        return ok([
          { code: "BE", name: "백엔드", attr1: null, attr2: null, attr3: null },
        ]);
      if (path === "/api/admin/excel-maps")
        return ok([
          {
            map_id: 42,
            excel_lbl_nm: "개발",
            trns_cd_grp_id: "JOB",
            trns_cd: "BE",
            map_role_cd: "MAIN",
            rmrk_desc: null,
            use_yn: "N",
            cd_grp_nm: "직무",
            cd_nm: "백엔드",
          },
        ]);
      if (path.endsWith("/history"))
        return ok([
          { ...standard, aply_bgn_ym: "2027-01", num_val: 11 },
          standard,
        ]);
      if (path === "/api/admin/operating-standards")
        return ok({ items: [standard], last_closed_ym: "2026-09" });
      if (path.startsWith("/api/admin/code-history?"))
        return ok({
          items: [
            {
              cd_hist_id: 5,
              cd_grp_id: "JOB",
              cd_grp_nm: "직무",
              dtl_cd: "BE",
              cd_chg_ty_cd: "UPDATE",
              item_nm: "cd_nm",
              bfr_val: "이전",
              aft_val: "이후",
              chg_user_id: null,
              chg_at: "2026-10-01T12:00:00",
            },
          ],
          page: Number(new URLSearchParams(path.split("?")[1]).get("page")),
          size: 50,
          total: 51,
        });
      throw new Error(`Unexpected request ${path}`);
    }),
  );
});
afterEach(() => vi.unstubAllGlobals());
async function show() {
  render(<CommonCodeScreen />);
  await screen.findByDisplayValue("백엔드");
}
function tab(name: string) {
  fireEvent.click(screen.getByRole("tab", { name }));
}
function mutations() {
  return vi.mocked(fetch).mock.calls.filter(([, init]) => init?.method);
}
it("공개 코드 조회는 동적 열, 비활성 코드와 저장 코드 읽기 전용을 유지한다", async () => {
  await show();
  expect(
    screen.getByRole("columnheader", { name: "기본 단가(원)" }),
  ).toBeVisible();
  expect(screen.getByLabelText("사용 2")).not.toBeChecked();
  expect(screen.getByLabelText("코드 1")).toHaveAttribute("readonly");
  expect(
    vi
      .mocked(fetch)
      .mock.calls.every(
        ([, init]) => !new Headers(init?.headers).has("Authorization"),
      ),
  ).toBe(true);
});
it("SYSTEM은 이름/순서만 편집하며 추가와 사용 전환을 막는다", async () => {
  await show();
  fireEvent.click(screen.getByRole("button", { name: /계약 유형/ }));
  await screen.findByDisplayValue("턴키");
  expect(screen.getByText(/계산 규칙에 쓰는 시스템/)).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "+ 코드 추가" }),
  ).not.toBeInTheDocument();
  expect(screen.getByLabelText("사용 1")).toBeDisabled();
  expect(screen.getByLabelText("적용 시작일 1")).toHaveAttribute("readonly");
  expect(screen.getByLabelText("순서 1")).not.toHaveAttribute("readonly");
});
it("전체 목록 저장은 식별자 외 wire 필드만 전송하고 204 후 재조회한다", async () => {
  await show();
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "새 이름" },
  });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("공통코드를 저장했습니다.");
  const body = JSON.parse(String(mutations()[0][1]?.body));
  expect(body.codes).toHaveLength(2);
  expect(body.codes[0]).toMatchObject({ dtl_cd: "BE", cd_nm: "새 이름" });
  expect(body.codes[0]).not.toHaveProperty("cd_grp_id");
  expect(useNavigation.getState().dirty["UI-27"]).toBe(false);
});
it("신규 행은 최대 순서+1과 Y이며 빈 코드 검증 후 취소로 snapshot 복원한다", async () => {
  await show();
  fireEvent.click(screen.getByRole("button", { name: "+ 코드 추가" }));
  expect(screen.getByLabelText("순서 3")).toHaveValue(3);
  expect(screen.getByLabelText("사용 3")).toBeChecked();
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("코드를 입력하세요.");
  expect(mutations()).toHaveLength(0);
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  await waitFor(() =>
    expect(screen.queryByLabelText("코드 3")).not.toBeInTheDocument(),
  );
});
it("저장 오류는 문의번호와 모든 입력을 유지한다", async () => {
  await show();
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "보존" },
  });
  vi.mocked(fetch).mockResolvedValueOnce(failure());
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("입력을 확인하세요.");
  expect(screen.getByDisplayValue("보존")).toBeVisible();
  expect(screen.getByText(/문의 번호: request-123/)).toBeVisible();
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
});
it("저장 후 조회 실패의 다시 시도는 PUT을 반복하지 않는다", async () => {
  await show();
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "성공" },
  });
  const original = vi.mocked(fetch).getMockImplementation()!;
  let committed = false;
  vi.mocked(fetch).mockImplementation(async (path, init) => {
    if (init?.method) {
      committed = true;
      return original(path, init);
    }
    if (committed && String(path).endsWith("/codes")) {
      committed = false;
      throw new Error("offline");
    }
    return original(path, init);
  });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/연결이 끊겼습니다/);
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await waitFor(() =>
    expect(screen.queryByText(/연결이 끊겼습니다/)).not.toBeInTheDocument(),
  );
  expect(mutations()).toHaveLength(1);
});
it("dirty 하위 탭과 그룹 이동은 계속 작성에서 입력/선택을 보존한다", async () => {
  await show();
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "보존" },
  });
  tab("기준값");
  await screen.findByRole("dialog");
  fireEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(screen.getByDisplayValue("보존")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: /계약 유형/ }));
  await screen.findByRole("dialog");
  fireEvent.click(screen.getByRole("button", { name: "나가기" }));
  await screen.findByDisplayValue("턴키");
});
it("그룹 추가는 검증·명시 취소 보호·허용 필드 POST와 목록 갱신을 수행한다", async () => {
  await show();
  fireEvent.click(screen.getByRole("button", { name: "+ 그룹 추가" }));
  const modal = within(await screen.findByRole("dialog"));
  fireEvent.click(modal.getByRole("button", { name: "저장" }));
  await modal.findByText("대문자·_ 1~30자로 입력하세요.");
  fireEvent.change(modal.getByLabelText("그룹 코드"), {
    target: { value: "CUSTOM" },
  });
  fireEvent.change(modal.getByLabelText("그룹명"), {
    target: { value: "추가" },
  });
  fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
  expect(screen.getByRole("dialog")).toBeVisible();
  fireEvent.click(modal.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(JSON.parse(String(mutations()[0][1]?.body))).toEqual({
    cd_grp_id: "CUSTOM",
    cd_grp_nm: "추가",
    attr1_nm: null,
    attr2_nm: null,
    attr3_nm: null,
  });
});
it("대응 행은 중지 참조를 표시하고 PATCH에서 세 mutable 필드만 전송한다", async () => {
  await show();
  tab("엑셀 표기 대응");
  await screen.findByText("개발");
  expect(screen.getByLabelText("사용 42")).not.toBeChecked();
  fireEvent.change(screen.getByLabelText("메모 42"), {
    target: { value: "변경" },
  });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(mutations()).toHaveLength(1));
  expect(mutations()[0][0]).toBe("/api/admin/excel-maps/42");
  expect(JSON.parse(String(mutations()[0][1]?.body))).toEqual({
    map_role_cd: "MAIN",
    rmrk_desc: "변경",
    use_yn: "N",
  });
});
it("대응 추가는 역할을 자동 선택하지 않고 실제 그룹의 활성 코드 옵션을 사용한다", async () => {
  await show();
  tab("엑셀 표기 대응");
  fireEvent.click(await screen.findByRole("button", { name: "+ 대응 추가" }));
  const modal = within(await screen.findByRole("dialog"));
  expect(modal.getByLabelText("주/보조")).toHaveValue("");
  fireEvent.change(modal.getByLabelText("그룹"), { target: { value: "JOB" } });
  await modal.findByRole("option", { name: "백엔드" });
  expect(modal.queryByRole("option", { name: "운영" })).not.toBeInTheDocument();
  fireEvent.change(modal.getByLabelText("코드"), { target: { value: "BE" } });
  fireEvent.change(modal.getByLabelText("엑셀 표기"), {
    target: { value: "개발자" },
  });
  fireEvent.change(modal.getByLabelText("주/보조"), {
    target: { value: "SUB" },
  });
  fireEvent.click(modal.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(mutations()).toHaveLength(1));
  expect(JSON.parse(String(mutations()[0][1]?.body))).toMatchObject({
    trns_cd: "BE",
    map_role_cd: "SUB",
  });
});
it("기준값은 숫자 0을 표시하며 마감·비율 검증 후 선택 키와 해당 종류만 저장한다", async () => {
  await show();
  tab("기준값");
  await screen.findByText("0 %");
  fireEvent.click(screen.getByRole("button", { name: "값 변경" }));
  const modal = within(await screen.findByRole("dialog"));
  fireEvent.change(modal.getByLabelText("새 값"), { target: { value: "101" } });
  fireEvent.change(modal.getByLabelText("적용 시작월"), {
    target: { value: "2026-09" },
  });
  fireEvent.click(modal.getByRole("button", { name: "저장" }));
  await modal.findByText("올바른 값을 입력하세요.");
  expect(modal.getByText("마감월 다음 달부터 선택하세요.")).toBeVisible();
  expect(mutations()).toHaveLength(0);
  fireEvent.change(modal.getByLabelText("새 값"), { target: { value: "11" } });
  fireEvent.change(modal.getByLabelText("적용 시작월"), {
    target: { value: "2026-11" },
  });
  fireEvent.click(modal.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(JSON.parse(String(mutations()[0][1]?.body))).toEqual({
    aply_bgn_ym: "2026-11",
    num_val: 11,
  });
  expect(await screen.findByText("0 %")).toBeVisible();
});
it("기준값 전체 이력은 미래를 포함하고 저장 버튼 없이 원래 탭으로 닫힌다", async () => {
  await show();
  tab("기준값");
  fireEvent.click(await screen.findByRole("button", { name: "이력 보기" }));
  const modal = within(await screen.findByRole("dialog"));
  expect(await modal.findByText("2027-01")).toBeVisible();
  expect(modal.queryByRole("button", { name: "저장" })).not.toBeInTheDocument();
  fireEvent.click(modal.getByRole("button", { name: "닫기" }));
  expect(screen.getByRole("tab", { name: "기준값" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
});
it("읽기 전용 이력은 기본 사용자·50건·다음 페이지 및 그룹 필터를 유지한다", async () => {
  await show();
  tab("변경 이력");
  await screen.findByText("기본 사용자");
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([path]) => String(path).includes("page=2")),
    ).toBe(true),
  );
  fireEvent.change(screen.getByLabelText("그룹", { exact: false }), {
    target: { value: "PARAM" },
  });
  await waitFor(() =>
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(
          ([path]) =>
            String(path).includes("page=1") &&
            String(path).includes("cd_grp_id=PARAM"),
        ),
    ).toBe(true),
  );
});
it("빈 목록은 가짜 그룹/코드 없이 빈 상태를 표시하고 코드 조회를 호출하지 않는다", async () => {
  vi.mocked(fetch).mockResolvedValue(ok([]));
  render(<CommonCodeScreen />);
  await screen.findByText("조건에 맞는 결과가 없습니다");
  expect(fetch).toHaveBeenCalledTimes(1);
});
it("초기 읽기 실패는 오류와 GET 재시도를 제공한다", async () => {
  vi.mocked(fetch).mockRejectedValueOnce(new Error("offline"));
  render(<CommonCodeScreen />);
  fireEvent.click(await screen.findByRole("button", { name: "다시 시도" }));
  expect(await screen.findByDisplayValue("백엔드")).toBeVisible();
});
it("공통 client 204는 JSON을 읽지 않으며 헤더 누락은 거부한다", async () => {
  vi.mocked(fetch).mockResolvedValueOnce(
    new Response(null, { status: 204, headers: { "X-Request-Id": "id" } }),
  );
  await expect(
    write("/api/admin/code-groups", "POST", {}),
  ).resolves.toBeUndefined();
  vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
  await expect(
    write("/api/admin/code-groups", "POST", {}),
  ).rejects.toBeInstanceOf(RequestError);
});
it("업무 오류 code/field를 보존하고 GET 봉투 문의번호 검증은 유지한다", async () => {
  vi.mocked(fetch).mockResolvedValueOnce(failure());
  await expect(commonCodeApi.groups()).rejects.toMatchObject({
    code: "DUPLICATE_CODE",
    field: "codes[0].cd_nm",
  });
  vi.mocked(fetch).mockResolvedValueOnce(
    new Response(
      JSON.stringify({ success: true, data: [], meta: { requestId: "wrong" } }),
      { headers: { "X-Request-Id": "id" } },
    ),
  );
  await expect(request("/api/admin/code-groups")).rejects.toThrow(
    "응답 문의 번호가 일치하지 않습니다.",
  );
});
it("기존 시스템 메뉴가 /UI-27 업무 본문에 연결되며 모바일 안내를 유지한다", async () => {
  const original = vi.mocked(fetch).getMockImplementation()!;
  vi.mocked(fetch).mockImplementation((path, init) =>
    path === "/api/menus"
      ? Promise.resolve(
          ok([
            {
              menuId: 1,
              name: "시스템",
              screenId: null,
              icon: null,
              mobile: true,
              children: [
                {
                  menuId: 2,
                  name: "공통코드",
                  screenId: "UI-27",
                  icon: null,
                  mobile: false,
                  children: [],
                },
              ],
            },
          ]),
        )
      : original(path, init),
  );
  render(
    <MemoryRouter initialEntries={["/UI-27"]}>
      <SharedWorkspace />
    </MemoryRouter>,
  );
  expect(
    await screen.findByRole("heading", { name: "공통코드", level: 1 }),
  ).toBeVisible();
  expect(await screen.findByDisplayValue("백엔드")).toBeVisible();
});

it("그룹 작업 창 취소 후에도 기존 코드 초안의 dirty 보호가 유지된다", async () => {
  await show();
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "기존 초안" },
  });
  fireEvent.click(screen.getByRole("button", { name: "+ 그룹 추가" }));
  fireEvent.click(
    within(await screen.findByRole("dialog")).getByRole("button", {
      name: "취소",
    }),
  );
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
  tab("기준값");
  expect(await screen.findByRole("dialog")).toHaveTextContent("미저장 내용");
  fireEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByDisplayValue("기존 초안")).toBeVisible();
});
