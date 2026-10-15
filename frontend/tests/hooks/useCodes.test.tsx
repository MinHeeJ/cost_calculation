import {
  act,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { getCodes, getParam } from "../../src/api/apiClient";
import { invalidateCodes, useCodes } from "../../src/pages/apiops/useCodes";
import { CodeSelect } from "../../src/pages/apiops/CodeSelect";

const choice = {
  code: "BE",
  name: "백엔드",
  attr1: "200000",
  attr2: null,
  attr3: null,
};
function ok(data: unknown) {
  return new Response(
    JSON.stringify({ success: true, data, meta: { requestId: "lookup" } }),
    {
      headers: { "X-Request-Id": "lookup" },
    },
  );
}
function failure() {
  return new Response(
    JSON.stringify({
      success: false,
      code: "500",
      message: "internal SQL",
      field: null,
      requestId: "lookup",
      meta: { requestId: "lookup" },
    }),
    { status: 500, headers: { "X-Request-Id": "lookup" } },
  );
}
afterEach(() => vi.unstubAllGlobals());

it("선택 목록 클라이언트는 선택한 그룹을 인코딩하고 공개 data를 한 번만 반환한다", async () => {
  const fetch = vi.fn().mockResolvedValue(ok([choice]));
  vi.stubGlobal("fetch", fetch);
  await expect(getCodes("A/B & C")).resolves.toEqual([choice]);
  expect(fetch).toHaveBeenCalledWith(
    "/api/codes/A%2FB%20%26%20C",
    expect.objectContaining({
      headers: { "X-Request-Id": expect.any(String) },
    }),
  );
  expect(fetch).toHaveBeenCalledTimes(1);
});

it("기준값 조회는 실제 선택 키와 요청 월을 보내고 snake_case 응답을 보존한다", async () => {
  const data = {
    oprt_std_id: "VAT_RATE",
    aply_bgn_ym: "2026-11",
    oprt_std_nm: "부가세율",
    num_val: 11,
    txt_val: null,
    unit_nm: "%",
    use_plc_desc: null,
  };
  const fetch = vi.fn().mockResolvedValue(ok(data));
  vi.stubGlobal("fetch", fetch);
  await expect(getParam(data.oprt_std_id, data.aply_bgn_ym)).resolves.toEqual(
    data,
  );
  expect(fetch).toHaveBeenCalledWith(
    "/api/params/VAT_RATE?month=2026-11",
    expect.any(Object),
  );
});

it("그룹 없음은 조회하지 않고 선택 목록은 API의 이름과 코드값을 사용한다", async () => {
  const fetch = vi.fn().mockResolvedValue(ok([choice]));
  vi.stubGlobal("fetch", fetch);
  const change = vi.fn();
  const { rerender } = render(
    <CodeSelect group={null} value="" onChange={change} />,
  );
  await waitFor(() => expect(screen.getByLabelText("코드")).toBeDisabled());
  expect(fetch).not.toHaveBeenCalled();
  rerender(<CodeSelect group="JOB" value="" onChange={change} />);
  expect(await screen.findByRole("option", { name: "백엔드" })).toHaveValue(
    "BE",
  );
  fireEvent.change(screen.getByLabelText("코드"), { target: { value: "BE" } });
  expect(change).toHaveBeenCalledWith("BE");
  expect(screen.getByLabelText("코드").className).toContain("input");
});

it("빈 목록은 정해진 안내를 표시하며 임의 선택지를 만들지 않는다", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(ok([])));
  render(<CodeSelect group="EMPTY" value="" onChange={vi.fn()} />);
  expect(await screen.findByText("조건에 맞는 결과가 없습니다")).toBeVisible();
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
});

it("오류는 빈 목록이 아니며 문의번호와 해당 GET 수동 재시도를 제공한다", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(failure())
    .mockResolvedValueOnce(ok([choice]));
  vi.stubGlobal("fetch", fetch);
  render(<CodeSelect group="JOB" value="" onChange={vi.fn()} />);
  expect(await screen.findByText(/lookup/)).toBeVisible();
  expect(
    screen.queryByText("조건에 맞는 결과가 없습니다"),
  ).not.toBeInTheDocument();
  expect(screen.queryByText("internal SQL")).not.toBeInTheDocument();
  expect(fetch).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByRole("option", { name: "백엔드" });
  expect(fetch).toHaveBeenCalledTimes(2);
  expect(fetch.mock.calls.every(([path]) => path === "/api/codes/JOB")).toBe(
    true,
  );
});

it("코드 저장 후 그룹 무효화는 해당 그룹의 모든 구독자만 재조회한다", async () => {
  let active = true;
  const fetch = vi.fn((path: string) =>
    Promise.resolve(ok(path === "/api/codes/JOB" && active ? [choice] : [])),
  );
  vi.stubGlobal("fetch", fetch);
  const first = renderHook(() => useCodes("JOB"));
  const second = renderHook(() => useCodes("JOB"));
  const other = renderHook(() => useCodes("POSITION"));
  await waitFor(() => expect(first.result.current.codes).toEqual([choice]));
  await waitFor(() => expect(second.result.current.codes).toEqual([choice]));
  await waitFor(() => expect(other.result.current.loading).toBe(false));
  const otherCalls = fetch.mock.calls.filter(
    ([path]) => path === "/api/codes/POSITION",
  ).length;
  active = false;
  act(() => invalidateCodes("JOB"));
  await waitFor(() => expect(first.result.current.codes).toEqual([]));
  await waitFor(() => expect(second.result.current.codes).toEqual([]));
  expect(
    fetch.mock.calls.filter(([path]) => path === "/api/codes/JOB"),
  ).toHaveLength(4);
  expect(
    fetch.mock.calls.filter(([path]) => path === "/api/codes/POSITION"),
  ).toHaveLength(otherCalls);
  first.unmount();
  second.unmount();
  act(() => invalidateCodes("JOB"));
  expect(
    fetch.mock.calls.filter(([path]) => path === "/api/codes/JOB"),
  ).toHaveLength(4);
});

it("그룹 변경 중 늦은 이전 응답은 현재 선택 목록을 덮지 않는다", async () => {
  let release!: (response: Response) => void;
  const fetch = vi.fn((path: string) =>
    path === "/api/codes/JOB"
      ? new Promise<Response>((resolve) => {
          release = resolve;
        })
      : Promise.resolve(ok([{ ...choice, code: "TL", name: "팀장" }])),
  );
  vi.stubGlobal("fetch", fetch);
  const hook = renderHook(({ group }) => useCodes(group), {
    initialProps: { group: "JOB" },
  });
  expect(hook.result.current.loading).toBe(true);
  hook.rerender({ group: "POSITION" });
  await waitFor(() => expect(hook.result.current.codes[0]?.code).toBe("TL"));
  await act(async () => {
    release(ok([choice]));
  });
  expect(hook.result.current.codes[0]?.code).toBe("TL");
});

it("진행중 조회를 무효화하면 이전 응답 대신 최신 그룹 조회 결과를 유지한다", async () => {
  let release!: (response: Response) => void;
  const fetch = vi
    .fn()
    .mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
    )
    .mockResolvedValueOnce(ok([]));
  vi.stubGlobal("fetch", fetch);
  const hook = renderHook(() => useCodes("JOB"));
  act(() => invalidateCodes("JOB"));
  await waitFor(() => expect(hook.result.current.loading).toBe(false));
  await act(async () => {
    release(ok([choice]));
  });
  expect(hook.result.current.codes).toEqual([]);
  expect(fetch).toHaveBeenCalledTimes(2);
});
