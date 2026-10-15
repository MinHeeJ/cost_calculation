import { afterEach, expect, it, vi } from "vitest";
import {
  getMenus,
  getMonthClose,
  getHealth,
  request,
  api,
  RequestError,
  getCodeGroups,
  getAdminCodes,
  createCodeGroup,
  saveCodes,
  getExcelMaps,
  createExcelMap,
  updateExcelMap,
  getCodes,
  getParam,
  getCodeHistory,
  getOperatingStandards,
  getOperatingStandardHistory,
  saveOperatingStandard,
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

const group = "JOB";
const standardKey = "VAT_RATE";
const mapId = 42;
it.each([
  ["그룹", () => getCodeGroups(), "/api/admin/code-groups", []],
  [
    "관리 코드",
    () => getAdminCodes(group),
    `/api/admin/code-groups/${group}/codes`,
    [],
  ],
  ["선택 코드", () => getCodes(group), `/api/codes/${group}`, []],
  ["엑셀 대응", () => getExcelMaps(), "/api/admin/excel-maps", []],
  [
    "현재 기준값",
    () => getOperatingStandards(),
    "/api/admin/operating-standards",
    { items: [], last_closed_ym: null },
  ],
  [
    "전체 적용 이력",
    () => getOperatingStandardHistory(standardKey),
    `/api/admin/operating-standards/${standardKey}/history`,
    [],
  ],
  [
    "공통 기준값",
    () => getParam(standardKey, "2026-10"),
    `/api/params/${standardKey}?month=2026-10`,
    { oprt_std_id: standardKey, num_val: 10 },
  ],
  [
    "변경 이력",
    () => getCodeHistory(group),
    "/api/admin/code-history?page=1&size=50&sort=chg_at%2Cdesc&cd_grp_id=JOB",
    { items: [], total: 0, page: 1, size: 50 },
  ],
] as const)(
  "공개 registry의 %s는 계약 경로와 data 형태를 그대로 유지한다",
  async (_name, call, path, data) => {
    const fetcher = vi.fn().mockResolvedValue(ok(data));
    vi.stubGlobal("fetch", fetcher);
    await expect(call()).resolves.toEqual(data);
    expect(fetcher).toHaveBeenCalledWith(
      path,
      expect.objectContaining({
        headers: { "X-Request-Id": expect.any(String) },
      }),
    );
    expect(fetcher).toHaveBeenCalledTimes(1);
  },
);

const newGroup = { cd_grp_id: "DELIVERY", cd_grp_nm: "전달" };
const newMap = {
  excel_lbl_nm: "개발",
  trns_cd_grp_id: group,
  trns_cd: "BE",
  map_role_cd: "MAIN" as const,
};
const mapChange = { rmrk_desc: null, use_yn: "N" as const };
const standardChange = { aply_bgn_ym: "2026-10", num_val: 10 };
it.each([
  [
    "그룹 추가",
    () => createCodeGroup(newGroup),
    "/api/admin/code-groups",
    "POST",
    newGroup,
  ],
  [
    "전체 코드 저장",
    () => saveCodes(group, []),
    `/api/admin/code-groups/${group}/codes`,
    "PUT",
    { codes: [] },
  ],
  [
    "대응 추가",
    () => createExcelMap(newMap),
    "/api/admin/excel-maps",
    "POST",
    newMap,
  ],
  [
    "대응 변경",
    () => updateExcelMap(mapId, mapChange),
    `/api/admin/excel-maps/${mapId}`,
    "PATCH",
    mapChange,
  ],
  [
    "기준값 저장",
    () => saveOperatingStandard(standardKey, standardChange),
    `/api/admin/operating-standards/${standardKey}`,
    "PUT",
    standardChange,
  ],
] as const)(
  "공개 registry의 %s는 JSON payload와 204를 보존하며 재제출하지 않는다",
  async (_name, call, path, method, body) => {
    const response = new Response(null, {
      status: 204,
      headers: { "X-Request-Id": "write-request" },
    });
    const parse = vi.spyOn(response, "json");
    const fetcher = vi.fn().mockResolvedValue(response);
    vi.stubGlobal("fetch", fetcher);
    await expect(call()).resolves.toBeUndefined();
    expect(parse).not.toHaveBeenCalled();
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher).toHaveBeenCalledWith(
      path,
      expect.objectContaining({
        method,
        body: JSON.stringify(body),
        headers: expect.objectContaining({
          "X-Request-Id": expect.any(String),
        }),
      }),
    );
    const headers = new Headers(fetcher.mock.calls[0][1].headers);
    expect(headers.get("Content-Type")).toBe("application/json");
  },
);
