import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { Can } from "../../src/permissions/Can";
import { permissionColumns, can } from "../../src/permissions/permissions";
import { processorName } from "../../src/lib/processorName";
import { Table } from "../../src/components/Table";

it("모든 기능 키를 허용하고 버튼과 sensitive 열을 숨기지 않는다", () => {
  Object.keys(permissionColumns).forEach((action) =>
    expect(can(`UI-27.${action}`)).toBe(true),
  );
  render(
    <>
      <Can perm="UI-27.update">
        <button>수정</button>
      </Can>
      <Table
        columns={[
          {
            key: "amount",
            label: "급여",
            sensitive: true,
            render: (row) => row.amount,
          },
        ]}
        rows={[{ id: 1, amount: 1234 }]}
        rowKey={(row) => row.id}
        total={1}
      />
    </>,
  );
  expect(screen.getByRole("button", { name: "수정" })).toBeVisible();
  expect(screen.getByRole("cell", { name: "1234" })).toBeVisible();
});
it("NULL 처리자만 기본 사용자로 표시하고 다른 이름은 보존한다", () => {
  expect(processorName(null)).toBe("기본 사용자");
  expect(processorName("홍길동")).toBe("홍길동");
  expect(processorName("")).toBe("");
});
