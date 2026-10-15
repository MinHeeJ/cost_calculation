import { useState } from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { request, RequestError } from "../../src/api/client";
import { ErrorBanner } from "../../src/components/ErrorBanner";

function Harness({
  error,
  retry,
}: {
  error: RequestError;
  retry: () => Promise<unknown>;
}) {
  const [value, setValue] = useState("작성 중");
  return (
    <>
      <input
        aria-label="입력"
        value={value}
        onChange={(event) => setValue(event.target.value)}
      />
      <ErrorBanner error={error} onRetry={retry} />
    </>
  );
}
it("서버 문의 번호를 검증하고 500 민감 메시지와 자동 재시도를 차단한다", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        code: "500",
        message: "SQL password=secret",
        field: null,
        requestId: "server-1",
        meta: { requestId: "server-1" },
      }),
      { status: 500, headers: { "X-Request-Id": "server-1" } },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  const error = (await request("/api/menus").catch(
    (value) => value,
  )) as RequestError;
  expect(error.source).toBe("server");
  expect(error.message).not.toContain("SQL");
  expect(fetcher).toHaveBeenCalledTimes(1);
  let done!: () => void;
  const retry = vi.fn(
    () =>
      new Promise<void>((resolve) => {
        done = resolve;
      }),
  );
  render(<Harness error={error} retry={retry} />);
  fireEvent.change(screen.getByLabelText("입력"), {
    target: { value: "보존할 입력" },
  });
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(retry).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("alert")).toHaveTextContent("문의 번호: server-1");
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
  expect(screen.getByLabelText("입력")).toHaveValue("보존할 입력");
  done();
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeEnabled(),
  );
});
it("응답 없는 연결 오류는 클라이언트 문의 번호를 쓰며 입력을 유지한다", async () => {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
  const error = (await request("/api/menus").catch(
    (value) => value,
  )) as RequestError;
  expect(error.source).toBe("client");
  expect(error.status).toBeNull();
  expect(error.requestId).toBeTruthy();
  render(
    <Harness error={error} retry={vi.fn().mockResolvedValue(undefined)} />,
  );
  expect(screen.getByRole("alert")).toHaveTextContent("클라이언트 문의 번호");
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(screen.getByLabelText("입력")).toHaveValue("작성 중");
});
it("헤더와 본문 ID가 다르면 서버 ID로 신뢰하지 않는다", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: [],
          meta: { requestId: "different" },
        }),
        { headers: { "X-Request-Id": "header" } },
      ),
    ),
  );
  await expect(request("/api/menus")).rejects.toMatchObject({
    source: "client",
  });
});
it("성공 data는 배열을 다시 감싸지 않고 반환하며 절대 API 주소는 거부한다", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: [],
          meta: { requestId: "same" },
        }),
        { headers: { "X-Request-Id": "same" } },
      ),
    ),
  );
  await expect(request("/api/menus")).resolves.toEqual([]);
  await expect(request("https://example.com/api/menus")).rejects.toThrow(
    "상대 경로",
  );
});
