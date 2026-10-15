import { Clock3 } from "lucide-react";
import type { MenuNode } from "../../api/types";
import { useShortcuts } from "./preferences";
import styles from "./Shared.module.css";

export function RecentScreens({
  menus,
  active,
  onOpen,
}: {
  menus: MenuNode[];
  active: string;
  onOpen: (id: string) => unknown;
}) {
  const recent = useShortcuts((state) => state.recent);
  const visible = recent.flatMap((id) =>
    menus.filter((menu) => menu.screenId === id),
  );
  return (
    <section className={styles.quick} aria-label="최근 본 화면">
      <h2>
        <Clock3 size={14} aria-hidden="true" />
        최근 본 화면
      </h2>
      {!visible.length && <p>최근 본 화면이 없습니다.</p>}
      {visible.map((menu) => (
        <a
          key={menu.screenId}
          href={`/${menu.screenId}`}
          className={styles.row}
          aria-current={active === menu.screenId ? "page" : undefined}
          onClick={(event) => {
            event.preventDefault();
            onOpen(menu.screenId!);
          }}
        >
          {menu.name}
        </a>
      ))}
    </section>
  );
}
