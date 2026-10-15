import { useState } from "react";
import type { MenuNode } from "../../api/types";
import { Button } from "../../components/Button";
import { ArrowUp, ArrowDown, Star } from "lucide-react";
import { useShortcuts } from "./preferences";
import styles from "./Shared.module.css";

export function Favorites({
  menus,
  active,
  onOpen,
}: {
  menus: MenuNode[];
  active: string;
  onOpen: (id: string) => unknown;
}) {
  const { favorites, reorderFavorite } = useShortcuts();
  const [dragged, setDragged] = useState<string>();
  const visible = favorites.flatMap((id) =>
    menus.filter((menu) => menu.screenId === id),
  );
  return (
    <section className={styles.quick} aria-label="즐겨찾기">
      <h2>
        <Star size={14} aria-hidden="true" />
        즐겨찾기
      </h2>
      {!visible.length && <p>제목의 별을 눌러 추가하세요.</p>}
      {visible.map((menu, index) => (
        <div
          key={menu.screenId}
          className={styles.shortcut}
          draggable
          onDragStart={(event) => {
            setDragged(menu.screenId!);
            event.dataTransfer?.setData("text/plain", menu.screenId!);
          }}
          onDragEnd={() => setDragged(undefined)}
          onDragOver={(event) => event.preventDefault()}
          onDrop={(event) => {
            event.preventDefault();
            if (dragged) reorderFavorite(dragged, menu.screenId!);
            setDragged(undefined);
          }}
        >
          <a
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
          <Button
            aria-label={`${menu.name} 위로`}
            disabled={index === 0}
            onClick={() =>
              reorderFavorite(menu.screenId!, visible[index - 1].screenId!)
            }
          >
            <ArrowUp size={16} />
          </Button>
          <Button
            aria-label={`${menu.name} 아래로`}
            disabled={index === visible.length - 1}
            onClick={() =>
              reorderFavorite(menu.screenId!, visible[index + 1].screenId!)
            }
          >
            <ArrowDown size={16} />
          </Button>
        </div>
      ))}
    </section>
  );
}
