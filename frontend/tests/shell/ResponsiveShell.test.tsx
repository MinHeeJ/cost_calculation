import { render, screen, fireEvent } from "@testing-library/react";
import { Link, MemoryRouter } from "react-router-dom";
import { expect, it, vi } from "vitest";
import { App } from "../../src/app/App";
import styles from "../../src/shell/Shell.module.css";
import { usePreferences } from "../../src/stores/preferences";

it("모바일 메뉴는 화면 이동 후 닫힌다", () => {
  vi.stubGlobal("innerWidth", 375);
  render(
    <MemoryRouter>
      <App sidebar={<Link to="/UI-03">개인 원가·수익표</Link>} />
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
  expect(screen.getByRole("complementary")).toHaveClass(styles.mobileOpen);
  fireEvent.click(screen.getByRole("link", { name: "개인 원가·수익표" }));
  expect(screen.getByRole("complementary")).not.toHaveClass(styles.mobileOpen);
});

it.each([1280, 1279, 1024, 768, 767, 375, 360])(
  "폭 %i에서 올바른 메뉴 토글 상태를 적용한다",
  (width) => {
    vi.stubGlobal("innerWidth", width);
    usePreferences.setState({ menuCollapsed: false });
    render(
      <MemoryRouter>
        <App sidebar={<nav>탐색</nav>} tabs={<div>화면 탭</div>}>
          <h1>본문</h1>
        </App>
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
    const aside = screen.getByRole("complementary");
    expect(aside).toHaveClass(
      width >= 1280
        ? styles.collapsed
        : width >= 768
          ? styles.expanded
          : styles.mobileOpen,
    );
    expect(screen.getByRole("heading", { name: "본문" })).toBeVisible();
  },
);
