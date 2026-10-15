import { X } from "lucide-react";
import type { MenuNode } from "../../api/types";
import { Button } from "../../components/Button";
import { useNavigation } from "./navigation";
import styles from "./Shared.module.css";

export function ScreenTabs({
  menus,
  onOpen,
  onClose,
}: {
  menus: MenuNode[];
  onOpen: (id: string) => unknown;
  onClose: (id: string) => unknown;
}) {
  const { tabs, active, dirty, busy } = useNavigation();
  return (
    <div role="tablist" aria-label="열린 화면" className={styles.tabs}>
      {tabs.map((id) => {
        const name = menus.find((menu) => menu.screenId === id)?.name ?? id;
        return (
          <div
            key={id}
            role="tab"
            aria-selected={active === id}
            className={styles.tab}
          >
            <a
              href={`/${id}`}
              aria-current={active === id ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();
                if (!busy) onOpen(id);
              }}
            >
              {dirty[id] ? "● " : ""}
              {name}
            </a>
            {id !== "UI-16" && (
              <Button
                aria-label={`${name} 탭 닫기`}
                disabled={busy}
                onClick={() => onClose(id)}
              >
                <X size={16} />
              </Button>
            )}
          </div>
        );
      })}
    </div>
  );
}
