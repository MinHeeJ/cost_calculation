import { render, screen, fireEvent } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { Table } from "../../src/components/Table";
import { Pagination } from "../../src/components/Pagination";
import { SearchBar } from "../../src/components/SearchBar";

it("total은 서버 전체 수를 사용하고 민감 열과 행은 그대로 표시한다", () => {
  render(
    <Table
      columns={[
        { key: "name", label: "이름", render: (row) => row.name },
        {
          key: "cost",
          label: "원가",
          kind: "number",
          sensitive: true,
          render: (row) => row.cost,
        },
      ]}
      rows={[{ id: "record", name: "테스트", cost: 1000 }]}
      rowKey={(row) => row.id}
      total={118}
    />,
  );
  expect(screen.getByText("총 118건")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "원가" })).toBeVisible();
  expect(screen.getByRole("cell", { name: "1000" })).toBeVisible();
});
it("118건 50건씩과 화살표 및 20/50/100 선택을 전달한다", () => {
  const page = vi.fn();
  const size = vi.fn();
  render(
    <Pagination
      total={118}
      page={1}
      size={50}
      onPageChange={page}
      onSizeChange={size}
    />,
  );
  expect(screen.getByText("1-50 / 118건")).toBeVisible();
  expect(screen.getByRole("button", { name: "처음 페이지" })).toBeDisabled();
  expect(screen.getAllByRole("option")).toHaveLength(3);
  fireEvent.click(screen.getByRole("button", { name: "마지막 페이지" }));
  expect(page).toHaveBeenCalledWith(3);
  fireEvent.change(screen.getByLabelText("페이지당 건수"), {
    target: { value: "100" },
  });
  expect(size).toHaveBeenCalledWith(100);
  expect(page).toHaveBeenLastCalledWith(1);
});
it("페이지 번호는 최대 10개이며 마지막 페이지 범위가 전체 수를 넘지 않는다", () => {
  const { rerender } = render(
    <Pagination
      total={3000}
      page={1}
      size={20}
      onPageChange={vi.fn()}
      onSizeChange={vi.fn()}
    />,
  );
  expect(
    screen
      .getAllByRole("button")
      .filter((button) => /^\d+$/.test(button.textContent ?? "")),
  ).toHaveLength(10);
  rerender(
    <Pagination
      total={118}
      page={3}
      size={50}
      onPageChange={vi.fn()}
      onSizeChange={vi.fn()}
    />,
  );
  expect(screen.getByText("101-118 / 118건")).toBeVisible();
  expect(screen.getByRole("button", { name: "다음 페이지" })).toBeDisabled();
});
it("조회는 form/Enter 경로, 초기화는 별도 콜백으로 호출한다", () => {
  const search = vi.fn();
  const reset = vi.fn();
  render(
    <SearchBar onSearch={search} onReset={reset}>
      <input aria-label="검색" defaultValue="초기조건" />
    </SearchBar>,
  );
  fireEvent.submit(screen.getByLabelText("검색").closest("form")!);
  expect(search).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "초기화" }));
  expect(reset).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText("검색")).toHaveValue("초기조건");
});
