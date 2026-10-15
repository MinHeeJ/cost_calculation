import { PanelsTopLeft, Monitor } from "lucide-react";
import { Button } from "../../components/Button";
import type { ScreenDefinition } from "./registry";
import { TitleBar } from "./TitleBar";
import { PeriodBar } from "./PeriodBar";
import styles from "./Shared.module.css";

/** Every canonical route shares this placeholder; mobile aliases are not separate routes. */
export function PlaceholderScreen({
  screen,
  name,
  groupName,
  mobile = false,
  onDashboard,
}: {
  screen: ScreenDefinition;
  name: string;
  groupName?: string;
  mobile?: boolean;
  onDashboard: () => unknown;
}) {
  if (mobile && !screen.mobile)
    return (
      <section className={styles.placeholder}>
        <Monitor size={32} aria-hidden="true" />
        <h1 className={styles.heading}>PC에서 이용해 주세요</h1>
        <Button onClick={onDashboard}>대시보드로</Button>
      </section>
    );
  return (
    <section>
      <TitleBar screenId={screen.screenId} name={name} groupName={groupName} />
      <div className={styles.page}>
        <PeriodBar screen={screen} />
        <div className={styles.placeholder}>
          <PanelsTopLeft size={32} strokeWidth={1.5} aria-hidden="true" />
          <p>이 화면은 다음 단계에서 만듭니다.</p>
        </div>
      </div>
    </section>
  );
}
