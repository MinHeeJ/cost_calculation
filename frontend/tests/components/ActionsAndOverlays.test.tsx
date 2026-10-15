import { act, render, screen, fireEvent } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { Button } from "../../src/components/Button";
import { WorkDialog } from "../../src/components/WorkDialog";
import { ConfirmDialog } from "../../src/components/ConfirmDialog";
import { Toast } from "../../src/components/Toast";
import { LockBanner } from "../../src/components/LockBanner";
import { UnderlineTabs } from "../../src/components/UnderlineTabs";
import { Badge, type BadgeTone } from "../../src/components/Badge";
import styles from "../../src/components/Common.module.css";

it("처리 중 스피너와 disabled로 중복 실행을 막는다", async () => {
  let done!: () => void;
  const run = vi.fn(
    () =>
      new Promise<void>((resolve) => {
        done = resolve;
      }),
  );
  render(
    <Button onClick={run} variant="primary">
      저장
    </Button>,
  );
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  fireEvent.click(screen.getByRole("button"));
  expect(run).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button")).toBeDisabled();
  expect(screen.getByLabelText("처리 중")).toBeVisible();
  await act(async () => done());
  expect(screen.getByRole("button")).toBeEnabled();
});
it("작업 창은 외부 클릭과 Escape로 닫지 않으며 명시 취소만 전달한다", () => {
  const close = vi.fn();
  render(
    <WorkDialog open title="작업" onClose={close}>
      <input aria-label="내용" />
    </WorkDialog>,
  );
  fireEvent.click(screen.getByRole("dialog").parentElement!);
  fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
  expect(close).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(close).toHaveBeenCalledTimes(1);
});
it("확인 창 내 위험 버튼과 취소 문구를 전달한다", () => {
  const confirm = vi.fn();
  render(
    <ConfirmDialog
      open
      destructive
      title="삭제 확인"
      confirmLabel="삭제"
      cancelLabel="계속 작성"
      onCancel={vi.fn()}
      onConfirm={confirm}
    >
      지우시겠습니까?
    </ConfirmDialog>,
  );
  expect(screen.getByRole("button", { name: "삭제" })).toHaveClass(
    styles.danger,
  );
  expect(screen.getByRole("button", { name: "계속 작성" })).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "삭제" }));
  expect(confirm).toHaveBeenCalledTimes(1);
});
it("성공 토스트는 3초 후 종료하고 오류 alert를 만들지 않는다", () => {
  vi.useFakeTimers();
  const close = vi.fn();
  render(<Toast message="완료" onClose={close} />);
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  act(() => vi.advanceTimersByTime(2999));
  expect(close).not.toHaveBeenCalled();
  act(() => vi.advanceTimersByTime(1));
  expect(close).toHaveBeenCalledTimes(1);
});
it("잠금 띠와 다섯 상태 팔레트를 연결한다", () => {
  render(
    <>
      <LockBanner month="2026-09" />
      {(["success", "warning", "danger", "info", "locked"] as BadgeTone[]).map(
        (tone) => (
          <Badge key={tone} tone={tone}>
            {tone}
          </Badge>
        ),
      )}
    </>,
  );
  expect(screen.getByRole("status")).toHaveClass(styles.lockBanner);
  expect(screen.getByRole("status")).toHaveTextContent(
    "2026-09는 마감되었습니다.",
  );
  expect(screen.getByText("danger")).toHaveClass(styles.dangerBadge);
});
it("밑줄 탭은 현재 상태와 선택을 전달한다", () => {
  const change = vi.fn();
  render(
    <UnderlineTabs
      tabs={[
        { id: "a", label: "하나" },
        { id: "b", label: "둘" },
      ]}
      value="a"
      onChange={change}
    />,
  );
  expect(screen.getByRole("tab", { name: "하나" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  fireEvent.click(screen.getByRole("tab", { name: "둘" }));
  expect(change).toHaveBeenCalledWith("b");
});
