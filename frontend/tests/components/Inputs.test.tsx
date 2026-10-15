import { useState } from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { AmountInput, parseAmount } from "../../src/components/AmountInput";
import { TextInput } from "../../src/components/TextInput";
import { FormField } from "../../src/components/FormField";
import { FormLayout } from "../../src/components/FormLayout";
import { Button } from "../../src/components/Button";

it("금액 붙여넣기 정규화와 범위 경계를 지킨다", () => {
  const accepted = vi.fn();
  render(<AmountInput aria-label="금액" value={null} onChange={accepted} />);
  fireEvent.change(screen.getByLabelText("금액"), {
    target: { value: "1,500,000원" },
  });
  expect(accepted).toHaveBeenLastCalledWith(1500000);
  expect(screen.getByLabelText("금액")).toHaveValue("1,500,000");
  expect(parseAmount("0")).toBe(0);
  expect(parseAmount("999999999999")).toBe(999999999999);
  for (const raw of ["-1", "1.2", "1000000000000", "abc"])
    expect(parseAmount(raw)).toBeUndefined();
});
it("invalid raw를 보존하고 blur 시 오류를 표시하며 승인 콜백을 부르지 않는다", () => {
  const change = vi.fn();
  render(<AmountInput aria-label="금액" value={null} onChange={change} />);
  fireEvent.change(screen.getByLabelText("금액"), { target: { value: "-1" } });
  expect(change).not.toHaveBeenCalled();
  expect(screen.getByLabelText("금액")).toHaveValue("-1");
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  fireEvent.blur(screen.getByLabelText("금액"));
  expect(screen.getByRole("alert")).toHaveTextContent("정수 금액");
  expect(screen.getByLabelText("금액")).toHaveAttribute("aria-invalid", "true");
});
it("50자 제한은 51번째 입력을 막고 50/50을 표시한다", async () => {
  const user = userEvent.setup();
  render(<TextInput aria-label="이름" maxLength={50} />);
  await user.type(screen.getByLabelText("이름"), "가".repeat(51));
  expect(screen.getByLabelText("이름")).toHaveValue("가".repeat(50));
  expect(screen.getByText("50/50")).toBeVisible();
});
it("필수 별표와 blur 오류, 저장 시 첫 오류 focus를 제공한다", async () => {
  const save = vi.fn();
  render(
    <FormLayout
      onSubmit={save}
      actions={
        <Button type="submit" variant="primary">
          저장
        </Button>
      }
    >
      <FormField label="이름" htmlFor="name" required>
        <TextInput id="name" required />
      </FormField>
      <FormField label="코드" htmlFor="code" required>
        <TextInput id="code" required />
      </FormField>
    </FormLayout>,
  );
  expect(screen.getAllByText("*")).toHaveLength(2);
  fireEvent.blur(screen.getByLabelText("이름 *"));
  expect(screen.getByRole("alert")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(screen.getByLabelText("이름 *")).toHaveFocus();
  expect(save).not.toHaveBeenCalled();
});
it("금액이 유효하면 form 저장을 전달한다", () => {
  const save = vi.fn((event) => event.preventDefault());
  function Form() {
    const [value, setValue] = useState<number | null>(null);
    return (
      <FormLayout onSubmit={save} actions={<Button type="submit">저장</Button>}>
        <AmountInput
          aria-label="금액"
          value={value}
          onChange={setValue}
          required
        />
      </FormLayout>
    );
  }
  render(<Form />);
  fireEvent.change(screen.getByLabelText("금액"), { target: { value: "0" } });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(save).toHaveBeenCalledTimes(1);
});
