import type { ApiError, ApiResponse } from "./types";

export class RequestError extends Error {
  constructor(
    message: string,
    public readonly requestId: string,
    public readonly source: "server" | "client",
    public readonly status: number | null,
    public readonly field: string | null = null,
    public readonly code: string | null = null,
    public readonly details: unknown = null,
  ) {
    super(message);
  }
}

/** A client ID tracks failures without responses; it is never presented as a server ID. */
export async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  if (!path.startsWith("/api/") || path.startsWith("//")) {
    throw new Error("API 경로는 /api/ 상대 경로여야 합니다.");
  }
  const trace = crypto.randomUUID();
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        ...Object.fromEntries(new Headers(init.headers)),
        "X-Request-Id": trace,
      },
    });
  } catch {
    throw new RequestError(
      "연결이 끊겼습니다. 다시 시도해 주세요.",
      trace,
      "client",
      null,
    );
  }
  const id = response.headers.get("X-Request-Id");
  if (response.status === 204) {
    if (!id?.trim()) {
      throw new RequestError(
        "응답 문의 번호가 없습니다.",
        trace,
        "client",
        response.status,
      );
    }
    return undefined as T;
  }
  let body: ApiResponse<T> | ApiError;
  try {
    body = await response.json();
  } catch {
    throw new RequestError(
      "응답을 확인할 수 없습니다.",
      trace,
      "client",
      response.status,
    );
  }
  if (
    !id ||
    id !== body.meta?.requestId ||
    (!body.success && id !== body.requestId)
  ) {
    throw new RequestError(
      "응답 문의 번호가 일치하지 않습니다.",
      trace,
      "client",
      response.status,
    );
  }
  if (!response.ok || !body.success) {
    const error = body as ApiError;
    const message =
      response.status >= 500
        ? "처리 중 오류가 발생했습니다. 다시 시도해 주세요."
        : error.message;
    throw new RequestError(
      message,
      id,
      "server",
      response.status,
      error.field ?? null,
      error.code ?? null,
      body,
    );
  }
  return body.data;
}

export const api = { get: <T>(path: string) => request<T>(path) };
