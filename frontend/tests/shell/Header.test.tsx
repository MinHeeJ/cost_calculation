import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { expect, it, vi } from "vitest";
import { Header } from "../../src/shell/Header";

it("팝오버는 Escape와 외부 클릭으로 닫히고 확장 상태를 알린다", () => {
  render(
    <MemoryRouter>
      <Header onToggleMenu={() => undefined} />
    </MemoryRouter>,
  );
  const trigger = screen.getByRole("button", { name: "알림" });
  fireEvent.click(trigger);
  expect(trigger).toHaveAttribute("aria-expanded", "true");
  fireEvent.keyDown(document, { key: "Escape" });
  expect(screen.queryByText("새 알림이 없습니다")).not.toBeInTheDocument();
  fireEvent.click(trigger);
  fireEvent.pointerDown(document.body);
  expect(screen.queryByText("새 알림이 없습니다")).not.toBeInTheDocument();
});

function Location() {
  return <output>{useLocation().pathname}</output>;
}
it("로고는 대시보드로 이동하고 알림과 기본 사용자 건수 설정을 제공한다", () => {
  const toggle = vi.fn();
  render(
    <MemoryRouter initialEntries={["/UI-03"]}>
      <Header onToggleMenu={toggle} />
      <Location />
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByRole("link", { name: "프로젝트·원가 관리" }));
  expect(screen.getByRole("status")).toHaveTextContent("/UI-16");
  fireEvent.click(screen.getByRole("button", { name: "알림" }));
  expect(screen.getByText("새 알림이 없습니다")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "기본 사용자 설정" }));
  fireEvent.change(screen.getByLabelText("목록 기본 건수"), {
    target: { value: "50" },
  });
  expect(JSON.parse(localStorage.getItem("si.pageSize")!)).toBe(50);
  fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
  expect(toggle).toHaveBeenCalledTimes(1);
});
