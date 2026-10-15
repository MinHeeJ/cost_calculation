import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { CommonCodeScreen } from "./CommonCodeScreen";
import { useNavigation } from "../pages/shared/navigation";
import { request } from "../api/client";
import type { AdminCode, CodeGroup } from "../pages/codegroups/api";

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
const initial: AdminCode[] = [
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
let stored: AdminCode[];
let failRead = false;
let failWrite = false;
let fetcher: ReturnType<typeof vi.fn>;
beforeEach(() => {
  stored = structuredClone(initial);
  failRead = false;
  failWrite = false;
  useNavigation.setState({ dirty: {}, screenState: {}, active: "UI-27" });
  fetcher = vi.fn(async (path: string, init?: RequestInit) => {
    if (init?.method === "PUT") {
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
      stored = JSON.parse(String(init.body)).codes.map((row: AdminCode) => ({
        ...row,
        cd_grp_id: "JOB",
      }));
      return new Response(null, {
        status: 204,
        headers: { "X-Request-Id": "server-id" },
      });
    }
    if (init?.method === "POST") {
      return new Response(null, {
        status: 204,
        headers: { "X-Request-Id": "server-id" },
      });
    }
    if (path === "/api/admin/code-groups") return respond(groups);
    if (failRead) throw new TypeError("connection");
    if (path.includes("CONTRACT_TYPE"))
      return respond([
        {
          ...initial[0],
          cd_grp_id: "CONTRACT_TYPE",
          dtl_cd: "TK",
          cd_nm: "턴키",
        },
      ]);
    return respond(stored);
  });
  vi.stubGlobal("fetch", fetcher);
});
afterEach(() => vi.unstubAllGlobals());
async function open() {
  render(<CommonCodeScreen />);
  await screen.findByDisplayValue("백엔드");
}
it("관리 표는 중지 코드와 동적 추가 값 제목을 보존한다", async () => {
  await open();
  expect(
    screen.getByRole("columnheader", { name: "기본 단가(원)" }),
  ).toBeVisible();
  expect(screen.getByLabelText("사용 2")).not.toBeChecked();
  expect(screen.getByLabelText("코드 1")).toHaveAttribute("readonly");
  expect(screen.getByDisplayValue("운영")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "삭제" }),
  ).not.toBeInTheDocument();
});
it("SYSTEM은 안내와 잠금, 이름/순서 외 읽기 전용이며 추가는 숨긴다", async () => {
  await open();
  fireEvent.click(screen.getByTestId("group-CONTRACT_TYPE"));
  await screen.findByDisplayValue("턴키");
  expect(screen.getByText(/계산 규칙에 쓰는 시스템 코드/)).toBeVisible();
  expect(screen.getByLabelText("사용 1")).toBeDisabled();
  expect(screen.getByLabelText("적용 시작일 1")).toHaveAttribute("readonly");
  expect(
    screen.queryByRole("button", { name: "+ 코드 추가" }),
  ).not.toBeInTheDocument();
});
it("그룹 이동 전 CMN-402 취소는 그룹과 입력을 유지한다", async () => {
  await open();
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "작성 중" },
  });
  fireEvent.click(screen.getByTestId("group-CONTRACT_TYPE"));
  await screen.findByRole("dialog");
  fireEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(screen.getByDisplayValue("작성 중")).toBeVisible();
  expect(screen.getByTestId("group-JOB")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
});
it("추가 행은 최대 순서+1이고 취소하면 snapshot과 dirty를 복원한다", async () => {
  await open();
  fireEvent.click(screen.getByRole("button", { name: "+ 코드 추가" }));
  expect(screen.getByLabelText("순서 3")).toHaveValue(3);
  expect(screen.getByLabelText("코드 3")).not.toHaveAttribute("readonly");
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  await waitFor(() =>
    expect(screen.queryByLabelText("코드 3")).not.toBeInTheDocument(),
  );
  expect(useNavigation.getState().dirty["UI-27"]).toBe(false);
});
it("204 저장은 JSON parsing 없이 성공하고 전체 목록 재조회 및 선택 훅 무효화한다", async () => {
  const invalidated = vi.fn();
  window.addEventListener("codes-invalidated", invalidated);
  await open();
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "수정 이름" },
  });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("공통코드를 저장했습니다.");
  await waitFor(() => expect(screen.getByLabelText("이름 1")).toBeEnabled());
  expect(screen.getByDisplayValue("수정 이름")).toBeVisible();
  const puts = fetcher.mock.calls.filter(([, init]) => init?.method === "PUT");
  expect(puts).toHaveLength(1);
  const payload = JSON.parse(String(puts[0][1]?.body));
  expect(payload.codes).toHaveLength(2);
  expect(payload.codes[0]).not.toHaveProperty("cd_grp_id");
  expect(invalidated).toHaveBeenCalledTimes(1);
  window.removeEventListener("codes-invalidated", invalidated);
});
it("커밋 뒤 재조회 실패의 다시 시도는 GET만 수행한다", async () => {
  await open();
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "저장된 이름" },
  });
  failRead = true;
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByRole("alert");
  failRead = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await waitFor(() =>
    expect(screen.queryByRole("alert")).not.toBeInTheDocument(),
  );
  expect(
    fetcher.mock.calls.filter(([, init]) => init?.method === "PUT"),
  ).toHaveLength(1);
});
it("409 오류는 문의번호와 입력값을 유지한다", async () => {
  await open();
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "오류 초안" },
  });
  failWrite = true;
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "이미 있는 코드입니다.",
  );
  expect(screen.getByDisplayValue("오류 초안")).toBeVisible();
  expect(useNavigation.getState().dirty["UI-27"]).toBe(true);
});
it("네 하위 탭은 독립 패널을 보이며 dirty 탭 전환 취소는 편집을 유지한다", async () => {
  render(
    <CommonCodeScreen
      panels={{
        "excel-maps": <p>대응 목록</p>,
        "operating-standards": <p>기준값 목록</p>,
        "code-history": <p>이력 목록</p>,
      }}
    />,
  );
  await screen.findByDisplayValue("백엔드");
  fireEvent.change(screen.getByLabelText("이름 1"), {
    target: { value: "미저장" },
  });
  fireEvent.click(screen.getByRole("tab", { name: "엑셀 표기 대응" }));
  fireEvent.click(await screen.findByRole("button", { name: "계속 작성" }));
  expect(screen.getByDisplayValue("미저장")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  for (const [tab, text] of [
    ["엑셀 표기 대응", "대응 목록"],
    ["기준값", "기준값 목록"],
    ["변경 이력", "이력 목록"],
  ]) {
    fireEvent.click(screen.getByRole("tab", { name: tab }));
    expect(await screen.findByText(text)).toBeVisible();
    expect(screen.queryByLabelText("이름 1")).not.toBeInTheDocument();
  }
});
it("그룹 작업창은 배경/ESC로 닫히지 않고 실제 입력으로 POST한다", async () => {
  await open();
  fireEvent.click(screen.getByRole("button", { name: "+ 그룹 추가" }));
  const dialog = await screen.findByRole("dialog", { name: "그룹 추가" });
  fireEvent.keyDown(dialog, { key: "Escape" });
  expect(dialog).toBeVisible();
  fireEvent.change(within(dialog).getByLabelText("그룹 코드"), {
    target: { value: "TEST_GROUP" },
  });
  fireEvent.change(within(dialog).getByLabelText("그룹명"), {
    target: { value: "테스트 그룹" },
  });
  fireEvent.click(within(dialog).getByRole("button", { name: "그룹 저장" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  const posts = fetcher.mock.calls.filter(
    ([, init]) => init?.method === "POST",
  );
  expect(JSON.parse(String(posts[0][1]?.body))).toEqual({
    cd_grp_id: "TEST_GROUP",
    cd_grp_nm: "테스트 그룹",
  });
});
it("빈 신규 코드는 정확한 오류를 보이고 전송하지 않는다", async () => {
  await open();
  fireEvent.click(screen.getByRole("button", { name: "+ 코드 추가" }));
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "코드를 입력하세요.",
  );
  expect(
    fetcher.mock.calls.filter(([, init]) => init?.method === "PUT"),
  ).toHaveLength(0);
});
it("초기 조회 실패는 오류와 수동 재시도를 제공한다", async () => {
  failRead = true;
  render(<CommonCodeScreen />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "연결이 끊겼습니다.",
  );
  failRead = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByDisplayValue("백엔드")).toBeVisible();
});
it("빈 목록은 지정 문구를 보인다", async () => {
  stored = [];
  render(<CommonCodeScreen />);
  expect(await screen.findByText("조건에 맞는 결과가 없습니다")).toBeVisible();
});
it("204에 문의번호가 없으면 성공으로 처리하지 않는다", async () => {
  fetcher.mockResolvedValueOnce(new Response(null, { status: 204 }));
  await expect(
    request<void>("/api/admin/code-groups", { method: "POST" }),
  ).rejects.toThrow("응답 문의 번호");
});
