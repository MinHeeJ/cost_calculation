import { afterEach, expect, it, vi } from "vitest";
import {
  getMenus,
  getMonthClose,
  getHealth,
  getCodeGroups,
  createCodeGroup,
  getAdminCodes,
  saveCodes,
  getCodeHistory,
  commonCodeApi,
  getParam,
  write,
  useCodes,
  invalidateCodes,
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

it("공통코드 registry는 선택한 식별자로 전체 목록과 공개 선택지를 조회한다", async () => {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(ok([])));
  vi.stubGlobal("fetch", fetcher);
  const group = "선택 그룹/직무";
  await getCodeGroups();
  await commonCodeApi.groups();
  await getAdminCodes(group);
  await commonCodeApi.codes(group);
  await commonCodeApi.choices(group);
  await commonCodeApi.maps();
  expect(fetcher.mock.calls.map(([path]) => path)).toEqual([
    "/api/admin/code-groups",
    "/api/admin/code-groups",
    `/api/admin/code-groups/${encodeURIComponent(group)}/codes`,
    `/api/admin/code-groups/${encodeURIComponent(group)}/codes`,
    `/api/codes/${encodeURIComponent(group)}`,
    "/api/admin/excel-maps",
  ]);
  expect(typeof useCodes).toBe("function");
  expect(typeof invalidateCodes).toBe("function");
  for (const [, init] of fetcher.mock.calls) {
    expect(new Headers(init.headers).has("Authorization")).toBe(false);
    expect(init.credentials).toBeUndefined();
  }
});

it("기준값 registry는 현재 목록, 전체 이력, 선택월 적용값의 서로 다른 봉투를 보존한다", async () => {
  const value = {
    oprt_std_id: "VAT_RATE",
    aply_bgn_ym: "2026-11",
    oprt_std_nm: "부가세율",
    num_val: 11,
    txt_val: null,
    unit_nm: "%",
    use_plc_desc: "세금",
  };
  const current = { items: [value], last_closed_ym: "2026-09" };
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(ok(current))
    .mockResolvedValueOnce(ok([value]))
    .mockResolvedValueOnce(ok(value));
  vi.stubGlobal("fetch", fetcher);
  await expect(commonCodeApi.standards()).resolves.toEqual(current);
  await expect(
    commonCodeApi.standardHistory(value.oprt_std_id),
  ).resolves.toEqual([value]);
  await expect(getParam(value.oprt_std_id, value.aply_bgn_ym)).resolves.toEqual(
    value,
  );
  expect(fetcher.mock.calls.map(([path]) => path)).toEqual([
    "/api/admin/operating-standards",
    "/api/admin/operating-standards/VAT_RATE/history",
    "/api/params/VAT_RATE?month=2026-11",
  ]);
});

it("이력 registry는 전체 필터를 생략하고 선택 그룹과 페이지를 보존한다", async () => {
  const data = { items: [], total: 0, page: 2, size: 20 };
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(ok(data)));
  vi.stubGlobal("fetch", fetcher);
  await expect(getCodeHistory("PARAM", 2, 20)).resolves.toEqual(data);
  await commonCodeApi.history("", 1);
  const selected = new URLSearchParams(fetcher.mock.calls[0][0].split("?")[1]);
  expect(Object.fromEntries(selected)).toEqual({
    cd_grp_id: "PARAM",
    page: "2",
    size: "20",
    sort: "chg_at,desc",
  });
  expect(
    new URLSearchParams(fetcher.mock.calls[1][0].split("?")[1]).has(
      "cd_grp_id",
    ),
  ).toBe(false);
});

it("다섯 쓰기는 JSON을 읽지 않고 추적 헤더가 있는 204를 void로 처리한다", async () => {
  const json = vi.fn();
  const fetcher = vi.fn().mockImplementation(() => {
    const response = new Response(null, {
      status: 204,
      headers: { "X-Request-Id": "saved" },
    });
    response.json = json;
    return Promise.resolve(response);
  });
  vi.stubGlobal("fetch", fetcher);
  const group = { cd_grp_id: "TEST_GROUP", cd_grp_nm: "테스트 그룹" };
  const row = {
    dtl_cd: "BE",
    cd_nm: "백엔드",
    sort_seq: 1,
    use_yn: "Y" as const,
    attr1_val: null,
    attr2_val: null,
    attr3_val: null,
    aply_bgn_dt: null,
  };
  const map = {
    excel_lbl_nm: "개발",
    trns_cd_grp_id: group.cd_grp_id,
    trns_cd: row.dtl_cd,
    map_role_cd: "SUB",
  };
  const selectedMap = { map_id: 73 };
  const selectedStandard = { oprt_std_id: "VAT_RATE" };
  await expect(createCodeGroup(group)).resolves.toBeUndefined();
  await expect(saveCodes(group.cd_grp_id, [row])).resolves.toBeUndefined();
  await expect(
    write("/api/admin/excel-maps", "POST", map),
  ).resolves.toBeUndefined();
  await expect(
    write(`/api/admin/excel-maps/${selectedMap.map_id}`, "PATCH", {
      use_yn: "N",
    }),
  ).resolves.toBeUndefined();
  await expect(
    write(
      `/api/admin/operating-standards/${selectedStandard.oprt_std_id}`,
      "PUT",
      {
        aply_bgn_ym: "2026-11",
        num_val: 11,
      },
    ),
  ).resolves.toBeUndefined();
  expect(json).not.toHaveBeenCalled();
  expect(
    fetcher.mock.calls.map(([path, init]) => [
      path,
      init.method,
      JSON.parse(init.body),
    ]),
  ).toEqual([
    ["/api/admin/code-groups", "POST", group],
    [
      `/api/admin/code-groups/${group.cd_grp_id}/codes`,
      "PUT",
      { codes: [row] },
    ],
    ["/api/admin/excel-maps", "POST", map],
    [`/api/admin/excel-maps/${selectedMap.map_id}`, "PATCH", { use_yn: "N" }],
    [
      `/api/admin/operating-standards/${selectedStandard.oprt_std_id}`,
      "PUT",
      {
        aply_bgn_ym: "2026-11",
        num_val: 11,
      },
    ],
  ]);
});

it.each([null, " "])(
  "204 추적 헤더 %s는 신뢰하지 않고 쓰기를 자동 재시도하지 않는다",
  async (id) => {
    const headers = new Headers();
    if (id !== null) headers.set("X-Request-Id", id);
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204, headers }));
    vi.stubGlobal("fetch", fetcher);
    await expect(
      createCodeGroup({ cd_grp_id: "TEST_GROUP", cd_grp_nm: "그룹" }),
    ).rejects.toMatchObject({ source: "client", status: 204 });
    expect(fetcher).toHaveBeenCalledTimes(1);
  },
);

it.each(["DUPLICATE_CODE", "SYSTEM_CODE_LOCKED", "CLOSED_MONTH"])(
  "업무 오류 %s를 registry 경계에서 잃지 않는다",
  async (code) => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          code,
          message: "저장할 수 없습니다.",
          field: "aply_bgn_ym",
          requestId: "conflict",
          meta: { requestId: "conflict" },
        }),
        { status: 409, headers: { "X-Request-Id": "conflict" } },
      ),
    );
    vi.stubGlobal("fetch", fetcher);
    await expect(
      write("/api/admin/operating-standards/VAT_RATE", "PUT", {}),
    ).rejects.toMatchObject({
      source: "server",
      status: 409,
      code,
      field: "aply_bgn_ym",
      requestId: "conflict",
    });
    expect(fetcher).toHaveBeenCalledTimes(1);
  },
);
