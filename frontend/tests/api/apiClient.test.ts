import { afterEach, expect, it, vi } from "vitest";
import {
  getMenus,
  getMonthClose,
  getHealth,
  request,
  api,
  RequestError,
} from "../../src/api/apiClient";

function ok(data: unknown) {
  return new Response(
    JSON.stringify({
      success: true,
      data,
      meta: { requestId: "client-contract" },
    }),
    {
      headers: { "X-Request-Id": "client-contract" },
    },
  );
}
afterEach(() => vi.unstubAllGlobals());
it("메뉴 등록은 비페이지 data 배열을 한 번만 풀며 인증을 요구하지 않는다", async () => {
  const menu = {
    menuId: 1,
    name: "메뉴",
    screenId: null,
    icon: null,
    mobile: true,
    children: [],
  };
  const fetcher = vi.fn().mockResolvedValue(ok([menu]));
  vi.stubGlobal("fetch", fetcher);
  await expect(getMenus()).resolves.toEqual([menu]);
  expect(fetcher).toHaveBeenCalledWith(
    "/api/menus",
    expect.objectContaining({
      headers: { "X-Request-Id": expect.any(String) },
    }),
  );
});
it("월 조회는 호출자가 선택한 from/to를 전달하고 월 배열을 그대로 반환한다", async () => {
  const data = [
    { month: "0000-01", status: "OPEN" },
    { month: "0000-02", status: "CLOSED" },
  ];
  const fetcher = vi.fn().mockResolvedValue(ok(data));
  vi.stubGlobal("fetch", fetcher);
  await expect(getMonthClose("0000-01", "0000-02")).resolves.toEqual(data);
  expect(fetcher).toHaveBeenCalledWith(
    "/api/month-close?from=0000-01&to=0000-02",
    expect.any(Object),
  );
});
it("health 등록은 승인된 GET 경로와 UP 객체를 사용한다", async () => {
  const fetcher = vi.fn().mockResolvedValue(ok({ status: "UP" }));
  vi.stubGlobal("fetch", fetcher);
  await expect(getHealth()).resolves.toEqual({ status: "UP" });
  expect(fetcher).toHaveBeenCalledWith("/api/health", expect.any(Object));
});
it("공개 registry도 원본 추적 검증과 수동 재시도 오류 정책을 보존한다", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        code: "400",
        message: "월 형식을 확인해 주세요.",
        field: "from",
        requestId: "validation",
        meta: { requestId: "validation" },
      }),
      { status: 400, headers: { "X-Request-Id": "validation" } },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await expect(getMonthClose("invalid", "2026-10")).rejects.toMatchObject({
    source: "server",
    status: 400,
    field: "from",
    requestId: "validation",
  });
  expect(fetcher).toHaveBeenCalledTimes(1);
  fetcher.mockResolvedValueOnce(ok([]));
  await expect(api.get("/api/menus")).resolves.toEqual([]);
  await expect(request("https://example.com/api/health")).rejects.toThrow(
    "상대 경로",
  );
  expect(RequestError).toBeDefined();
});
it("등록된 health도 헤더와 본문 ID 불일치를 서버 문의번호로 신뢰하지 않는다", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: { status: "UP" },
          meta: { requestId: "body" },
        }),
        { headers: { "X-Request-Id": "header" } },
      ),
    ),
  );
  await expect(getHealth()).rejects.toMatchObject({ source: "client" });
});
