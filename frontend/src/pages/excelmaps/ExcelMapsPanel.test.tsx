import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ExcelMapsPanel } from "./ExcelMapsPanel";
import { type ExcelMapView, createExcelMap } from "./api";
import { useNavigation } from "../shared/navigation";
import { invalidateCodes } from "./useCodes";

let rows: ExcelMapView[];
let calls: { path: string; method: string; body?: Record<string, unknown> }[];
let failRead: boolean;
let failWrite: boolean;
let fetcher: ReturnType<typeof vi.fn>;
const trace = "excel-contract-trace";
const envelope = (data: unknown) =>
  new Response(
    JSON.stringify({
      success: true,
      data,
      meta: { requestId: trace },
    }),
    { status: 200, headers: { "X-Request-Id": trace } },
  );
const error = (status: number, field: string, message: string) =>
  new Response(
    JSON.stringify({
      success: false,
      code: String(status),
      field,
      message,
      requestId: trace,
      meta: { requestId: trace },
    }),
    { status, headers: { "X-Request-Id": trace } },
  );

beforeEach(() => {
  rows = [
    {
      map_id: 24,
      excel_lbl_nm: "백엔드 표기",
      trns_cd_grp_id: "JOB",
      trns_cd: "BE",
      map_role_cd: "MAIN",
      rmrk_desc: "원본 메모",
      use_yn: "Y",
      cd_grp_nm: "직무",
      cd_nm: "백엔드",
    },
  ];
  calls = [];
  failRead = false;
  failWrite = false;
  useNavigation.setState({ dirty: {} });
  fetcher = vi.fn(async (path: string, init: RequestInit = {}) => {
    const method = init.method ?? "GET";
    const body = init.body ? JSON.parse(init.body as string) : undefined;
    calls.push({ path, method, body });
    if (method === "POST") {
      if (failWrite) return error(409, "excel_lbl_nm", "이미 있는 대응입니다.");
      rows = [...rows, { ...rows[0], ...body, map_id: 42, use_yn: "Y" }];
      return new Response(null, {
        status: 204,
        headers: { "X-Request-Id": trace },
      });
    }
    if (method === "PATCH") {
      if (failWrite) return error(400, "rmrk_desc", "메모를 확인하세요.");
      rows = rows.map((row) =>
        row.map_id === Number(path.split("/").at(-1))
          ? { ...row, ...body }
          : row,
      );
      return new Response(null, {
        status: 204,
        headers: { "X-Request-Id": trace },
      });
    }
    if (path === "/api/admin/excel-maps") {
      if (failRead) return error(500, "body", "조회 실패");
      return envelope(rows);
    }
    if (path === "/api/admin/code-groups")
      return envelope([
        { cd_grp_id: "JOB", cd_grp_nm: "직무" },
        { cd_grp_id: "POSITION", cd_grp_nm: "직급" },
      ]);
    if (path === "/api/codes/JOB")
      return envelope([
        { code: "BE", name: "백엔드", attr1: null, attr2: null, attr3: null },
      ]);
    if (path === "/api/codes/POSITION")
      return envelope([
        { code: "TL", name: "팀장", attr1: "200000", attr2: null, attr3: null },
      ]);
    throw new Error(`Unexpected request: ${method} ${path}`);
  });
  vi.stubGlobal("fetch", fetcher);
});

it("표기·그룹/코드명·주/보조를 표시하고 미저장 편집 취소는 원본과 dirty를 복원한다", async () => {
  rows.push({
    ...rows[0],
    map_id: 25,
    excel_lbl_nm: "운영",
    map_role_cd: "SUB",
    use_yn: "N",
  });
  render(<ExcelMapsPanel />);
  expect(await screen.findAllByText("직무 · 백엔드")).toHaveLength(2);
  expect(screen.getByLabelText("백엔드 표기 주/보조")).toHaveValue("MAIN");
  expect(screen.getByLabelText("운영 주/보조")).toHaveValue("SUB");
  expect(screen.getByLabelText("운영 사용")).not.toBeChecked();
  fireEvent.change(screen.getByLabelText("백엔드 표기 메모"), {
    target: { value: "새 메모" },
  });
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
  const row = screen.getByText("백엔드 표기").closest("tr")!;
  fireEvent.click(within(row).getByRole("button", { name: "취소" }));
  await waitFor(() =>
    expect(screen.getByLabelText("백엔드 표기 메모")).toHaveValue("원본 메모"),
  );
  expect(useNavigation.getState().dirty["UI-27"]).toBe(false);
  expect(calls.every((call) => call.method === "GET")).toBe(true);
});

it("선택한 실제 map_id의 변경 필드만 저장하고 204를 JSON으로 읽지 않는다", async () => {
  render(<ExcelMapsPanel />);
  const memo = await screen.findByLabelText("백엔드 표기 메모");
  fireEvent.change(memo, { target: { value: "수정" } });
  const row = screen.getByText("백엔드 표기").closest("tr")!;
  fireEvent.click(within(row).getByRole("button", { name: "저장" }));
  expect(
    await screen.findByText("엑셀 표기 대응을 저장했습니다."),
  ).toBeVisible();
  await waitFor(() =>
    expect(useNavigation.getState().dirty["UI-27"]).toBe(false),
  );
  expect(calls.find((call) => call.method === "PATCH")).toEqual({
    path: "/api/admin/excel-maps/24",
    method: "PATCH",
    body: { rmrk_desc: "수정" },
  });
  expect(screen.getByLabelText("백엔드 표기 메모")).toHaveValue("수정");
});

it("저장 후 조회 실패는 GET만 재시도하며 PATCH를 중복 제출하지 않는다", async () => {
  render(<ExcelMapsPanel />);
  fireEvent.change(await screen.findByLabelText("백엔드 표기 메모"), {
    target: { value: "수정" },
  });
  failRead = true;
  fireEvent.click(
    within(screen.getByText("백엔드 표기").closest("tr")!).getByRole("button", {
      name: "저장",
    }),
  );
  expect(await screen.findByText(/저장은 완료되었습니다/)).toBeVisible();
  failRead = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await waitFor(() =>
    expect(screen.queryByRole("alert")).not.toBeInTheDocument(),
  );
  expect(calls.filter((call) => call.method === "PATCH")).toHaveLength(1);
});

it("쓰기 오류는 입력과 dirty를 보존하고 field 오류를 보여 준다", async () => {
  failWrite = true;
  render(<ExcelMapsPanel />);
  fireEvent.change(await screen.findByLabelText("백엔드 표기 메모"), {
    target: { value: "미저장" },
  });
  fireEvent.click(
    within(screen.getByText("백엔드 표기").closest("tr")!).getByRole("button", {
      name: "저장",
    }),
  );
  expect(
    await screen.findByText("rmrk_desc: 메모를 확인하세요."),
  ).toBeVisible();
  expect(screen.getByLabelText("백엔드 표기 메모")).toHaveValue("미저장");
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
});

it("추가창 필수 검증·그룹 변경시 코드 초기화·API 선택 값·MAIN 기본값을 따른다", async () => {
  render(<ExcelMapsPanel />);
  fireEvent.click(await screen.findByRole("button", { name: "+ 대응 추가" }));
  const dialog = within(screen.getByRole("dialog"));
  fireEvent.click(dialog.getByRole("button", { name: "저장" }));
  expect(
    await dialog.findByText("excel_lbl_nm: 엑셀 글자를 입력하세요."),
  ).toBeVisible();
  fireEvent.change(dialog.getByLabelText("엑셀 표기"), {
    target: { value: "새 표기" },
  });
  await dialog.findByRole("option", { name: "직무" });
  fireEvent.change(dialog.getByLabelText("그룹"), { target: { value: "JOB" } });
  await dialog.findByRole("option", { name: "백엔드" });
  fireEvent.change(dialog.getByLabelText("코드"), { target: { value: "BE" } });
  fireEvent.change(dialog.getByLabelText("그룹"), {
    target: { value: "POSITION" },
  });
  expect(dialog.getByLabelText("코드")).toHaveValue("");
  await dialog.findByRole("option", { name: "팀장" });
  fireEvent.change(dialog.getByLabelText("코드"), { target: { value: "TL" } });
  fireEvent.click(dialog.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(calls.find((call) => call.method === "POST")?.body).toMatchObject({
    excel_lbl_nm: "새 표기",
    trns_cd_grp_id: "POSITION",
    trns_cd: "TL",
    map_role_cd: "MAIN",
  });
});

it("추가 중 취소는 서버 저장 없이 닫고 ESC·바깥 클릭은 입력을 유지한다", async () => {
  render(<ExcelMapsPanel />);
  fireEvent.click(await screen.findByRole("button", { name: "+ 대응 추가" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.change(within(dialog).getByLabelText("엑셀 표기"), {
    target: { value: "취소할 표기" },
  });
  fireEvent.keyDown(dialog, { key: "Escape" });
  fireEvent.click(dialog.parentElement!);
  expect(screen.getByRole("dialog")).toBeVisible();
  fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(calls.filter((call) => call.method !== "GET")).toHaveLength(0);
});

it("조회 빈 집합 및 실패 재시도 상태를 제공한다", async () => {
  rows = [];
  failRead = true;
  render(<ExcelMapsPanel />);
  expect(screen.getByText("불러오는 중입니다.")).toBeVisible();
  expect(await screen.findByRole("alert")).toBeVisible();
  failRead = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("조건에 맞는 결과가 없습니다")).toBeVisible();
});

it("그룹 코드 무효화가 열린 신규 선택 소비자를 재조회한다", async () => {
  render(<ExcelMapsPanel />);
  fireEvent.click(await screen.findByRole("button", { name: "+ 대응 추가" }));
  await screen.findByRole("option", { name: "직무" });
  fireEvent.change(screen.getByLabelText("그룹"), { target: { value: "JOB" } });
  await screen.findByRole("option", { name: "백엔드" });
  const before = calls.filter((call) => call.path === "/api/codes/JOB").length;
  act(() => invalidateCodes("JOB"));
  await waitFor(() =>
    expect(calls.filter((call) => call.path === "/api/codes/JOB")).toHaveLength(
      before + 1,
    ),
  );
});

it("204 header 없는 응답을 성공으로 취급하지 않는다", async () => {
  fetcher.mockResolvedValueOnce(new Response(null, { status: 204 }));
  await expect(
    createExcelMap({
      excel_lbl_nm: "표기",
      trns_cd_grp_id: "JOB",
      trns_cd: "BE",
      map_role_cd: "MAIN",
    }),
  ).rejects.toThrow("응답 문의 번호가 없습니다.");
});
