import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CodeHistoryPanel } from "./CodeHistoryPanel";
import { getCodeHistory } from "./api";

const entry = {
  cd_hist_id: 51,
  cd_grp_id: "POSITION",
  cd_grp_nm: "직급",
  dtl_cd: "TL",
  cd_chg_ty_cd: "UPDATE",
  item_nm: "attr1_val",
  bfr_val: "200000",
  aft_val: "250000",
  chg_user_id: null,
  chg_at: "2026-10-15T09:30:00",
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
function routes(
  history: (query: URLSearchParams) => Response | Promise<Response>,
) {
  const fetch = vi.fn((path: string) => {
    if (path === "/api/admin/code-groups") {
      return Promise.resolve(
        respond([{ cd_grp_id: "POSITION", cd_grp_nm: "직급" }]),
      );
    }
    return Promise.resolve(
      history(new URL(path, "https://test.invalid").searchParams),
    );
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
afterEach(() => vi.unstubAllGlobals());

it("공개 읽기 전용 이력은 원본 값·서울 일시·기본 사용자와 기본 50건 페이지를 표시한다", async () => {
  const fetch = routes(() =>
    respond({ items: [entry], total: 51, page: 1, size: 50 }),
  );
  render(<CodeHistoryPanel />);
  expect(await screen.findByRole("cell", { name: "TL" })).toBeVisible();
  for (const text of [
    "수정",
    "attr1_val",
    "200000",
    "250000",
    "기본 사용자",
    "2026-10-15 09:30",
  ]) {
    expect(screen.getByRole("cell", { name: text })).toBeVisible();
  }
  expect(screen.getByLabelText("페이지당 건수")).toHaveValue("50");
  expect(screen.getByRole("button", { name: "이전 페이지" })).toBeDisabled();
  expect(
    screen.queryByRole("button", { name: /저장|삭제|추가|수정/ }),
  ).not.toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(fetch.mock.calls.some(([path]) => path.includes("size=50"))).toBe(
    true,
  );
  expect(fetch.mock.calls.every(([path]) => path.startsWith("/api/"))).toBe(
    true,
  );
});

it("다음 페이지와 그룹 변경은 실제 key를 사용하고 필터 변경 시 1페이지로 돌아간다", async () => {
  const fetch = routes((query) =>
    respond({
      items: [{ ...entry, cd_hist_id: query.get("page") === "2" ? 1 : 51 }],
      total: 51,
      page: Number(query.get("page")),
      size: Number(query.get("size")),
    }),
  );
  render(<CodeHistoryPanel />);
  await screen.findByRole("cell", { name: "TL" });
  fireEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await screen.findByText("51-51 / 51건");
  expect(screen.getByRole("button", { name: "다음 페이지" })).toBeDisabled();
  fireEvent.change(screen.getByLabelText("그룹"), {
    target: { value: "POSITION" },
  });
  await screen.findByText("1-50 / 51건");
  expect(fetch.mock.calls.at(-1)?.[0]).toContain("cd_grp_id=POSITION");
  expect(fetch.mock.calls.at(-1)?.[0]).toContain("page=1");
  fireEvent.change(screen.getByLabelText("페이지당 건수"), {
    target: { value: "20" },
  });
  await screen.findByText("1-20 / 51건");
  fireEvent.change(screen.getByLabelText("그룹"), { target: { value: "" } });
  await waitFor(() =>
    expect(fetch.mock.calls.at(-1)?.[0]).not.toContain("cd_grp_id"),
  );
});

it("PARAM은 기준값을 표시하고 데이터가 없으면 계약의 빈 목록 안내를 표시한다", async () => {
  const fetch = routes((query) =>
    respond({
      items:
        query.get("cd_grp_id") === "PARAM"
          ? [{ ...entry, cd_grp_id: "PARAM", cd_grp_nm: "기준값" }]
          : [],
      total: query.get("cd_grp_id") === "PARAM" ? 1 : 0,
      page: 1,
      size: 50,
    }),
  );
  render(<CodeHistoryPanel />);
  expect(await screen.findByText("조건에 맞는 결과가 없습니다")).toBeVisible();
  fireEvent.change(screen.getByLabelText("그룹"), {
    target: { value: "PARAM" },
  });
  expect(await screen.findByRole("cell", { name: "기준값" })).toBeVisible();
  expect(fetch.mock.calls.at(-1)?.[0]).toContain("cd_grp_id=PARAM");
});

it("실패는 빈 성공으로 숨기지 않고 문의 번호를 보이며 같은 GET만 재시도한다", async () => {
  let fail = true;
  const fetch = routes(() => {
    if (!fail) return respond({ items: [], total: 0, page: 1, size: 50 });
    return new Response(
      JSON.stringify({
        success: false,
        code: "500",
        message: "internal",
        requestId: "server-id",
        meta: { requestId: "server-id" },
      }),
      { status: 500, headers: { "X-Request-Id": "server-id" } },
    );
  });
  render(<CodeHistoryPanel />);
  expect(await screen.findByText(/server-id/)).toBeVisible();
  expect(
    screen.queryByText("조건에 맞는 결과가 없습니다"),
  ).not.toBeInTheDocument();
  fail = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("조건에 맞는 결과가 없습니다")).toBeVisible();
  expect(
    fetch.mock.calls.filter(([path]) => path.includes("code-history")),
  ).toHaveLength(2);
});

it("늦게 완료된 이전 필터 응답은 현재 필터 이력을 덮어쓰지 않는다", async () => {
  let release: (value: Response) => void = () => undefined;
  routes((query) =>
    query.has("cd_grp_id")
      ? respond({
          items: [{ ...entry, cd_grp_nm: "기준값" }],
          total: 1,
          page: 1,
          size: 50,
        })
      : new Promise<Response>((resolve) => {
          release = resolve;
        }),
  );
  render(<CodeHistoryPanel />);
  await screen.findByRole("option", { name: "직급" });
  expect(screen.getByLabelText("불러오는 중")).toBeVisible();
  fireEvent.change(screen.getByLabelText("그룹"), {
    target: { value: "PARAM" },
  });
  await screen.findByRole("cell", { name: "기준값" });
  release(respond({ items: [entry], total: 1, page: 1, size: 50 }));
  await waitFor(() =>
    expect(
      screen.queryByRole("cell", { name: "직급" }),
    ).not.toBeInTheDocument(),
  );
});

it("API 어댑터는 특수문자 그룹도 안전하게 인코딩하고 응답 data를 한 번만 반환한다", async () => {
  const fetch = routes(() =>
    respond({ items: [], total: 0, page: 2, size: 100 }),
  );
  expect(await getCodeHistory("A&B", 2, 100)).toEqual({
    items: [],
    total: 0,
    page: 2,
    size: 100,
  });
  expect(fetch.mock.calls[0][0]).toContain("cd_grp_id=A%26B");
});
