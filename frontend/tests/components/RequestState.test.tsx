import { render, screen, fireEvent } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { RequestState } from "../../src/components/RequestState";
import { RequestError } from "../../src/api/client";

it("불러오기, 빈 이유/행동, 실패 재시도, 성공을 구별한다", () => {
  const action = vi.fn();
  const retry = vi.fn();
  const { rerender } = render(<RequestState loading onRetry={retry} />);
  expect(screen.getByRole("status", { name: "불러오는 중" })).toBeVisible();
  rerender(
    <RequestState
      empty
      emptyReason="검색 조건에 해당하는 항목이 없습니다."
      emptyAction={{ label: "조건 초기화", onClick: action }}
      onRetry={retry}
    />,
  );
  expect(
    screen.getByText("검색 조건에 해당하는 항목이 없습니다."),
  ).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "조건 초기화" }));
  expect(action).toHaveBeenCalledTimes(1);
  rerender(
    <RequestState
      error={new RequestError("오류", "server", "server", 500)}
      onRetry={retry}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(retry).toHaveBeenCalledTimes(1);
  rerender(
    <RequestState onRetry={retry}>
      <p>결과</p>
    </RequestState>,
  );
  expect(screen.getByText("결과")).toBeVisible();
});
