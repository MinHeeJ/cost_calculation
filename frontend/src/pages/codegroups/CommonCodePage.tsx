import type { ComponentProps } from "react";
import { Button } from "../../components/Button";
import { TitleBar } from "../shared/TitleBar";
import { CommonCodeScreen } from "./CommonCodeScreen";
import shared from "../shared/Shared.module.css";

/** Existing /UI-27 workspace destination; wiring selects this page without adding a second route or menu. */
export function CommonCodePage({
  name,
  groupName,
  mobile = false,
  onDashboard,
  excelMaps,
  operatingStandards,
}: ComponentProps<typeof CommonCodeScreen> & {
  name: string;
  groupName?: string;
  mobile?: boolean;
  onDashboard: () => unknown;
}) {
  if (mobile)
    return (
      <section className={shared.placeholder} data-testid="common-code-pc-only">
        <h1 className={shared.heading}>PC에서 이용해 주세요</h1>
        <Button onClick={onDashboard}>대시보드로</Button>
      </section>
    );
  return (
    <section data-testid="common-code-page">
      <TitleBar screenId="UI-27" name={name} groupName={groupName} />
      <CommonCodeScreen
        excelMaps={excelMaps}
        operatingStandards={operatingStandards}
      />
    </section>
  );
}
